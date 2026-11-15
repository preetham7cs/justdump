from uuid import UUID

from app.retrieval import CHUNK_TOKENS, OVERLAP_TOKENS, chunk_note, content_digest, token_count


NOTE_ID = UUID("33333333-3333-4333-8333-333333333333")


def test_short_note_remains_intact_with_source_offsets() -> None:
    content = "Keep this genuinely short note together."
    chunks = chunk_note(NOTE_ID, content)

    assert len(chunks) == 1
    assert chunks[0].content == content
    assert (chunks[0].start_offset, chunks[0].end_offset) == (0, len(content))
    assert chunks[0].source_content_sha256 == content_digest(content)


def test_long_note_uses_token_overlap_and_exact_slices() -> None:
    content = " ".join(f"token-{number}" for number in range(900))
    chunks = chunk_note(NOTE_ID, content)

    assert len(chunks) > 1
    assert all(content[chunk.start_offset:chunk.end_offset] == chunk.content for chunk in chunks)
    assert all(token_count(chunk.content) <= CHUNK_TOKENS for chunk in chunks)
    assert chunks[1].start_offset < chunks[0].end_offset
    assert OVERLAP_TOKENS > 0
