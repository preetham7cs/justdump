"""A local, durable maximum-spend ledger for the 3R synthetic experiment."""

import sqlite3
from dataclasses import dataclass
from pathlib import Path
from uuid import uuid4


class BudgetExceeded(Exception):
    """Raised before a provider request that could exceed the local cap."""


@dataclass(frozen=True)
class BudgetReservation:
    request_id: str
    reserved_usd: float


class ExperimentBudgetLedger:
    """Reserve worst-case cost before a call; unknown outcomes stay reserved.

    The ledger deliberately stores no prompts, note text, questions, API keys,
    or responses. SQLite's `BEGIN IMMEDIATE` serialises reservations from the
    single local FastAPI process used by this milestone.
    """

    def __init__(self, path: Path, limit_usd: float) -> None:
        self._path = path
        self._limit_usd = limit_usd
        self._path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                """create table if not exists reservations (
                    request_id text primary key,
                    reserved_usd real not null,
                    actual_usd real,
                    status text not null
                )"""
            )

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self._path, isolation_level=None)

    def reserve(self, worst_case_usd: float) -> BudgetReservation:
        if worst_case_usd < 0:
            raise ValueError("worst_case_usd must not be negative")
        request_id = str(uuid4())
        with self._connect() as connection:
            connection.execute("begin immediate")
            used = connection.execute(
                "select coalesce(sum(reserved_usd), 0) from reservations"
            ).fetchone()[0]
            if used + worst_case_usd > self._limit_usd + 1e-9:
                connection.execute("rollback")
                raise BudgetExceeded("The local $1 experiment limit has been reached.")
            connection.execute(
                "insert into reservations values (?, ?, null, 'reserved')",
                (request_id, worst_case_usd),
            )
            connection.execute("commit")
        return BudgetReservation(request_id=request_id, reserved_usd=worst_case_usd)

    def reconcile(self, reservation: BudgetReservation, actual_usd: float) -> None:
        if actual_usd < 0 or actual_usd > reservation.reserved_usd + 1e-9:
            raise ValueError("actual cost must be within the pre-reserved maximum")
        with self._connect() as connection:
            connection.execute(
                "update reservations set reserved_usd = ?, actual_usd = ?, status = 'settled' "
                "where request_id = ?",
                (actual_usd, actual_usd, reservation.request_id),
            )

    def remaining_usd(self) -> float:
        with self._connect() as connection:
            used = connection.execute(
                "select coalesce(sum(reserved_usd), 0) from reservations"
            ).fetchone()[0]
        return max(0.0, self._limit_usd - used)
