"""Bounded OpenAI calls for the synthetic Milestone 3R experiment only."""

from dataclasses import dataclass
from math import ceil

from openai import AsyncOpenAI

from .rag_budget import ExperimentBudgetLedger


EMBEDDING_MODEL = "text-embedding-3-small"
ANSWER_MODEL = "gpt-5-mini"
EMBEDDING_INPUT_USD_PER_MILLION = 0.02
ANSWER_INPUT_USD_PER_MILLION = 0.25
ANSWER_OUTPUT_USD_PER_MILLION = 2.00
# GPT-5 mini may consume part of this allowance before emitting strict JSON.
# 800 remains bounded while avoiding truncation of a small grounded response.
MAX_ANSWER_OUTPUT_TOKENS = 800

ANSWER_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "answer": {"type": "string"},
        "citation_ids": {"type": "array", "items": {"type": "string"}},
        "insufficient_evidence": {"type": "boolean"},
        "conflict": {"type": "boolean"},
    },
    "required": ["answer", "citation_ids", "insufficient_evidence", "conflict"],
}


@dataclass(frozen=True)
class AnswerCallResult:
    text: str
    input_tokens: int
    output_tokens: int


class ProviderRequestError(Exception):
    """Safe provider failure category; deliberately carries no request data."""


class OpenAiGateway:
    """Provider wrapper with one attempt per operation and a cost reservation."""

    def __init__(self, api_key: str, ledger: ExperimentBudgetLedger) -> None:
        self._client = AsyncOpenAI(api_key=api_key, max_retries=0, timeout=30.0)
        self._ledger = ledger

    @staticmethod
    def _conservative_input_tokens(local_estimate: int) -> int:
        """Reserve for provider tokenizer/version differences before a call.

        Chunking remains GPT-5-token based, but the embedding service may count
        text differently. Doubling a local estimate plus a small floor can only
        reject sooner; it cannot relax the configured $1 ceiling.
        """
        return max(128, ceil(local_estimate * 2))

    async def embed(self, texts: list[str], input_tokens: int) -> list[list[float]]:
        worst_case = self._conservative_input_tokens(input_tokens) * EMBEDDING_INPUT_USD_PER_MILLION / 1_000_000
        reservation = self._ledger.reserve(worst_case)
        try:
            response = await self._client.embeddings.create(model=EMBEDDING_MODEL, input=texts)
        except Exception as error:
            raise ProviderRequestError(type(error).__name__) from error
        actual_tokens = response.usage.prompt_tokens
        self._ledger.reconcile(
            reservation,
            actual_tokens * EMBEDDING_INPUT_USD_PER_MILLION / 1_000_000,
        )
        return [item.embedding for item in response.data]

    async def answer(self, prompt: str, input_tokens: int) -> AnswerCallResult:
        worst_case = (
            self._conservative_input_tokens(input_tokens) * ANSWER_INPUT_USD_PER_MILLION / 1_000_000
            + MAX_ANSWER_OUTPUT_TOKENS * ANSWER_OUTPUT_USD_PER_MILLION / 1_000_000
        )
        reservation = self._ledger.reserve(worst_case)
        try:
            response = await self._client.responses.create(
                model=ANSWER_MODEL,
                input=prompt,
                max_output_tokens=MAX_ANSWER_OUTPUT_TOKENS,
                store=False,
                text={
                    "format": {
                        "type": "json_schema",
                        "name": "grounded_answer",
                        "strict": True,
                        "schema": ANSWER_SCHEMA,
                    },
                    "verbosity": "low",
                },
            )
        except Exception as error:
            raise ProviderRequestError(type(error).__name__) from error
        usage = response.usage
        input_used = usage.input_tokens if usage else input_tokens
        output_used = usage.output_tokens if usage else MAX_ANSWER_OUTPUT_TOKENS
        self._ledger.reconcile(
            reservation,
            input_used * ANSWER_INPUT_USD_PER_MILLION / 1_000_000
            + output_used * ANSWER_OUTPUT_USD_PER_MILLION / 1_000_000,
        )
        return AnswerCallResult(
            text=response.output_text,
            input_tokens=input_used,
            output_tokens=output_used,
        )
