from uuid import UUID

from app.conflicts import detect_date_conflict
from app.main import conflict_fallback
from app.models import EvidenceChunk


FIRST = UUID("10000000-0000-4000-8000-000000000001")
SECOND = UUID("10000000-0000-4000-8000-000000000004")


def evidence(note_id: UUID, content: str) -> EvidenceChunk:
    return EvidenceChunk(
        client_note_id=note_id, chunk_index=0, start_offset=0,
        end_offset=len(content), content=content, score=1.0,
    )


def test_detects_conflicting_dates_for_shared_subject() -> None:
    found = detect_date_conflict([
        evidence(FIRST, "The Aurora pilot begins on 2026-10-03."),
        evidence(SECOND, "The Aurora pilot start is delayed to 2026-10-10."),
    ])

    assert found is not None
    assert found.source_ids == {FIRST, SECOND}


def test_conflict_fallback_cites_every_conflicting_source() -> None:
    found = detect_date_conflict([
        evidence(FIRST, "The Aurora pilot begins on 2026-10-03."),
        evidence(SECOND, "The Aurora pilot start is delayed to 2026-10-10."),
    ])
    assert found is not None

    answer = conflict_fallback(found)

    assert answer.conflict is True
    assert answer.insufficient_evidence is False
    assert set(answer.citation_ids) == {FIRST, SECOND}


def test_does_not_treat_unrelated_dates_as_a_conflict() -> None:
    found = detect_date_conflict([
        evidence(FIRST, "The Aurora pilot begins on 2026-10-03."),
        evidence(SECOND, "The train leaves York on 2026-10-10."),
    ])

    assert found is None
