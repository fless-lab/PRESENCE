#!/usr/bin/env python3
"""Démonstration d'audit : falsifie directement la base, comme le ferait un administrateur malveillant.

Le script ajoute de fausses attestations pour un étudiant absent d'une séance scellée,
en contournant l'API. La base dit alors « présent ». L'audit du tableau de bord
(ou `GET /audit`) détecte l'écart : la racine recalculée ne correspond plus à la
racine ancrée sur le registre.

    python falsifier.py --base sqlite:///donnees/presence.db --seance S-20261001-204-01 --matricule E005

Dépendance : sqlalchemy (et psycopg pour PostgreSQL).
"""

from __future__ import annotations

import argparse
import hashlib
import json

from sqlalchemy import create_engine, text


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True, help="URL SQLAlchemy de la base")
    ap.add_argument("--seance", required=True)
    ap.add_argument("--matricule", required=True, help="étudiant à rendre présent")
    args = ap.parse_args()

    moteur = create_engine(args.base)
    with moteur.begin() as cx:
        appareil = cx.execute(
            text("SELECT a.id FROM appareils a JOIN personnes p ON p.id = a.personne_id "
                 "WHERE p.matricule = :m ORDER BY a.enrole_ms DESC LIMIT 1"),
            {"m": args.matricule},
        ).scalar()
        modeles = cx.execute(
            text("SELECT seance_id, type, auteur, salle_id, horodatage, canonique FROM evenements "
                 "WHERE seance_id = :s AND type = 'ATTESTATION' ORDER BY horodatage"),
            {"s": args.seance},
        ).fetchall()
        if appareil is None or not modeles:
            raise SystemExit("Appareil ou attestations introuvables")
        ajouts = 0
        for i, m in enumerate(modeles[:: max(1, len(modeles) // 20)]):
            doc = json.loads(m.canonique)
            doc["contenu"]["appareil"] = appareil
            doc["contenu"]["compteur"] = 900_000 + i
            canon = json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
            cx.execute(
                text("INSERT INTO evenements (seance_id, type, auteur, salle_id, horodatage, "
                     "appareil, compteur, canonique, hachage, recu_ms) VALUES "
                     "(:s, 'ATTESTATION', :a, :salle, :t, :app, :c, :canon, :h, :t)"),
                {"s": m.seance_id, "a": m.auteur, "salle": m.salle_id, "t": m.horodatage + 1,
                 "app": appareil, "c": 900_000 + i, "canon": canon,
                 "h": hashlib.sha256(b"\x00" + canon.encode()).hexdigest()},
            )
            ajouts += 1
    print(f"{ajouts} fausses attestations insérées pour {args.matricule} dans {args.seance}.")
    print("Lancer l'audit : la séance doit apparaître en écart.")


if __name__ == "__main__":
    main()
