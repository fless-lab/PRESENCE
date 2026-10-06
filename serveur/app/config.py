"""Configuration lue dans les variables d'environnement."""

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    base_url: str = os.environ.get(
        "PRESENCE_BASE_URL", "postgresql://presence:presence@localhost:5432/presence"
    )
    env: str = os.environ.get("PRESENCE_ENV", "developpement")


config = Config()
