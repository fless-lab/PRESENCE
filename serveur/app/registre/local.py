"""Registre local : journal en ajout seul, chaque entrée liée à la précédente par son hachage.

Il remplace Fabric pendant le développement. Il n'apporte pas la garantie
d'un registre partagé : une seule machine le détient. Il détecte en revanche
toute modification d'une entrée existante, car la chaîne de hachages se rompt.
"""

from __future__ import annotations

import json
import threading
from pathlib import Path

from app import horloge
from app.domaine.canonique import canonique
from app.securite.crypto import sha256_hex

ZERO = "0" * 64


class RegistreLocal:
    nom = "local"

    def __init__(self, chemin: Path):
        self.chemin = chemin
        self.chemin.parent.mkdir(parents=True, exist_ok=True)
        self._verrou = threading.Lock()

    def _entrees(self) -> list[dict]:
        if not self.chemin.exists():
            return []
        entrees = []
        precedent = ZERO
        for ligne in self.chemin.read_text(encoding="utf-8").splitlines():
            if not ligne.strip():
                continue
            e = json.loads(ligne)
            corps = {k: v for k, v in e.items() if k != "hachage"}
            if e.get("precedent") != precedent or sha256_hex(canonique(corps).encode()) != e.get(
                "hachage"
            ):
                raise RuntimeError("registre local altéré : chaîne de hachages rompue")
            precedent = e["hachage"]
            entrees.append(e)
        return entrees

    def _ajouter(self, contenu: dict) -> str:
        with self._verrou:
            entrees = self._entrees()
            corps = {
                **contenu,
                "n": len(entrees),
                "horodatage": horloge.maintenant_ms(),
                "precedent": entrees[-1]["hachage"] if entrees else ZERO,
            }
            corps["hachage"] = sha256_hex(canonique(corps).encode())
            with self.chemin.open("a", encoding="utf-8") as f:
                f.write(canonique(corps) + "\n")
            return corps["hachage"]

    def ancrer_seance(self, seance, salle, debut, fin, racine, nb, observateurs) -> str:
        if self.lire(seance) is not None:
            raise ValueError(f"la séance {seance} est déjà ancrée")
        return self._ajouter(
            {
                "type": "ANCRAGE",
                "seance": seance,
                "salle": salle,
                "debut": debut,
                "fin": fin,
                "racine": racine,
                "nb_evenements": nb,
                "observateurs": observateurs,
            }
        )

    def ancrer_correction(self, seance, nouvelle_racine, racine_precedente, motif) -> str:
        actuel = self.lire(seance)
        if actuel is None:
            raise ValueError(f"aucun ancrage pour la séance {seance}")
        courante = (
            actuel["corrections"][-1]["racine"] if actuel["corrections"] else actuel["racine"]
        )
        if courante != racine_precedente:
            raise ValueError("la racine précédente ne correspond pas à la racine courante")
        return self._ajouter(
            {
                "type": "CORRECTION",
                "seance": seance,
                "racine": nouvelle_racine,
                "racine_precedente": racine_precedente,
                "motif": motif,
            }
        )

    def lire(self, seance: str) -> dict | None:
        resultat = None
        for e in self._entrees():
            if e["seance"] != seance:
                continue
            if e["type"] == "ANCRAGE":
                resultat = {
                    "seance": seance,
                    "racine": e["racine"],
                    "corrections": [],
                    "transaction": e["hachage"],
                    "registre": self.nom,
                    "nb_evenements": e["nb_evenements"],
                }
            elif e["type"] == "CORRECTION" and resultat is not None:
                resultat["corrections"].append(
                    {
                        "racine": e["racine"],
                        "racine_precedente": e["racine_precedente"],
                        "motif": e["motif"],
                        "transaction": e["hachage"],
                    }
                )
        return resultat
