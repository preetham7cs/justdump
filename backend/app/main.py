from contextlib import asynccontextmanager
from typing import Annotated
from uuid import UUID

from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .auth import Principal, SupabaseJwtVerifier
from .config import Settings, get_settings
from .models import CloudTextNote, OwnerIdentity, TextNoteWrite
from .supabase_gateway import SupabaseGateway


def create_app(
    settings: Settings | None = None,
    gateway: SupabaseGateway | None = None,
    verifier: SupabaseJwtVerifier | None = None,
) -> FastAPI:
    settings = settings or get_settings()
    gateway = gateway or SupabaseGateway(settings)
    verifier = verifier or SupabaseJwtVerifier(settings)
    bearer = HTTPBearer(auto_error=False)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        yield
        await gateway.close()

    app = FastAPI(
        title="JustDump API",
        version="0.1.0",
        description="Single-owner explicit text-note sync for Milestone 3A.",
        lifespan=lifespan,
    )

    async def require_owner(
        credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    ) -> tuple[Principal, str]:
        if credentials is None or credentials.scheme.lower() != "bearer":
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="A valid JustDump sign-in is required.",
                headers={"WWW-Authenticate": "Bearer"},
            )
        return verifier.require_owner(credentials.credentials), credentials.credentials

    OwnerRequest = Annotated[tuple[Principal, str], Depends(require_owner)]

    @app.get("/healthz", tags=["operational"])
    async def healthz() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/v1/me", response_model=OwnerIdentity, tags=["identity"])
    async def me(request: OwnerRequest) -> OwnerIdentity:
        principal, _ = request
        return OwnerIdentity(owner_id=principal.user_id)

    @app.post(
        "/v1/text-notes",
        response_model=CloudTextNote,
        status_code=status.HTTP_201_CREATED,
        tags=["text-notes"],
    )
    async def upload_text_note(
        note: TextNoteWrite,
        request: OwnerRequest,
    ) -> CloudTextNote:
        _, access_token = request
        return await gateway.upsert_text_note(access_token, note)

    @app.get(
        "/v1/text-notes/{client_note_id}",
        response_model=CloudTextNote,
        tags=["text-notes"],
    )
    async def read_text_note(
        client_note_id: UUID,
        request: OwnerRequest,
    ) -> CloudTextNote:
        _, access_token = request
        note = await gateway.get_text_note(access_token, client_note_id)
        if note is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="No cloud note exists with that local ID for this account.",
            )
        return note

    return app
