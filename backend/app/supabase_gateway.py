from typing import Any
from uuid import UUID

import httpx
from fastapi import HTTPException, status

from .config import Settings
from .models import CloudTextNote, TextNoteWrite


class SupabaseGateway:
    """Call PostgREST as the authenticated owner, never as a privileged role."""

    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None) -> None:
        self._settings = settings
        self._client = client or httpx.AsyncClient(timeout=15.0)
        self._owns_client = client is None

    async def close(self) -> None:
        if self._owns_client:
            await self._client.aclose()

    def _headers(self, access_token: str) -> dict[str, str]:
        return {
            "apikey": self._settings.justdump_supabase_publishable_key,
            "Authorization": f"Bearer {access_token}",
        }

    async def upsert_text_note(
        self,
        access_token: str,
        note: TextNoteWrite,
    ) -> CloudTextNote:
        response = await self._client.post(
            f"{self._settings.rest_url}/rpc/upsert_text_note",
            headers={**self._headers(access_token), "Content-Type": "application/json"},
            json={
                "p_client_note_id": str(note.client_note_id),
                "p_content": note.content,
                "p_created_at": note.created_at.isoformat(),
                "p_updated_at": note.updated_at.isoformat(),
            },
        )
        self._raise_for_response(response)
        return CloudTextNote.model_validate(self._one_record(response.json()))

    async def get_text_note(
        self,
        access_token: str,
        client_note_id: UUID,
    ) -> CloudTextNote | None:
        response = await self._client.get(
            f"{self._settings.rest_url}/text_notes",
            headers=self._headers(access_token),
            params={
                "select": "client_note_id,content,created_at,updated_at,cloud_created_at",
                "client_note_id": f"eq.{client_note_id}",
            },
        )
        self._raise_for_response(response)
        records = response.json()
        if not records:
            return None
        return CloudTextNote.model_validate(records[0])

    @staticmethod
    def _one_record(payload: Any) -> dict[str, Any]:
        if isinstance(payload, list):
            if len(payload) != 1:
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail="Cloud storage returned an unexpected note response.",
                )
            return payload[0]
        if isinstance(payload, dict):
            return payload
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Cloud storage returned an unexpected note response.",
        )

    @staticmethod
    def _raise_for_response(response: httpx.Response) -> None:
        if response.is_success:
            return

        # The SQL function raises this only when one local ID is reused with
        # different immutable content. It is a client conflict, not a retry.
        response_text = response.text
        if "different immutable content" in response_text:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="This local note ID already exists with different cloud content.",
            )
        if response.status_code in {401, 403}:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Cloud storage rejected this account.",
            )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Cloud storage is temporarily unavailable. The local note is unchanged.",
        )
