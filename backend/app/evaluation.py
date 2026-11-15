"""Deterministic metrics and validation for the versioned 3R dataset."""

from dataclasses import dataclass
from uuid import UUID


@dataclass(frozen=True)
class RetrievalMetrics:
    note_recall_at_3: float
    reciprocal_rank: float


def deduplicated_note_ids(note_ids: list[UUID]) -> list[UUID]:
    return list(dict.fromkeys(note_ids))


def retrieval_metrics(expected: set[UUID], returned: list[UUID], limit: int = 3) -> RetrievalMetrics:
    ranked = deduplicated_note_ids(returned)[:limit]
    recall = len(expected.intersection(ranked)) / len(expected) if expected else 1.0
    first_rank = next((index + 1 for index, note_id in enumerate(ranked) if note_id in expected), None)
    return RetrievalMetrics(recall, 0.0 if first_rank is None else 1.0 / first_rank)


def citations_are_valid(citation_ids: list[UUID], retrieved_note_ids: list[UUID]) -> bool:
    """Every cited original must be present in retrieved evidence, exactly once."""
    return len(citation_ids) == len(set(citation_ids)) and set(citation_ids).issubset(set(retrieved_note_ids))
