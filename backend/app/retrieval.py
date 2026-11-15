"""Deterministic retrieval preparation for the deliberately synthetic 3R corpus."""

from dataclasses import dataclass
from hashlib import sha256
from uuid import UUID

import tiktoken


CHUNKING_VERSION = "token-500-overlap-75-v1"
CHUNK_TOKENS = 500
OVERLAP_TOKENS = 75


@dataclass(frozen=True)
class TextChunk:
    client_note_id: UUID
    chunk_index: int
    start_offset: int
    end_offset: int
    content: str
    source_content_sha256: str


def content_digest(content: str) -> str:
    return sha256(content.encode("utf-8")).hexdigest()


def _encoding() -> tiktoken.Encoding:
    # GPT-5 uses the o200k tokenizer. The named lookup keeps this explicit and
    # the fallback keeps chunking reproducible if a later SDK lacks the alias.
    try:
        return tiktoken.encoding_for_model("gpt-5-mini")
    except KeyError:
        return tiktoken.get_encoding("o200k_base")


def token_count(content: str) -> int:
    return len(_encoding().encode(content))


def chunk_note(client_note_id: UUID, content: str) -> list[TextChunk]:
    """Chunk by tokens while retaining exact Python-string source offsets.

    Notes at or below the chunk limit remain one intact evidence item. Longer
    notes overlap only at token boundaries. `decode_with_offsets` provides the
    character start position for every decoded token, avoiding a lossy search
    for repeated text.
    """
    encoding = _encoding()
    tokens = encoding.encode(content)
    if not tokens:
        return []
    _, offsets = encoding.decode_with_offsets(tokens)
    chunks: list[TextChunk] = []
    start_token = 0
    chunk_index = 0
    digest = content_digest(content)
    while start_token < len(tokens):
        end_token = min(start_token + CHUNK_TOKENS, len(tokens))
        start_offset = offsets[start_token]
        end_offset = offsets[end_token] if end_token < len(tokens) else len(content)
        chunks.append(
            TextChunk(
                client_note_id=client_note_id,
                chunk_index=chunk_index,
                start_offset=start_offset,
                end_offset=end_offset,
                content=content[start_offset:end_offset],
                source_content_sha256=digest,
            )
        )
        if end_token == len(tokens):
            break
        start_token = end_token - OVERLAP_TOKENS
        chunk_index += 1
    return chunks
