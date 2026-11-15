"""Small, transparent conflict detection for the 3R synthetic baseline."""

import re
from dataclasses import dataclass
from uuid import UUID

from .models import EvidenceChunk


DATE_PATTERN = re.compile(r"\b\d{4}-\d{2}-\d{2}\b")
WORD_PATTERN = re.compile(r"[a-z]{4,}")
STOP_WORDS = {"this", "that", "with", "from", "into", "because", "note", "source"}


@dataclass(frozen=True)
class DateConflict:
    source_dates: tuple[tuple[UUID, str], ...]

    @property
    def source_ids(self) -> set[UUID]:
        return {source_id for source_id, _ in self.source_dates}

    def deterministic_answer(self) -> str:
        statements = "; ".join(f"{source_id} says {date}" for source_id, date in self.source_dates)
        return f"The retrieved evidence conflicts: {statements}."


def _meaningful_words(content: str) -> set[str]:
    return {word for word in WORD_PATTERN.findall(content.lower()) if word not in STOP_WORDS}


def detect_date_conflict(evidence: list[EvidenceChunk]) -> DateConflict | None:
    """Find distinct ISO dates in semantically overlapping evidence chunks.

    This deliberately narrow rule avoids claiming general fact-conflict
    detection. It is enough to guard the baseline's planned date-conflict case.
    """
    dated = [
        (chunk, date)
        for chunk in evidence
        for date in sorted(set(DATE_PATTERN.findall(chunk.content)))
    ]
    for index, (left, left_date) in enumerate(dated):
        for right, right_date in dated[index + 1 :]:
            if left_date == right_date or left.client_note_id == right.client_note_id:
                continue
            if len(_meaningful_words(left.content) & _meaningful_words(right.content)) >= 2:
                return DateConflict(
                    tuple(sorted(((left.client_note_id, left_date), (right.client_note_id, right_date))))
                )
    return None
