from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import session
from app.etat import cle_serveur
from app.modeles import Inscription, Seance
from app.securite.requetes import Signataire, telephone
from app.services import recus, seances

router = APIRouter(prefix="/moi", tags=["téléphone"])


def _profil(s: Signataire) -> dict:
    p, a = s.personne, s.appareil
    assert p is not None and a is not None
    return {
        "matricule": p.matricule,
        "nom": p.nom,
        "prenom": p.prenom,
        "role": p.role,
        "appareil": {
            "id": a.id,
            "modele": a.modele,
            "attestation": a.attestation,
            "deverrouillage_recent": a.deverrouillage_recent,
        },
    }


@router.get("")
def moi(s: Signataire = Depends(telephone)) -> dict:
    return _profil(s)


def _vue_personnelle(db: Session, seance: Seance, matricule: str) -> dict:
    calc = seances.calculer(db, seance)
    p = calc.presences.get(matricule)
    base = seances.resume(db, seance, calc)
    base["statut"] = p.statut if p else None
    base["fenetres"] = {"total": calc.total, "validees": p.validees if p else []}
    base["ancrage"] = seance.ancrage
    return base


@router.get("/seance-courante")
def seance_courante(s: Signataire = Depends(telephone), db: Session = Depends(session)):
    assert s.personne is not None
    seance = db.scalars(
        select(Seance)
        .join(Inscription, Inscription.cours_id == Seance.cours_id)
        .where(Inscription.personne_id == s.personne.id, Seance.etat.in_(("ACTIVE", "PAUSE")))
        .order_by(Seance.debut_ms.desc())
    ).first()
    return _vue_personnelle(db, seance, s.personne.matricule) if seance else None


@router.get("/historique")
def historique(s: Signataire = Depends(telephone), db: Session = Depends(session)) -> dict:
    assert s.personne is not None
    if s.personne.role == "ENSEIGNANT":
        liste = db.scalars(
            select(Seance)
            .where(Seance.enseignant_id == s.personne.id)
            .order_by(Seance.debut_ms.desc())
            .limit(100)
        )
        return {"seances": [seances.resume(db, x) for x in liste]}
    liste = db.scalars(
        select(Seance)
        .join(Inscription, Inscription.cours_id == Seance.cours_id)
        .where(Inscription.personne_id == s.personne.id)
        .order_by(Seance.debut_ms.desc())
        .limit(100)
    )
    return {"seances": [_vue_personnelle(db, x, s.personne.matricule) for x in liste]}


@router.get("/recus/{seance_id}")
def recu(seance_id: str, s: Signataire = Depends(telephone), db: Session = Depends(session)):
    seance = seances.obtenir(db, seance_id)
    if s.personne is None:
        raise HTTPException(403, "Accès refusé")
    return recus.construire(db, seance, s.personne, cle_serveur())
