# Synthetic v1 retrieval baseline results

Status: **verified experiment; Milestone 3R remains under owner review.** The raw redacted reports are intentionally git-ignored in `evals/reports/`.

## Fixed experiment

- Six deliberately synthetic cloud text notes; five development and three held-out questions.
- The question sets include answerable, unanswerable, conflicting, and instruction-in-content cases.
- Retrieval limit: three deduplicated source-note IDs.
- Keyword: Postgres full-text search. Vector: `text-embedding-3-small` plus pgvector cosine similarity.
- Answers: `gpt-5-mini`, identical grounded prompt and strict structured output for both modes.
- Token chunking: `token-500-overlap-75-v1`. Every fixture note is genuinely short and therefore remains one chunk.

## Measured results

| Mode | Split | Mean Recall@3 | Mean MRR | Citation IDs valid |
| --- | --- | ---: | ---: | --- |
| Full-text | Development | 0.70 | 0.60 | All reported IDs |
| Full-text | Held-out | 0.67 | 0.33 | All reported IDs |
| Vector | Development | 1.00 | 0.80 | All reported IDs |
| Vector | Held-out | 1.00 | 0.67 | All reported IDs |

The vector comparison retrieved the expected source note(s) for this tiny set, including paraphrased garden and cooking questions that full-text missed. This is an experiment result, **not** proof of general retrieval quality.

## Important failures and limits

- Full-text retrieved only one of the two expected conflict notes, so it could not report the conflict.
- Vector retrieved both conflict notes but the answer path abstained because a generated response could not be validated. This is correctly recorded as a failed conflict response, not silently converted into an answer.
- Unanswerable questions returned an explicit abstention; all reported citations resolved to retrieved source-note IDs.
- Answer support and completeness still require human review; the automated checks establish source-ID validity, not factual quality.
- Provider calls used only synthetic material. The local ledger reconciled $0.013036 actual usage over 61 requests; $0.013769 remains conservatively reserved because uncertain outcomes are not refunded automatically. Both are below the configured $1 limit.

## Conflict-handling refinement (verified after the baseline run)

The initial answer cap of 350 output tokens could truncate strict JSON before it completed. The cap is now a bounded 800 tokens. For the narrow baseline case, code detects two distinct ISO dates in overlapping evidence, instructs the model to set `conflict=true` and cite both source IDs, and validates that contract. A malformed or incomplete response falls back to a deterministic conflict summary citing both sources instead of abstaining. A direct synthetic model check passed with `conflict=true`, `insufficient_evidence=false`, and both required citations. The complete four-report baseline has not been rerun after this refinement.

## Decision

Retain this baseline and its failure cases. Do not add Android chat, automatic sync, hybrid/reranking, or agentic routing from these numbers alone. The immediate proposed follow-up is a bounded LangGraph wrapper around the same retrieve -> answer/abstain pipeline, with privacy-aware tracing and comparison against this fixed baseline.
