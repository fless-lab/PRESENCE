"""Reçus de présence : événements du titulaire, preuves d'inclusion, signature du serveur."""

from __future__ import annotations

import json

from fastapi import HTTPException
from sqlalchemy.orm import Session

from app import horloge
from app.constantes import VERSION
from app.domaine import merkle
from app.domaine.canonique import octets
from app.modeles import Personne, Seance
from app.securite.crypto import CleServeur, b64
from app.services import evenements, seances

TYPES_COMMUNS = ("DEBUT", "PAUSE", "REPRISE", "CLOTURE")
TYPES_PERSONNELS = ("VALIDATION_MANUELLE", "DECISION", "CORRECTION")


def construire(db: Session, seance: Seance, personne: Personne, cle: CleServeur) -> dict:
    if seance.etat != "SCELLEE":
        raise HTTPException(409, "Le reçu est disponible une fois la séance scellée")
    calc = seances.calculer(db, seance)
    p = calc.presences.get(personne.matricule)
    if p is None:
        raise HTTPException(403, "Vous n'êtes pas inscrit à ce cours")
    appareil = calc.appareils.get(personne.matricule)
    mes_appareils = {a.id for a in personne.appareils}

    evts = evenements.de_la_seance(db, seance.id)
    feuilles = [e.canonique.encode("utf-8") for e in evts]
    racine = merkle.racine(feuilles).hex()
    if racine != seance.racine:
        raise HTTPException(500, "La base ne correspond plus à la racine scellée")

    retenus = []
    for i, e in enumerate(evts):
        if (
            e.type in TYPES_COMMUNS
            or (e.type == "ATTESTATION" and e.appareil in mes_appareils)
            or (e.type in TYPES_PERSONNELS and e.matricule == personne.matricule)
        ):
            retenus.append(
                {
                    "index": i,
                    "canonique": e.canonique,
                    "preuve": [h.hex() for h in merkle.preuve_inclusion(i, feuilles)],
                }
            )

    ancrage = seance.ancrage or {}
    recu = {
        "v": VERSION,
        "type": "RECU",
        "seance": {
            "id": seance.id,
            "cours": seance.cours.code,
            "intitule": seance.cours.intitule,
            "salle": seance.salle_id,
            "debut_ms": seance.debut_ms,
            "fin_ms": seance.fin_ms,
        },
        "titulaire": {
            "matricule": personne.matricule,
            "nom": personne.nom,
            "prenom": personne.prenom,
            "appareil": appareil,
        },
        "statut": p.statut,
        "fenetres": {"validees": len(p.validees), "total": p.total},
        "arbre": {"taille": len(feuilles), "racine": racine},
        "evenements": retenus,
        "ancrage": {"registre": ancrage.get("registre"), "transaction": ancrage.get("transaction")},
        "emis_ms": horloge.maintenant_ms(),
    }
    recu["sig_serveur"] = b64(cle.signer(octets(recu)))
    return recu


def verifier_signature(recu: dict, cle_publique_b64: str) -> bool:
    from app.securite.crypto import de_b64, verifier

    corps = {k: v for k, v in recu.items() if k != "sig_serveur"}
    return verifier(cle_publique_b64, octets(corps), de_b64(recu["sig_serveur"]))


def evenements_du_recu(recu: dict) -> list[dict]:
    return [json.loads(e["canonique"]) for e in recu["evenements"]]
