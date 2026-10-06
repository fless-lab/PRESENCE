"""Objets partagés créés au premier usage."""

from __future__ import annotations

from app.config import config
from app.securite.crypto import CleServeur

_cle: CleServeur | None = None


def cle_serveur() -> CleServeur:
    global _cle
    if _cle is None:
        _cle = CleServeur(config.donnees)
    return _cle


def reinitialiser() -> None:
    global _cle
    _cle = None
