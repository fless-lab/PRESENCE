"""Création des événements, sous forme canonique et hachée."""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app import horloge
from app.constantes import VERSION
from app.domaine.canonique import canonique
from app.domaine.merkle import hacher_feuille
from app.modeles import Evenement, Seance


def enregistrer(
    db: Session,
    *,
    type: str,
    auteur: str,
    horodatage: int,
    contenu: dict,
    preuves: dict,
    salle_id: int | None,
    seance: Seance | None = None,
    appareil: str | None = None,
    compteur: int | None = None,
    matricule: str | None = None,
) -> Evenement:
    document = {
        "v": VERSION,
        "type": type,
        "seance": seance.id if seance else None,
        "salle": salle_id,
        "auteur": auteur,
        "horodatage": horodatage,
        "contenu": contenu,
        "preuves": preuves,
    }
    canon = canonique(document)
    e = Evenement(
        seance_id=seance.id if seance else None,
        type=type,
        auteur=auteur,
        salle_id=salle_id,
        horodatage=horodatage,
        appareil=appareil,
        compteur=compteur,
        matricule=matricule,
        canonique=canon,
        hachage=hacher_feuille(canon.encode("utf-8")).hex(),
        recu_ms=horloge.maintenant_ms(),
    )
    db.add(e)
    db.flush()
    return e


def de_la_seance(db: Session, seance_id: str) -> list[Evenement]:
    """Événements d'une séance dans l'ordre des feuilles de l'arbre : (horodatage, hachage)."""
    return list(
        db.scalars(
            select(Evenement)
            .where(Evenement.seance_id == seance_id)
            .order_by(Evenement.horodatage, Evenement.hachage)
        )
    )
