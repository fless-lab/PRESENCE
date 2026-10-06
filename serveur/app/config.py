"""Configuration lue dans les variables d'environnement."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


def _liste(valeur: str) -> list[str]:
    return [v.strip() for v in valeur.split(",") if v.strip()]


@dataclass
class Config:
    base_url: str = field(
        default_factory=lambda: os.environ.get(
            "PRESENCE_BASE_URL", "postgresql+psycopg://presence:presence@localhost:5432/presence"
        )
    )
    donnees: Path = field(
        default_factory=lambda: Path(os.environ.get("PRESENCE_DONNEES", "./donnees"))
    )
    registre: str = field(default_factory=lambda: os.environ.get("PRESENCE_REGISTRE", "local"))
    registre_url: str = field(
        default_factory=lambda: os.environ.get("PRESENCE_REGISTRE_URL", "http://localhost:8800")
    )
    attestation: str = field(
        default_factory=lambda: os.environ.get("PRESENCE_ATTESTATION", "souple")
    )
    cors: list[str] = field(
        default_factory=lambda: _liste(os.environ.get("PRESENCE_CORS", "http://localhost:5173"))
    )


config = Config()
