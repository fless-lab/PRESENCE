from fastapi import APIRouter, Body, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import horloge
from app.constantes import PERIODE_NONCE_S
from app.db import session
from app.modeles import Appareil, Cours, Inscription, Personne
from app.securite.requetes import Signataire, equipement
from app.services import seances, temoignages

router = APIRouter(prefix="/equipements/moi", tags=["équipements"])


def _cles(db: Session, personnes_ids) -> list[dict]:
    appareils = db.scalars(
        select(Appareil)
        .where(Appareil.personne_id.in_(personnes_ids), Appareil.actif.is_(True))
        .order_by(Appareil.id)
    )
    return [{"id": a.id, "cle": a.cle_publique} for a in appareils]


@router.get("/config")
def config(s: Signataire = Depends(equipement), db: Session = Depends(session)) -> dict:
    e = s.equipement
    assert e is not None
    seance = seances.seance_active_salle(db, e.salle_id)
    if seance is None:
        ids = select(Personne.id).where(Personne.role == "ENSEIGNANT")
        description = None
    else:
        cours = db.get(Cours, seance.cours_id)
        ids = [
            i
            for (i,) in db.execute(
                select(Inscription.personne_id).where(Inscription.cours_id == seance.cours_id)
            )
        ]
        if cours and cours.enseignant_id:
            ids.append(cours.enseignant_id)
        description = {"numero": seance.numero, "etat": seance.etat, "id": seance.id}
    return {
        "heure_ms": horloge.maintenant_ms(),
        "nom": e.nom,
        "salle": e.salle_id,
        "periode_nonce_s": PERIODE_NONCE_S,
        "seance": description,
        "cles": _cles(db, ids),
    }


@router.post("/temoignages")
def recevoir(
    temoignages_: list[dict] = Body(..., alias="temoignages", embed=True),
    s: Signataire = Depends(equipement),
    db: Session = Depends(session),
) -> dict:
    assert s.equipement is not None
    return temoignages.traiter(db, s.equipement, temoignages_)
