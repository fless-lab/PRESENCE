"""JSON canonique : clés triées, aucun espace, UTF-8, pas de nombre flottant."""

from __future__ import annotations

import json
from typing import Any


def _controler(valeur: Any) -> None:
    if isinstance(valeur, float):
        raise ValueError("nombre flottant interdit dans le JSON canonique")
    if isinstance(valeur, dict):
        for v in valeur.values():
            _controler(v)
    elif isinstance(valeur, list):
        for v in valeur:
            _controler(v)


def canonique(valeur: Any) -> str:
    _controler(valeur)
    return json.dumps(valeur, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def octets(valeur: Any) -> bytes:
    return canonique(valeur).encode("utf-8")
