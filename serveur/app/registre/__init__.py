"""Accès au registre. Seul module du serveur qui dépend de la technologie de registre.

Deux implémentations :
- `local` : journal chaîné dans un fichier, pour développer et démontrer sans Fabric ;
- `fabric` : appel de la passerelle registre/passerelle, qui soumet au chaincode d'ancrage.
"""

from __future__ import annotations

from typing import Protocol

from app.config import config


class Registre(Protocol):
    nom: str

    def ancrer_seance(
        self,
        seance: str,
        salle: int,
        debut: int,
        fin: int,
        racine: str,
        nb: int,
        observateurs: list[str],
    ) -> str: ...

    def ancrer_correction(
        self, seance: str, nouvelle_racine: str, racine_precedente: str, motif: str
    ) -> str: ...

    def lire(self, seance: str) -> dict | None: ...


_registre: Registre | None = None


def registre() -> Registre:
    global _registre
    if _registre is None:
        if config.registre == "fabric":
            from app.registre.fabric import RegistreFabric

            _registre = RegistreFabric(config.registre_url)
        else:
            from app.registre.local import RegistreLocal

            _registre = RegistreLocal(config.donnees / "registre_local.jsonl")
    return _registre


def reinitialiser() -> None:
    global _registre
    _registre = None


def racine_courante(ancrage: dict) -> str:
    corrections = ancrage.get("corrections") or []
    return corrections[-1]["racine"] if corrections else ancrage["racine"]
