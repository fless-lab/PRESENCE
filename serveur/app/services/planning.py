"""Planning : lecture du site de l'école, modifications locales, journal des changements.

Le planning vient d'un import (site d'emploi du temps de l'école) puis se modifie ici :
déplacer, annuler, ajouter. Chaque modification est journalisée et notifiée aux étudiants
et enseignants du cours. Un import ne réécrit jamais un créneau modifié ici : si l'école
a changé le même créneau entre-temps, l'écart est seulement signalé dans le rapport.
"""

from __future__ import annotations

import csv
import io
from datetime import datetime
from zoneinfo import ZoneInfo

from fastapi import HTTPException
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app import horloge
from app.config import config
from app.modeles import Changement, Cours, Creneau, Inscription, Personne, Salle

ETATS = ("PREVU", "ANNULE")
DUREE_MAX_MS = 12 * 3_600_000
JOUR_MS = 86_400_000
COLONNES_REQUISES = ("ref", "cours_code", "salle", "debut", "fin")


# --- lecture des valeurs -----------------------------------------------------------------------


def instant(valeur) -> int:
    """Date en millisecondes. Un texte sans fuseau est lu dans le fuseau de l'école."""
    if isinstance(valeur, bool):
        raise ValueError("date invalide")
    if isinstance(valeur, int):
        return valeur
    texte = str(valeur).strip()
    if texte.isdigit():
        return int(texte)
    try:
        d = datetime.fromisoformat(texte.replace(" ", "T"))
    except ValueError as exc:
        raise ValueError(f"date illisible : « {texte} » (attendu 2026-11-03T09:00)") from exc
    if d.tzinfo is None:
        d = d.replace(tzinfo=ZoneInfo(config.fuseau))
    return int(d.timestamp() * 1000)


def _cours(db: Session, code: str) -> Cours:
    c = db.scalar(select(Cours).where(Cours.code == code))
    if c is None:
        raise ValueError(f"cours inconnu : {code}")
    return c


def _salle(db: Session, valeur) -> Salle:
    try:
        s = db.get(Salle, int(str(valeur).strip()))
    except ValueError:
        s = None
    if s is None:
        raise ValueError(f"salle inconnue : {valeur}")
    return s


def _enseignant(db: Session, matricule: str | None) -> Personne | None:
    if not matricule:
        return None
    p = db.scalar(select(Personne).where(Personne.matricule == matricule))
    if p is None or p.role != "ENSEIGNANT":
        raise ValueError(f"enseignant inconnu : {matricule}")
    return p


def _verifier_horaire(debut: int, fin: int) -> None:
    if fin <= debut:
        raise ValueError("la fin doit suivre le début")
    if fin - debut > DUREE_MAX_MS:
        raise ValueError("un créneau dure 12 heures au plus")


def _etat(c: Creneau) -> dict:
    """Valeurs d'un créneau, sous la forme comparée et enregistrée dans le journal."""
    ens = c.enseignant or (c.cours.enseignant if c.cours else None)
    return {
        "debut_ms": c.debut_ms,
        "fin_ms": c.fin_ms,
        "salle": c.salle_id,
        "enseignant": ens.matricule if ens else None,
        "etat": c.etat,
    }


def _appliquer(
    db: Session,
    c: Creneau,
    nouveau: dict,
    *,
    motif: str,
    auteur: str,
    origine: str,
    notifie: bool = True,
) -> Changement | None:
    """Passe le créneau aux valeurs `nouveau` et journalise ce qui a changé."""
    avant = _etat(c)
    if avant == nouveau:
        return None
    c.debut_ms, c.fin_ms, c.salle_id, c.etat = (
        nouveau["debut_ms"],
        nouveau["fin_ms"],
        nouveau["salle"],
        nouveau["etat"],
    )
    c.enseignant_id = None
    if nouveau["enseignant"]:
        ens = db.scalar(select(Personne).where(Personne.matricule == nouveau["enseignant"]))
        c.enseignant_id = ens.id if ens else None
    c.maj_ms = horloge.maintenant_ms()
    if avant["etat"] == "PREVU" and nouveau["etat"] == "ANNULE":
        type_ = "ANNULE"
    elif avant["etat"] == "ANNULE" and nouveau["etat"] == "PREVU":
        type_ = "RETABLI"
    else:
        type_ = "DEPLACE"
    ch = Changement(
        creneau_id=c.id,
        cours_id=c.cours_id,
        type=type_,
        avant=avant,
        apres=nouveau,
        motif=motif,
        auteur=auteur,
        origine=origine,
        notifie=notifie,
        horodatage=c.maj_ms,
    )
    db.add(ch)
    return ch


def _journaliser_creation(
    db: Session, c: Creneau, *, motif: str, auteur: str, origine: str, notifie: bool
) -> None:
    db.add(
        Changement(
            creneau_id=c.id,
            cours_id=c.cours_id,
            type="CREE",
            avant=None,
            apres=_etat(c),
            motif=motif,
            auteur=auteur,
            origine=origine,
            notifie=notifie,
            horodatage=c.maj_ms,
        )
    )


# --- import depuis le site de l'école ------------------------------------------------------------


def _valeurs_ligne(db: Session, ligne: dict) -> dict:
    for col in COLONNES_REQUISES:
        if not ligne.get(col):
            raise ValueError(f"colonne « {col} » vide")
    cours = _cours(db, ligne["cours_code"])
    salle = _salle(db, ligne["salle"])
    ens = _enseignant(db, ligne.get("enseignant"))
    debut, fin = instant(ligne["debut"]), instant(ligne["fin"])
    _verifier_horaire(debut, fin)
    etat = (ligne.get("etat") or "PREVU").upper()
    if etat not in ETATS:
        raise ValueError(f"état inconnu : {etat}")
    titulaire = ens or cours.enseignant
    return {
        "debut_ms": debut,
        "fin_ms": fin,
        "salle": salle.id,
        "enseignant": titulaire.matricule if titulaire else None,
        "etat": etat,
        "_cours": cours.id,
        "_ens": ens.id if ens else None,
    }


def importer_csv(db: Session, contenu: str, auteur: str) -> dict:
    lecteur = csv.DictReader(io.StringIO(contenu.lstrip("﻿")))
    manquantes = [c for c in COLONNES_REQUISES if c not in (lecteur.fieldnames or [])]
    if manquantes:
        raise HTTPException(422, f"Colonnes manquantes : {', '.join(manquantes)}")
    premiere_fois = (db.scalar(select(func.count(Creneau.id))) or 0) == 0
    rapport: dict = {
        "crees": 0,
        "modifies": 0,
        "inchanges": 0,
        "ecarts": [],
        "non_retrouves": [],
        "erreurs": [],
    }
    vus: set[str] = set()
    for n, brut in enumerate(lecteur, start=2):
        ligne = {k: (v or "").strip() for k, v in brut.items() if k}
        try:
            valeurs = _valeurs_ligne(db, ligne)
        except ValueError as exc:
            rapport["erreurs"].append({"ligne": n, "message": str(exc)})
            continue
        ref = ligne["ref"]
        cours_id, ens_id = valeurs.pop("_cours"), valeurs.pop("_ens")
        vus.add(ref)
        c = db.scalar(select(Creneau).where(Creneau.ref == ref))
        if c is None:
            c = Creneau(
                ref=ref,
                cours_id=cours_id,
                salle_id=valeurs["salle"],
                enseignant_id=ens_id,
                debut_ms=valeurs["debut_ms"],
                fin_ms=valeurs["fin_ms"],
                etat=valeurs["etat"],
                origine="IMPORT",
                src=valeurs,
                maj_ms=horloge.maintenant_ms(),
            )
            db.add(c)
            db.flush()
            _journaliser_creation(
                db, c, motif="", auteur=auteur, origine="IMPORT", notifie=not premiere_fois
            )
            rapport["crees"] += 1
            continue
        if c.modifie_localement:
            if c.src is not None and c.src != valeurs and _etat(c) != valeurs:
                rapport["ecarts"].append({"ref": ref, "local": _etat(c), "ecole": valeurs})
            c.src = valeurs
            rapport["inchanges"] += 1
            continue
        c.src = valeurs
        if _appliquer(db, c, valeurs, motif="", auteur=auteur, origine="IMPORT"):
            rapport["modifies"] += 1
        else:
            rapport["inchanges"] += 1
    if vus:
        for c in db.scalars(
            select(Creneau).where(
                Creneau.origine == "IMPORT",
                Creneau.ref.is_not(None),
                Creneau.debut_ms > horloge.maintenant_ms(),
            )
        ):
            if c.ref not in vus:
                rapport["non_retrouves"].append(c.ref)
    db.flush()
    return rapport


# --- modifications locales -----------------------------------------------------------------------


def obtenir(db: Session, creneau_id: int) -> Creneau:
    c = db.get(Creneau, creneau_id)
    if c is None:
        raise HTTPException(404, "Créneau introuvable")
    return c


def creer_local(
    db: Session,
    auteur: Personne,
    *,
    cours_code: str,
    salle,
    debut,
    fin,
    enseignant: str | None = None,
    motif: str = "",
) -> Creneau:
    try:
        cours, sa, ens = _cours(db, cours_code), _salle(db, salle), _enseignant(db, enseignant)
        d, f = instant(debut), instant(fin)
        _verifier_horaire(d, f)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    c = Creneau(
        cours_id=cours.id,
        salle_id=sa.id,
        enseignant_id=ens.id if ens else None,
        debut_ms=d,
        fin_ms=f,
        etat="PREVU",
        origine="LOCAL",
        modifie_localement=True,
        maj_ms=horloge.maintenant_ms(),
    )
    db.add(c)
    db.flush()
    _journaliser_creation(
        db, c, motif=motif, auteur=auteur.matricule, origine="LOCAL", notifie=True
    )
    return c


def deplacer(
    db: Session,
    auteur: Personne,
    creneau_id: int,
    *,
    debut=None,
    fin=None,
    salle=None,
    enseignant: str | None = None,
    motif: str,
) -> Creneau:
    if not motif.strip():
        raise HTTPException(422, "Un motif est demandé")
    c = obtenir(db, creneau_id)
    if c.etat == "ANNULE":
        raise HTTPException(409, "Créneau annulé : le rétablir d'abord")
    nouveau = _etat(c)
    try:
        if debut is not None:
            nouveau["debut_ms"] = instant(debut)
        if fin is not None:
            nouveau["fin_ms"] = instant(fin)
        if debut is not None and fin is None:
            # Déplacer sans préciser la fin conserve la durée.
            nouveau["fin_ms"] = nouveau["debut_ms"] + (c.fin_ms - c.debut_ms)
        if salle is not None:
            nouveau["salle"] = _salle(db, salle).id
        if enseignant is not None:
            ens = _enseignant(db, enseignant)
            nouveau["enseignant"] = ens.matricule if ens else None
        _verifier_horaire(nouveau["debut_ms"], nouveau["fin_ms"])
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    if nouveau == _etat(c):
        raise HTTPException(422, "Rien ne change")
    _appliquer(db, c, nouveau, motif=motif.strip(), auteur=auteur.matricule, origine="LOCAL")
    c.modifie_localement = True
    return c


def _changer_etat(db: Session, auteur: Personne, creneau_id: int, etat: str, motif: str) -> Creneau:
    c = obtenir(db, creneau_id)
    if c.etat == etat:
        raise HTTPException(409, "Le créneau est déjà dans cet état")
    nouveau = dict(_etat(c), etat=etat)
    _appliquer(db, c, nouveau, motif=motif.strip(), auteur=auteur.matricule, origine="LOCAL")
    c.modifie_localement = True
    return c


def annuler(db: Session, auteur: Personne, creneau_id: int, motif: str) -> Creneau:
    if not motif.strip():
        raise HTTPException(422, "Un motif est demandé")
    return _changer_etat(db, auteur, creneau_id, "ANNULE", motif)


def retablir(db: Session, auteur: Personne, creneau_id: int, motif: str = "") -> Creneau:
    return _changer_etat(db, auteur, creneau_id, "PREVU", motif)


# --- lecture -------------------------------------------------------------------------------------


def vue(c: Creneau) -> dict:
    ens = c.enseignant or c.cours.enseignant
    return {
        "id": c.id,
        "ref": c.ref,
        "cours": {
            "code": c.cours.code,
            "intitule": c.cours.intitule,
            "groupe": c.cours.groupe,
            "filiere": c.cours.filiere,
            "niveau": c.cours.niveau,
        },
        "salle": {"id": c.salle_id, "nom": c.salle.nom if c.salle else str(c.salle_id)},
        "enseignant": {"matricule": ens.matricule, "nom": ens.nom, "prenom": ens.prenom}
        if ens
        else None,
        "debut_ms": c.debut_ms,
        "fin_ms": c.fin_ms,
        "etat": c.etat,
        "origine": c.origine,
        "maj_ms": c.maj_ms,
    }


def lister(
    db: Session,
    *,
    filiere: str | None = None,
    niveau: str | None = None,
    du_ms: int | None = None,
    au_ms: int | None = None,
) -> list[dict]:
    q = select(Creneau).join(Cours, Cours.id == Creneau.cours_id).order_by(Creneau.debut_ms)
    if filiere:
        q = q.where(Cours.filiere == filiere)
    if niveau:
        q = q.where(Cours.niveau == niveau.upper())
    if du_ms is not None:
        q = q.where(Creneau.fin_ms > du_ms)
    if au_ms is not None:
        q = q.where(Creneau.debut_ms < au_ms)
    return [vue(c) for c in db.scalars(q.limit(1000))]


def _cours_de(db: Session, p: Personne) -> set[int]:
    if p.role == "ENSEIGNANT":
        ids = set(db.scalars(select(Cours.id).where(Cours.enseignant_id == p.id)))
        ids |= set(db.scalars(select(Creneau.cours_id).where(Creneau.enseignant_id == p.id)))
        return ids
    return set(db.scalars(select(Inscription.cours_id).where(Inscription.personne_id == p.id)))


def pour_personne(
    db: Session, p: Personne, du_ms: int | None = None, au_ms: int | None = None
) -> list[dict]:
    maintenant = horloge.maintenant_ms()
    du = du_ms if du_ms is not None else maintenant - 6 * 3_600_000
    au = au_ms if au_ms is not None else maintenant + 14 * JOUR_MS
    if p.role == "ENSEIGNANT":
        cond = or_(
            Creneau.enseignant_id == p.id,
            (Creneau.enseignant_id.is_(None)) & (Cours.enseignant_id == p.id),
        )
    else:
        cond = Creneau.cours_id.in_(_cours_de(db, p) or {-1})
    q = (
        select(Creneau)
        .join(Cours, Cours.id == Creneau.cours_id)
        .where(cond, Creneau.fin_ms > du, Creneau.debut_ms < au)
        .order_by(Creneau.debut_ms)
        .limit(500)
    )
    return [vue(c) for c in db.scalars(q)]


def _vue_changement(ch: Changement) -> dict:
    return {
        "id": ch.id,
        "creneau": ch.creneau_id,
        "type": ch.type,
        "cours": {"code": ch.cours.code, "intitule": ch.cours.intitule},
        "avant": ch.avant,
        "apres": ch.apres,
        "motif": ch.motif,
        "auteur": ch.auteur,
        "origine": ch.origine,
        "horodatage": ch.horodatage,
    }


def changements_pour(db: Session, p: Personne, apres_id: int = 0, limite: int = 100) -> list[dict]:
    """Changements notifiables des cours de la personne, du plus ancien au plus récent."""
    cours = _cours_de(db, p)
    if not cours:
        return []
    q = (
        select(Changement)
        .where(
            Changement.id > apres_id, Changement.notifie.is_(True), Changement.cours_id.in_(cours)
        )
        .order_by(Changement.id)
        .limit(limite)
    )
    return [_vue_changement(ch) for ch in db.scalars(q)]


def journal(
    db: Session, *, filiere: str | None = None, niveau: str | None = None, limite: int = 200
) -> list[dict]:
    q = select(Changement).join(Cours, Cours.id == Changement.cours_id)
    if filiere:
        q = q.where(Cours.filiere == filiere)
    if niveau:
        q = q.where(Cours.niveau == niveau.upper())
    q = q.order_by(Changement.id.desc()).limit(limite)
    return [_vue_changement(ch) for ch in db.scalars(q)]
