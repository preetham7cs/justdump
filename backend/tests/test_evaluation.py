from uuid import UUID

from app.evaluation import citations_are_valid, retrieval_metrics


A = UUID("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
B = UUID("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
C = UUID("cccccccc-cccc-4ccc-8ccc-cccccccccccc")


def test_note_metrics_deduplicate_chunks_from_one_note() -> None:
    metrics = retrieval_metrics({A, B}, [A, A, B])
    assert metrics.note_recall_at_3 == 1.0
    assert metrics.reciprocal_rank == 1.0


def test_citations_must_point_to_returned_originals_once() -> None:
    assert citations_are_valid([A, B], [A, B, B])
    assert not citations_are_valid([A, A], [A])
    assert not citations_are_valid([C], [A, B])
