from hashlib import sha256
from typing import Any
from uuid import UUID

import httpx
from fastapi import HTTPException, status

from .config import Settings
from .models import CloudTextNote, CloudVaultItem, EvidenceChunk, TextNoteWrite, VaultItemWrite
from .retrieval import TextChunk


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

    async def get_text_notes(
        self, access_token: str, client_note_ids: list[UUID]
    ) -> list[CloudTextNote]:
        ids = ",".join(str(note_id) for note_id in client_note_ids)
        response = await self._client.get(
            f"{self._settings.rest_url}/text_notes",
            headers=self._headers(access_token),
            params={
                "select": "client_note_id,content,created_at,updated_at,cloud_created_at",
                "client_note_id": f"in.({ids})",
            },
        )
        self._raise_for_response(response)
        return [CloudTextNote.model_validate(record) for record in response.json()]

    async def upsert_vault_item(self, access_token: str, item: VaultItemWrite) -> CloudVaultItem:
        response = await self._client.post(
            f"{self._settings.rest_url}/rpc/upsert_vault_item",
            headers={**self._headers(access_token), "Content-Type": "application/json"},
            json={
                "p_client_item_id": str(item.client_item_id), "p_kind": item.kind,
                "p_source_url": item.source_url, "p_original_object_path": item.original_object_path,
                "p_mime_type": item.mime_type, "p_content_sha256": item.content_sha256,
                "p_created_at": item.created_at.isoformat(), "p_updated_at": item.updated_at.isoformat(),
            },
        )
        self._raise_for_response(response)
        return CloudVaultItem.model_validate(self._one_record(response.json()))

    async def upload_original(
        self, access_token: str, path: str, mime_type: str, content: bytes,
    ) -> None:
        response = await self._client.post(
            f"{str(self._settings.justdump_supabase_url).rstrip('/')}/storage/v1/object/justdump-originals/{path}",
            headers={**self._headers(access_token), "Content-Type": mime_type, "x-upsert": "false"},
            content=content,
        )
        # A retry after a successful object upload should continue to the
        # idempotent metadata RPC rather than manufacture a duplicate object.
        if response.status_code == status.HTTP_409_CONFLICT:
            return
        self._raise_for_response(response)

    async def get_vault_item(self, access_token: str, client_item_id: UUID) -> CloudVaultItem | None:
        response = await self._client.get(
            f"{self._settings.rest_url}/vault_items",
            headers=self._headers(access_token),
            params={
                "select": "client_item_id,kind,source_url,original_object_path,mime_type,processing_status,processing_error",
                "client_item_id": f"eq.{client_item_id}",
            },
        )
        self._raise_for_response(response)
        records = response.json()
        return CloudVaultItem.model_validate(records[0]) if records else None

    async def has_matching_chunks(
        self, access_token: str, note: CloudTextNote, chunking_version: str
    ) -> bool:
        response = await self._client.get(
            f"{self._settings.rest_url}/retrieval_chunks",
            headers={**self._headers(access_token), "Prefer": "count=exact"},
            params={
                "select": "chunk_index",
                "client_note_id": f"eq.{note.client_note_id}",
                "chunking_version": f"eq.{chunking_version}",
                "source_content_sha256": f"eq.{sha256(note.content.encode()).hexdigest()}",
                "limit": "1",
            },
        )
        self._raise_for_response(response)
        return response.headers.get("content-range", "").split("/")[-1] not in {"", "0", "*"}

    async def replace_chunks(
        self, access_token: str, note_id: UUID, chunking_version: str, chunks: list[TextChunk]
    ) -> int:
        response = await self._client.post(
            f"{self._settings.rest_url}/rpc/replace_retrieval_chunks",
            headers={**self._headers(access_token), "Content-Type": "application/json"},
            json={
                "p_client_note_id": str(note_id),
                "p_chunking_version": chunking_version,
                "p_source_content_sha256": chunks[0].source_content_sha256,
                "p_chunks": [
                    {"chunk_index": c.chunk_index, "start_offset": c.start_offset,
                     "end_offset": c.end_offset, "content": c.content}
                    for c in chunks
                ],
            },
        )
        self._raise_for_response(response)
        return int(response.json())

    async def existing_embedding_indexes(
        self, access_token: str, note_id: UUID, chunking_version: str, embedding_model: str
    ) -> set[int]:
        response = await self._client.get(
            f"{self._settings.rest_url}/retrieval_embeddings",
            headers=self._headers(access_token),
            params={
                "select": "chunk_index", "client_note_id": f"eq.{note_id}",
                "chunking_version": f"eq.{chunking_version}", "embedding_model": f"eq.{embedding_model}",
            },
        )
        self._raise_for_response(response)
        return {int(record["chunk_index"]) for record in response.json()}

    async def upsert_embeddings(
        self, access_token: str, note_id: UUID, chunking_version: str,
        embedding_model: str, embeddings: list[tuple[int, list[float]]]
    ) -> int:
        response = await self._client.post(
            f"{self._settings.rest_url}/rpc/upsert_retrieval_embeddings",
            headers={**self._headers(access_token), "Content-Type": "application/json"},
            json={
                "p_client_note_id": str(note_id), "p_chunking_version": chunking_version,
                "p_embedding_model": embedding_model,
                "p_embeddings": [
                    {"chunk_index": index, "embedding": "[" + ",".join(map(str, vector)) + "]"}
                    for index, vector in embeddings
                ],
            },
        )
        self._raise_for_response(response)
        return int(response.json())

    async def search_fts(self, access_token: str, question: str, limit: int) -> list[EvidenceChunk]:
        return await self._search("search_retrieval_fts", access_token, {"p_query": question, "p_limit": limit})

    async def search_vector(
        self, access_token: str, embedding_model: str, vector: list[float], limit: int
    ) -> list[EvidenceChunk]:
        return await self._search(
            "search_retrieval_vector", access_token,
            {"p_embedding_model": embedding_model, "p_query_embedding": "[" + ",".join(map(str, vector)) + "]", "p_limit": limit},
        )

    async def _search(self, function: str, access_token: str, payload: dict[str, Any]) -> list[EvidenceChunk]:
        response = await self._client.post(
            f"{self._settings.rest_url}/rpc/{function}", headers={**self._headers(access_token), "Content-Type": "application/json"}, json=payload
        )
        self._raise_for_response(response)
        return [EvidenceChunk.model_validate(record) for record in response.json()]

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
