"""Registre Hyperledger Fabric, joint par la passerelle HTTP de registre/passerelle."""

from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request


class RegistreFabric:
    nom = "fabric"

    def __init__(self, url: str, delai_s: float = 30.0):
        self.url = url.rstrip("/")
        self.delai_s = delai_s

    def _appel(self, methode: str, chemin: str, corps: dict | None = None) -> dict | None:
        donnees = json.dumps(corps).encode() if corps is not None else None
        req = urllib.request.Request(
            self.url + chemin,
            data=donnees,
            method=methode,
            headers={"Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=self.delai_s) as r:
                return json.loads(r.read() or b"null")
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                return None
            detail = exc.read().decode(errors="replace")
            raise RuntimeError(f"passerelle Fabric : {exc.code} {detail}") from exc

    def ancrer_seance(self, seance, salle, debut, fin, racine, nb, observateurs) -> str:
        r = self._appel(
            "POST",
            "/ancrages",
            {
                "seance": seance,
                "salle": salle,
                "debut": str(debut),
                "fin": str(fin),
                "racine": racine,
                "nb_evenements": nb,
                "observateurs": observateurs,
            },
        )
        return (r or {})["transaction"]

    def ancrer_correction(self, seance, nouvelle_racine, racine_precedente, motif) -> str:
        r = self._appel(
            "POST",
            "/corrections",
            {
                "seance": seance,
                "nouvelle_racine": nouvelle_racine,
                "racine_precedente": racine_precedente,
                "motif": motif,
            },
        )
        return (r or {})["transaction"]

    def lire(self, seance: str) -> dict | None:
        a = self._appel("GET", "/ancrages/" + urllib.parse.quote(seance, safe=""))
        if a is None:
            return None
        return {
            "seance": a["seance"],
            "racine": a["racine"],
            "corrections": [
                {
                    "racine": c["racine"],
                    "racine_precedente": c["racine_precedente"],
                    "motif": c["motif"],
                    "transaction": c["transaction"],
                }
                for c in a.get("corrections") or []
            ],
            "transaction": a.get("transaction"),
            "registre": self.nom,
            "nb_evenements": a.get("nb_evenements"),
        }
