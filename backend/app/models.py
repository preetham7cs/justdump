from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class TextNoteWrite(BaseModel):
    """An immutable local note sent explicitly by the signed-in owner."""

    model_config = ConfigDict(extra="forbid")

    client_note_id: UUID
    content: str = Field(min_length=1, max_length=100_000)
    created_at: datetime
    updated_at: datetime

    @field_validator("content")
    @classmethod
    def content_must_not_be_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("content must contain non-whitespace text")
        return value

    @field_validator("created_at", "updated_at")
    @classmethod
    def timestamp_must_include_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("timestamps must include a timezone")
        return value


class CloudTextNote(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    client_note_id: UUID
    content: str
    created_at: datetime
    updated_at: datetime
    cloud_created_at: datetime


class OwnerIdentity(BaseModel):
    owner_id: UUID


class RetrievalIndexRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    client_note_ids: list[UUID] = Field(min_length=1, max_length=20)
    mode: Literal["fts", "vector"]


class RetrievalIndexResult(BaseModel):
    mode: Literal["fts", "vector"]
    indexed_notes: int
    indexed_chunks: int
    embedded_chunks: int
    skipped_unchanged_notes: int


class EvidenceChunk(BaseModel):
    client_note_id: UUID
    chunk_index: int
    start_offset: int
    end_offset: int
    content: str
    score: float


class RetrievalQuery(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question: str = Field(min_length=1, max_length=2_000)
    mode: Literal["fts", "vector"]
    limit: int = Field(default=3, ge=1, le=20)


class RetrievalResult(BaseModel):
    evidence: list[EvidenceChunk]


class AnswerResponse(BaseModel):
    answer: str
    citation_ids: list[UUID]
    insufficient_evidence: bool
    conflict: bool
