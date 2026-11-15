import pytest

from app.rag_budget import BudgetExceeded, ExperimentBudgetLedger
from app.openai_gateway import MAX_ANSWER_OUTPUT_TOKENS, OpenAiGateway


def test_ledger_reserves_before_spending_and_reconciles(tmp_path) -> None:
    ledger = ExperimentBudgetLedger(tmp_path / "ledger.sqlite3", limit_usd=1.0)
    first = ledger.reserve(0.70)
    assert ledger.remaining_usd() == pytest.approx(0.30)
    ledger.reconcile(first, 0.20)
    assert ledger.remaining_usd() == pytest.approx(0.80)


def test_ledger_keeps_unknown_failed_call_reserved(tmp_path) -> None:
    ledger = ExperimentBudgetLedger(tmp_path / "ledger.sqlite3", limit_usd=1.0)
    ledger.reserve(0.80)
    with pytest.raises(BudgetExceeded):
        ledger.reserve(0.21)


def test_provider_reservation_token_margin_is_conservative() -> None:
    assert OpenAiGateway._conservative_input_tokens(1) == 128
    assert OpenAiGateway._conservative_input_tokens(100) == 200
    assert MAX_ANSWER_OUTPUT_TOKENS == 800
