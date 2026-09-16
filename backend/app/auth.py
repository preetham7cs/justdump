from dataclasses import dataclass
from uuid import UUID

import jwt
from fastapi import HTTPException, status
from jwt import PyJWKClient

from .config import Settings


@dataclass(frozen=True)
class Principal:
    user_id: UUID


class SupabaseJwtVerifier:
    """Verify Supabase access tokens before a request can reach PostgREST."""

    def __init__(self, settings: Settings) -> None:
        self._owner_id = UUID(str(settings.justdump_owner_id))
        self._issuer = settings.auth_issuer
        self._jwks_client = PyJWKClient(settings.jwks_url)

    def require_owner(self, token: str) -> Principal:
        try:
            signing_key = self._jwks_client.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256", "ES256", "EdDSA"],
                audience="authenticated",
                issuer=self._issuer,
            )
            user_id = UUID(claims["sub"])
        except (KeyError, ValueError, jwt.PyJWTError) as error:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="A valid JustDump sign-in is required.",
                headers={"WWW-Authenticate": "Bearer"},
            ) from error

        if user_id != self._owner_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="This account is not authorised for JustDump.",
            )

        return Principal(user_id=user_id)
