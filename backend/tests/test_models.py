from datetime import UTC, datetime
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.models import TextNoteWrite


def test_text_note_rejects_whitespace_only_content() -> None:
    with pytest.raises(ValidationError, match="non-whitespace"):
        TextNoteWrite(
            client_note_id=uuid4(),
            content="   \n",
            created_at=datetime.now(UTC),
            updated_at=datetime.now(UTC),
        )


def test_text_note_requires_timezone_aware_timestamps() -> None:
    with pytest.raises(ValidationError, match="timezone"):
        TextNoteWrite(
            client_note_id=uuid4(),
            content="A local note",
            created_at=datetime.now(),
            updated_at=datetime.now(),
        )
