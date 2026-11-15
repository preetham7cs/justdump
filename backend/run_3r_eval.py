"""Run the approved synthetic-only 3R experiment against a local FastAPI API.

The script prompts for the existing Supabase owner password rather than storing
it. It writes a redacted report (IDs and metrics only), never questions, note
text, answers, access tokens, or API keys.
"""

import argparse
import getpass
import json
from datetime import UTC, datetime
from pathlib import Path
from uuid import UUID

import httpx

from app.config import Settings
from app.evaluation import citations_are_valid, retrieval_metrics


ROOT = Path(__file__).parents[1]
DATASET = ROOT / "evals" / "synthetic" / "v1" / "corpus.json"
REPORT_DIR = ROOT / "evals" / "reports"


def authenticate(settings: Settings, email: str, password: str) -> str:
    response = httpx.post(
        f"{settings.auth_issuer}/token?grant_type=password",
        headers={"apikey": settings.justdump_supabase_publishable_key},
        json={"email": email, "password": password}, timeout=20.0,
    )
    response.raise_for_status()
    return response.json()["access_token"]


def request(client: httpx.Client, method: str, path: str, token: str, body: dict) -> dict:
    response = client.request(method, path, headers={"Authorization": f"Bearer {token}"}, json=body)
    if response.is_error:
        # The server's detail is intentionally designed to remain non-sensitive.
        try:
            detail = response.json().get("detail", "request failed")
        except json.JSONDecodeError:
            detail = "non-JSON error response"
        raise RuntimeError(f"JustDump {response.status_code}: {detail}")
    return response.json()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default="http://127.0.0.1:8000")
    parser.add_argument("--mode", choices=["fts", "vector"], required=True)
    parser.add_argument("--split", choices=["development", "held_out"], default="development")
    parser.add_argument("--email", required=True)
    args = parser.parse_args()

    settings = Settings()
    if settings.openai_api_key is None:
        raise SystemExit("OPENAI_API_KEY is missing from ignored backend/.env.")
    password = getpass.getpass("Supabase owner password (not saved): ")
    token = authenticate(settings, args.email, password)
    dataset = json.loads(DATASET.read_text(encoding="utf-8"))
    note_ids = [note["id"] for note in dataset["notes"]]
    timestamp = datetime.now(UTC).isoformat()
    with httpx.Client(base_url=args.api, timeout=60.0) as client:
        for note in dataset["notes"]:
            request(client, "POST", "/v1/text-notes", token, {
                "client_note_id": note["id"], "content": note["content"],
                "created_at": timestamp, "updated_at": timestamp,
            })
        index = request(client, "POST", "/v1/retrieval/index", token, {
            "client_note_ids": note_ids, "mode": args.mode,
        })
        questions = dataset[f"{args.split}_questions"]
        rows = []
        for item in questions:
            search = request(client, "POST", "/v1/retrieval/search", token, {
                "question": item["question"], "mode": args.mode, "limit": 3,
            })
            evidence_ids = [UUID(chunk["client_note_id"]) for chunk in search["evidence"]]
            expected = {UUID(note_id) for note_id in item["expected_note_ids"]}
            metrics = retrieval_metrics(expected, evidence_ids)
            answer = request(client, "POST", "/v1/questions/answer", token, {
                "question": item["question"], "mode": args.mode, "limit": 3,
            })
            citation_ids = [UUID(note_id) for note_id in answer["citation_ids"]]
            rows.append({
                "question_id": item["id"], "note_recall_at_3": metrics.note_recall_at_3,
                "reciprocal_rank": metrics.reciprocal_rank,
                "retrieved_note_ids": [str(note_id) for note_id in dict.fromkeys(evidence_ids)],
                "citation_ids": [str(note_id) for note_id in citation_ids],
                "citation_ids_valid": citations_are_valid(citation_ids, evidence_ids),
                "insufficient_evidence": answer["insufficient_evidence"], "conflict": answer["conflict"],
                "abstention_matches_expectation": answer["insufficient_evidence"] == (not item["answerable"]),
                "conflict_matches_expectation": answer["conflict"] == item.get("conflict", False),
                "requires_human_review": True,
            })
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "dataset_version": dataset["dataset_version"], "mode": args.mode, "split": args.split,
        "index": index, "results": rows,
        "mean_note_recall_at_3": sum(row["note_recall_at_3"] for row in rows) / len(rows),
        "mean_mrr": sum(row["reciprocal_rank"] for row in rows) / len(rows),
        "limitation": "Tiny synthetic results are an experiment, not evidence of general quality.",
    }
    output = REPORT_DIR / f"{dataset['dataset_version']}-{args.mode}-{args.split}.json"
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote redacted report: {output.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
