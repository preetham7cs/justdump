# JustDump backend — Milestone 3A

This FastAPI service supports one authenticated owner's explicit text-note upload and cloud read-back. Milestone 3R adds an unverified, synthetic-only retrieval experiment; it is not an Android question feature, automatic sync, or backup service.

## Local setup

1. Copy `.env.example` to `.env` and fill the values described in [`../docs/local-configuration.md`](../docs/local-configuration.md). The actual file is ignored by Git. `OPENAI_API_KEY` is required only to run the approved synthetic 3R experiment.
2. Create and activate a virtual environment, then install the development dependencies:

   ```powershell
   python -m venv .venv
   .\.venv\Scripts\python.exe -m pip install -e ".[dev]"
   ```

3. In Supabase Dashboard, open **SQL Editor** and run the migrations in numeric order: [`migrations/0001_owner_text_notes.sql`](migrations/0001_owner_text_notes.sql), [`migrations/0002_qualify_pgcrypto_digest.sql`](migrations/0002_qualify_pgcrypto_digest.sql), then [`migrations/0003_retrieval_baseline.sql`](migrations/0003_retrieval_baseline.sql). Migration 0003 creates owner-RLS-protected chunks and embeddings for the explicit synthetic experiment. Keep the SQL result screen private; it may contain account-related metadata.
4. Run the local checks:

   ```powershell
   .\.venv\Scripts\python.exe -m pytest -p no:cacheprovider
   .\.venv\Scripts\python.exe -m uvicorn --factory app.main:create_app --reload
   ```

The backend uses the caller's bearer token and Supabase publishable key for every PostgREST request. The migration enables RLS, and the RPC is `security invoker`, so it does not bypass RLS. No database password or service-role key is used.

This API is verified only as a local laptop process. The debug Android app needs that process running, plus the temporary ADB reverse route, for cloud actions; it does not need it for device-local capture and reading. It is not deployed.

## API contract

- `GET /healthz` — no credentials; reports only process health.
- `GET /v1/me` — bearer token required; returns the authorised owner UUID.
- `POST /v1/text-notes` — bearer token required; uploads one immutable local note by its existing `client_note_id`.
- `GET /v1/text-notes/{client_note_id}` — bearer token required; returns the owner's cloud record for read-back.

Posting the same local UUID and same content returns the existing cloud row. Posting the same UUID with different content returns a conflict. This makes a retry safe without treating two intentionally identical notes as one note.

## Milestone 3R synthetic runner

`run_3r_eval.py` is intentionally command-line only. It prompts for the existing owner password (it is neither logged nor saved), obtains a caller JWT, seeds the tracked synthetic corpus through the normal authenticated API, and indexes exactly those fixture IDs. It never selects or indexes the existing personal vault.

With FastAPI running locally, run one comparison at a time:

```powershell
.\.venv\Scripts\python.exe run_3r_eval.py --mode fts --email your-owner-email@example.com
.\.venv\Scripts\python.exe run_3r_eval.py --mode vector --email your-owner-email@example.com
```

The runner writes a git-ignored redacted report under `../evals/reports/`: metrics, source IDs, and boolean review flags only. It does not write questions, note text, answers, access tokens, or API keys. Vector indexing and answer generation use the configured OpenAI key; keyword indexing itself does not. The local SQLite ledger reserves a worst-case cost before every provider request and refuses to exceed the configured $1 aggregate experiment limit. It allows no automatic provider retries.
