# JustDump backend — Milestone 3A

This FastAPI service supports one authenticated owner's explicit text-note upload and cloud read-back. It is not a synchronisation worker, backup service, retrieval service, or AI service.

## Local setup

1. Copy `.env.example` to `.env` and fill the three values described in [`../docs/local-configuration.md`](../docs/local-configuration.md). The actual file is ignored by Git.
2. Create and activate a virtual environment, then install the development dependencies:

   ```powershell
   python -m venv .venv
   .\.venv\Scripts\python.exe -m pip install -e ".[dev]"
   ```

3. In Supabase Dashboard, open **SQL Editor** and run the migrations in numeric order: [`migrations/0001_owner_text_notes.sql`](migrations/0001_owner_text_notes.sql), then [`migrations/0002_qualify_pgcrypto_digest.sql`](migrations/0002_qualify_pgcrypto_digest.sql). Keep the SQL result screen private; it may contain account-related metadata.
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
