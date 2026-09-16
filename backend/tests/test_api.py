from datetime import UTC, datetime
from uuid import UUID, uuid4

from fastapi import HTTPException, status
from fastapi.testclient import TestClient

from app.auth import Principal
from app.config import Settings
from app.main import create_app
from app.models import CloudTextNote, TextNoteWrite


OWNER_ID = UUID("11111111-1111-4111-8111-111111111111")
OTHER_ID = UUID("22222222-2222-4222-8222-222222222222")


class FakeVerifier:
    def __init__(self, user_id: UUID) -> None:
        self._user_id = user_id

    def require_owner(self, _: str) -> Principal:
        if self._user_id != OWNER_ID:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="This account is not authorised for JustDump.",
            )
        return Principal(user_id=self._user_id)


class FakeGateway:
    def __init__(self) -> None:
        self.records: dict[UUID, CloudTextNote] = {}

    async def close(self) -> None:
        return None

    async def upsert_text_note(self, _: str, note: TextNoteWrite) -> CloudTextNote:
        existing = self.records.get(note.client_note_id)
        if existing:
            if existing.content != note.content:
                raise HTTPException(status_code=409, detail="immutable conflict")
            return existing
        cloud_note = CloudTextNote(
            client_note_id=note.client_note_id,
            content=note.content,
            created_at=note.created_at,
            updated_at=note.updated_at,
            cloud_created_at=datetime.now(UTC),
        )
        self.records[note.client_note_id] = cloud_note
        return cloud_note

    async def get_text_note(self, _: str, note_id: UUID) -> CloudTextNote | None:
        return self.records.get(note_id)


def make_client(user_id: UUID = OWNER_ID) -> tuple[TestClient, FakeGateway]:
    settings = Settings(
        justdump_supabase_url="https://example.supabase.co",
        justdump_supabase_publishable_key="publishable-test-key",
        justdump_owner_id=OWNER_ID,
    )
    gateway = FakeGateway()
    app = create_app(settings=settings, gateway=gateway, verifier=FakeVerifier(user_id))
    return TestClient(app), gateway


def note_payload(note_id: UUID) -> dict[str, str]:
    timestamp = datetime.now(UTC).isoformat()
    return {
        "client_note_id": str(note_id),
        "content": "A persistent local note.",
        "created_at": timestamp,
        "updated_at": timestamp,
    }


def test_missing_bearer_token_is_rejected() -> None:
    client, _ = make_client()

    response = client.get("/v1/me")

    assert response.status_code == 401


def test_non_owner_is_rejected_before_cloud_access() -> None:
    client, gateway = make_client(OTHER_ID)

    response = client.post(
        "/v1/text-notes",
        headers={"Authorization": "Bearer valid-but-other-user"},
        json=note_payload(uuid4()),
    )

    assert response.status_code == 403
    assert gateway.records == {}


def test_owner_can_repeat_upload_and_read_back_same_note() -> None:
    client, gateway = make_client()
    note_id = uuid4()
    payload = note_payload(note_id)
    headers = {"Authorization": "Bearer owner-token"}

    first = client.post("/v1/text-notes", headers=headers, json=payload)
    second = client.post("/v1/text-notes", headers=headers, json=payload)
    read_back = client.get(f"/v1/text-notes/{note_id}", headers=headers)

    assert first.status_code == 201
    assert second.status_code == 201
    assert read_back.status_code == 200
    assert len(gateway.records) == 1
    assert read_back.json()["client_note_id"] == str(note_id)
    assert read_back.json()["content"] == payload["content"]


def test_owner_cannot_reuse_id_for_different_content() -> None:
    client, _ = make_client()
    note_id = uuid4()
    headers = {"Authorization": "Bearer owner-token"}
    first_payload = note_payload(note_id)
    second_payload = {**first_payload, "content": "Different content."}

    client.post("/v1/text-notes", headers=headers, json=first_payload)
    conflict = client.post("/v1/text-notes", headers=headers, json=second_payload)

    assert conflict.status_code == 409
