import csv
import io
from datetime import UTC, datetime

from fastapi import APIRouter, Body, Depends, File, HTTPException, UploadFile
from fastapi.responses import Response
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import horloge
from app.constantes import EQUIPEMENT_EN_LIGNE_MS
from app.db import session
from app.modeles import Equipement, Personne, Salle, Seance
from app.securite.crypto import charger_cle_publique
from app.securite.jetons import emettre_jeton, personnel, verifier_mot_de_passe
from app.services import evenements, personnes, seances

router = APIRouter(tags=["personnel"])

LECTURE = personnel("SCOLARITE", "ADMIN", "AUDITEUR")
GESTION = personnel("SCOLARITE", "ADMIN")
TECHNIQUE = personnel("ADMIN")


@router.post("/auth/connexion")
def connexion(
    matricule: str = Body(...), mot_de_passe: str = Body(...), db: Session = Depends(session)
) -> dict:
    p = db.scalar(select(Personne).where(Personne.matricule == matricule))
    if (
        p is None
        or p.role not in ("SCOLARITE", "ADMIN", "AUDITEUR")
        or not verifier_mot_de_passe(mot_de_passe, p.mot_de_passe)
    ):
        raise HTTPException(401, "Identifiants incorrects")
    return {
        "jeton": emettre_jeton(p),
        "personne": {"matricule": p.matricule, "nom": p.nom, "prenom": p.prenom, "role": p.role},
    }


def _equipement(e: Equipement, maintenant: int) -> dict:
    return {
        "nom": e.nom,
        "salle": e.salle_id,
        "nom_salle": e.salle.nom if e.salle else "",
        "type": e.type,
        "actif": e.actif,
        "vu_ms": e.vu_ms,
        "en_ligne": bool(e.actif and e.vu_ms and maintenant - e.vu_ms < EQUIPEMENT_EN_LIGNE_MS),
    }


@router.get("/admin/apercu")
def apercu(_: Personne = Depends(LECTURE), db: Session = Depends(session)) -> dict:
    maintenant = horloge.maintenant_ms()
    debut_jour = int(
        datetime.fromtimestamp(maintenant / 1000, tz=UTC)
        .replace(hour=0, minute=0, second=0, microsecond=0)
        .timestamp()
        * 1000
    )
    actives = list(
        db.scalars(
            select(Seance).where(Seance.etat.in_(("ACTIVE", "PAUSE"))).order_by(Seance.debut_ms)
        )
    )
    du_jour = list(db.scalars(select(Seance).where(Seance.debut_ms >= debut_jour)))
    resumes_actives = [seances.resume(db, s) for s in actives]
    presents = inscrits = 0
    for s in du_jour:
        r = seances.resume(db, s)
        presents += r["presents"]
        inscrits += r["inscrits"]
    equipements = [
        _equipement(e, maintenant) for e in db.scalars(select(Equipement).order_by(Equipement.nom))
    ]
    return {
        "maintenant_ms": maintenant,
        "chiffres": {
            "seances_actives": len(actives),
            "seances_du_jour": len(du_jour),
            "taux_presence": (presents / inscrits) if inscrits else None,
            "equipements_en_ligne": sum(1 for e in equipements if e["en_ligne"]),
            "equipements_total": len(equipements),
        },
        "seances_actives": resumes_actives,
        "equipements": equipements,
    }


@router.post("/admin/import")
async def importer(
    fichier: UploadFile = File(...), _: Personne = Depends(GESTION), db: Session = Depends(session)
) -> dict:
    contenu = (await fichier.read()).decode("utf-8", errors="replace")
    return personnes.importer_csv(db, contenu)


@router.get("/admin/personnes")
def liste_personnes(_: Personne = Depends(LECTURE), db: Session = Depends(session)) -> dict:
    sortie = []
    for p in db.scalars(select(Personne).order_by(Personne.role, Personne.nom, Personne.prenom)):
        actif = next((a for a in p.appareils if a.actif), None)
        sortie.append(
            {
                "matricule": p.matricule,
                "nom": p.nom,
                "prenom": p.prenom,
                "role": p.role,
                "appareil": {
                    "id": actif.id,
                    "modele": actif.modele,
                    "actif": actif.actif,
                    "attestation": actif.attestation,
                    "enrole_ms": actif.enrole_ms,
                }
                if actif
                else None,
                "code_en_attente": p.code_enrolement,
            }
        )
    return {"personnes": sortie}


@router.post("/admin/personnes/{matricule}/code")
def code(matricule: str, _: Personne = Depends(GESTION), db: Session = Depends(session)) -> dict:
    p = db.scalar(select(Personne).where(Personne.matricule == matricule))
    if p is None or p.role not in ("ETUDIANT", "ENSEIGNANT"):
        raise HTTPException(404, "Personne introuvable")
    return {"matricule": p.matricule, "code": personnes.attribuer_code(db, p)}


@router.get("/admin/salles")
def salles(_: Personne = Depends(LECTURE), db: Session = Depends(session)) -> dict:
    return {
        "salles": [{"id": s.id, "nom": s.nom} for s in db.scalars(select(Salle).order_by(Salle.id))]
    }


@router.post("/admin/salles")
def ajouter_salle(
    id: int = Body(...),
    nom: str = Body(...),
    _: Personne = Depends(TECHNIQUE),
    db: Session = Depends(session),
) -> dict:
    if db.get(Salle, id) is not None:
        raise HTTPException(409, "Cette salle existe déjà")
    db.add(Salle(id=id, nom=nom))
    return {"id": id, "nom": nom}


@router.get("/admin/equipements")
def equipements(_: Personne = Depends(LECTURE), db: Session = Depends(session)) -> dict:
    maintenant = horloge.maintenant_ms()
    return {
        "equipements": [
            _equipement(e, maintenant)
            for e in db.scalars(select(Equipement).order_by(Equipement.nom))
        ]
    }


@router.post("/admin/equipements")
def ajouter_equipement(
    nom: str = Body(...),
    salle: int = Body(...),
    type: str = Body("OBSERVATEUR"),
    cle_publique: str = Body(...),
    _: Personne = Depends(TECHNIQUE),
    db: Session = Depends(session),
) -> dict:
    if db.get(Salle, salle) is None:
        raise HTTPException(404, "Salle inconnue")
    if type not in ("OBSERVATEUR", "PORTE"):
        raise HTTPException(422, "Type attendu : OBSERVATEUR ou PORTE")
    try:
        charger_cle_publique(cle_publique.strip())
    except ValueError as exc:
        raise HTTPException(422, "Clé publique P-256 invalide") from exc
    existant = db.get(Equipement, nom)
    if existant is not None:
        existant.salle_id, existant.type, existant.cle_publique, existant.actif = (
            salle,
            type,
            cle_publique.strip(),
            True,
        )
    else:
        db.add(Equipement(nom=nom, salle_id=salle, type=type, cle_publique=cle_publique.strip()))
    return {"nom": nom, "salle": salle, "type": type}


@router.get("/admin/seances")
def liste_seances(
    etat: str | None = None, _: Personne = Depends(LECTURE), db: Session = Depends(session)
) -> dict:
    q = select(Seance).order_by(Seance.debut_ms.desc()).limit(300)
    if etat:
        q = q.where(Seance.etat == etat)
    return {"seances": [seances.resume(db, s) for s in db.scalars(q)]}


@router.get("/admin/seances/{seance_id}")
def detail_seance(seance_id: str, _: Personne = Depends(LECTURE), db: Session = Depends(session)):
    return seances.detail(db, seances.obtenir(db, seance_id))


@router.get("/admin/seances/{seance_id}/evenements")
def journal(seance_id: str, _: Personne = Depends(LECTURE), db: Session = Depends(session)) -> dict:
    import json

    seances.obtenir(db, seance_id)
    return {
        "evenements": [
            {
                "index": i,
                "type": e.type,
                "horodatage": e.horodatage,
                "auteur": e.auteur,
                "hachage": e.hachage,
                "contenu": json.loads(e.canonique)["contenu"],
            }
            for i, e in enumerate(evenements.de_la_seance(db, seance_id))
        ]
    }


@router.get("/admin/seances/{seance_id}/export.csv")
def export(seance_id: str, _: Personne = Depends(LECTURE), db: Session = Depends(session)):
    d = seances.detail(db, seances.obtenir(db, seance_id))
    tampon = io.StringIO()
    w = csv.writer(tampon)
    w.writerow(
        [
            "seance",
            "cours",
            "salle",
            "matricule",
            "nom",
            "prenom",
            "statut",
            "fenetres_validees",
            "fenetres_total",
            "manuel",
            "corrige",
            "racine",
        ]
    )
    for e in d["etudiants"]:
        w.writerow(
            [
                d["id"],
                d["cours"]["code"],
                d["salle"]["id"],
                e["matricule"],
                e["nom"],
                e["prenom"],
                e["statut"],
                len(e["fenetres_validees"]),
                d["fenetres"]["total"],
                int(e["manuel"]),
                int(e["corrige"]),
                d["racine"] or "",
            ]
        )
    return Response(
        tampon.getvalue(),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="{seance_id}.csv"'},
    )


@router.post("/admin/seances/{seance_id}/corrections")
def correction(
    seance_id: str,
    matricule: str = Body(...),
    statut: str = Body(...),
    motif: str = Body(...),
    auteur: Personne = Depends(GESTION),
    db: Session = Depends(session),
) -> dict:
    seance = seances.corriger(db, seances.obtenir(db, seance_id), auteur, matricule, statut, motif)
    return {"racine": seance.racine, "ancrage": seance.ancrage}


@router.post("/admin/seances/{seance_id}/scellement")
def sceller(seance_id: str, _: Personne = Depends(GESTION), db: Session = Depends(session)) -> dict:
    seance = seances.sceller(db, seances.obtenir(db, seance_id))
    return {"racine": seance.racine, "ancrage": seance.ancrage}


@router.get("/audit")
def audit(_: Personne = Depends(LECTURE), db: Session = Depends(session)) -> dict:
    sortie = []
    for s in db.scalars(
        select(Seance).where(Seance.etat == "SCELLEE").order_by(Seance.debut_ms.desc())
    ):
        recalculee, nb = seances.racine_base(db, s.id)
        try:
            ancree = seances.racine_ancree(s.id)
            registre = (s.ancrage or {}).get("registre")
        except Exception:  # noqa: BLE001  registre injoignable ou altéré
            ancree, registre = None, None
        sortie.append(
            {
                "seance": s.id,
                "cours": s.cours.code,
                "debut_ms": s.debut_ms,
                "nb_evenements": nb,
                "racine_recalculee": recalculee,
                "racine_ancree": ancree,
                "registre": registre,
                "conforme": ancree is not None and ancree == recalculee,
            }
        )
    return {"verifie_ms": horloge.maintenant_ms(), "seances": sortie}
