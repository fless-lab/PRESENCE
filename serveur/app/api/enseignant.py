from fastapi import APIRouter, Body, Depends
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db import session
from app.modeles import Cours, Inscription, Salle, Seance
from app.securite.requetes import Signataire, enseignant
from app.services import seances

router = APIRouter(tags=["enseignant"])


@router.get("/enseignant/cours")
def mes_cours(s: Signataire = Depends(enseignant), db: Session = Depends(session)) -> dict:
    assert s.personne is not None
    cours = db.scalars(
        select(Cours).where(Cours.enseignant_id == s.personne.id).order_by(Cours.code)
    )
    resultat = []
    for c in cours:
        n = db.scalar(
            select(func.count()).select_from(Inscription).where(Inscription.cours_id == c.id)
        )
        ouverte = db.scalars(
            select(Seance).where(Seance.cours_id == c.id, Seance.etat.in_(("ACTIVE", "PAUSE")))
        ).first()
        resultat.append(
            {
                "code": c.code,
                "intitule": c.intitule,
                "groupe": c.groupe,
                "inscrits": n or 0,
                "seance_ouverte": ouverte.id if ouverte else None,
            }
        )
    return {"cours": resultat}


@router.get("/enseignant/presence")
def ma_presence(s: Signataire = Depends(enseignant), db: Session = Depends(session)):
    assert s.personne is not None
    lieu = seances.presence_enseignant(db, s.personne)
    if lieu is None:
        return None
    salle_id, depuis = lieu
    salle = db.get(Salle, salle_id)
    return {
        "salle": salle_id,
        "nom_salle": salle.nom if salle else str(salle_id),
        "depuis_ms": depuis,
    }


@router.get("/enseignant/seances")
def mes_seances(s: Signataire = Depends(enseignant), db: Session = Depends(session)) -> dict:
    assert s.personne is not None
    liste = db.scalars(
        select(Seance)
        .where(Seance.enseignant_id == s.personne.id)
        .order_by(Seance.debut_ms.desc())
        .limit(100)
    )
    sortie = []
    for x in liste:
        r = seances.resume(db, x)
        r["ancrage"] = x.ancrage
        sortie.append(r)
    return {"seances": sortie}


@router.post("/seances")
def demarrer(
    cours: str = Body(..., embed=True),
    s: Signataire = Depends(enseignant),
    db: Session = Depends(session),
) -> dict:
    seance = seances.demarrer(db, s, cours)
    return seances.resume(db, seance)


def _action(action: str):
    def route(seance_id: str, s: Signataire = Depends(enseignant), db: Session = Depends(session)):
        seance = seances.changer_etat(db, seances.obtenir(db, seance_id), s, action)
        return seances.resume(db, seance)

    return route


router.post("/seances/{seance_id}/pause")(_action("PAUSE"))
router.post("/seances/{seance_id}/reprise")(_action("REPRISE"))
router.post("/seances/{seance_id}/cloture")(_action("CLOTURE"))


@router.get("/seances/{seance_id}/direct")
def direct(seance_id: str, s: Signataire = Depends(enseignant), db: Session = Depends(session)):
    seance = seances.obtenir(db, seance_id)
    seances._controle_enseignant(seance, s)
    d = seances.detail(db, seance)
    d["inscrits_detail"] = d["etudiants"]
    d["inscrits"] = d.pop("etudiants")
    d["nb_inscrits"] = len(d["inscrits"])
    return d


@router.post("/seances/{seance_id}/validations")
def valider(
    seance_id: str,
    matricule: str = Body(...),
    motif: str = Body(""),
    s: Signataire = Depends(enseignant),
    db: Session = Depends(session),
) -> dict:
    seances.decider(db, seances.obtenir(db, seance_id), s, matricule, motif)
    return {"ok": True}


@router.post("/seances/{seance_id}/decisions")
def decider(
    seance_id: str,
    matricule: str = Body(...),
    decision: str = Body(...),
    motif: str = Body(""),
    s: Signataire = Depends(enseignant),
    db: Session = Depends(session),
) -> dict:
    seances.decider(db, seances.obtenir(db, seance_id), s, matricule, motif, decision)
    return {"ok": True}


@router.post("/seances/{seance_id}/scellement")
def sceller(seance_id: str, s: Signataire = Depends(enseignant), db: Session = Depends(session)):
    seance = seances.obtenir(db, seance_id)
    seances._controle_enseignant(seance, s)
    seances.sceller(db, seance)
    return {
        "racine": seance.racine,
        "nb_evenements": seance.nb_evenements,
        "ancrage": seance.ancrage,
    }
