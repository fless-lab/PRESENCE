"""Cycle de vie d'une séance : démarrage, pauses, clôture, calcul, scellement, corrections."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import UTC, datetime

from fastapi import HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app import horloge
from app.constantes import DELAI_PRESENCE_ENSEIGNANT_MS, DUREE_FENETRE_MS, ECART_DOUBLE_PRESENCE_MS
from app.domaine import merkle, presence
from app.modeles import Appareil, Cours, Evenement, Inscription, Personne, Salle, Seance
from app.registre import racine_courante, registre
from app.securite.requetes import Signataire
from app.services import evenements

CONTROLES = ("DEBUT", "PAUSE", "REPRISE", "CLOTURE")


# --- Lecture -------------------------------------------------------------------------------


def inscrits(db: Session, seance: Seance) -> list[Personne]:
    return list(
        db.scalars(
            select(Personne)
            .join(Inscription, Inscription.personne_id == Personne.id)
            .where(Inscription.cours_id == seance.cours_id)
            .order_by(Personne.nom, Personne.prenom)
        )
    )


def obtenir(db: Session, seance_id: str) -> Seance:
    s = db.get(Seance, seance_id)
    if s is None:
        raise HTTPException(404, "Séance introuvable")
    return s


@dataclass
class Calcul:
    intervalles: list[tuple[int, int | None]]
    total: int
    duree_active_ms: int
    presences: dict[str, presence.Presence] = field(default_factory=dict)
    appareils: dict[str, str | None] = field(default_factory=dict)


def _derniers(evts: list[Evenement], type_: str) -> dict[str, dict]:
    resultat: dict[str, dict] = {}
    for e in evts:
        if e.type == type_ and e.matricule:
            resultat[e.matricule] = json.loads(e.canonique)["contenu"]
    return resultat


def _heure(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000, tz=UTC).strftime("%Hh%M")


def calculer(db: Session, seance: Seance, maintenant: int | None = None) -> Calcul:
    maintenant = maintenant if maintenant is not None else horloge.maintenant_ms()
    evts = evenements.de_la_seance(db, seance.id)
    ivs = presence.intervalles((e.type, e.horodatage) for e in evts if e.type in CONTROLES)
    fermes = presence.fermer(ivs, maintenant)
    duree = presence.duree_active(fermes)
    total = presence.nb_fenetres(duree)
    calc = Calcul(intervalles=ivs, total=total, duree_active_ms=duree)

    personnes = inscrits(db, seance)
    ids = [p.id for p in personnes]
    appareils = (
        list(db.scalars(select(Appareil).where(Appareil.personne_id.in_(ids)))) if ids else []
    )
    proprietaire = {a.id: a.personne_id for a in appareils}

    instants: dict[int, list[int]] = {}
    for e in evts:
        if e.type == "ATTESTATION" and e.appareil in proprietaire:
            instants.setdefault(proprietaire[e.appareil], []).append(e.horodatage)

    # Même appareil vu dans une autre salle à moins de 120 s d'écart
    doubles: dict[int, str] = {}
    if proprietaire and instants:
        fin = seance.fin_ms or maintenant
        ailleurs = db.scalars(
            select(Evenement).where(
                Evenement.type == "ATTESTATION",
                Evenement.appareil.in_(list(proprietaire)),
                Evenement.salle_id != seance.salle_id,
                Evenement.horodatage >= seance.debut_ms - ECART_DOUBLE_PRESENCE_MS,
                Evenement.horodatage <= fin + ECART_DOUBLE_PRESENCE_MS,
            )
        )
        for a in ailleurs:
            pid = proprietaire[a.appareil]
            if pid in doubles:
                continue
            if any(
                abs(a.horodatage - t) <= ECART_DOUBLE_PRESENCE_MS for t in instants.get(pid, [])
            ):
                doubles[pid] = f"vu en salle {a.salle_id} à {_heure(a.horodatage)}"

    corrections = _derniers(evts, "CORRECTION")
    decisions = _derniers(evts, "DECISION")
    manuelles = _derniers(evts, "VALIDATION_MANUELLE")

    for p in personnes:
        v = presence.fenetres_validees(instants.get(p.id, []), fermes, total)
        calc.presences[p.matricule] = presence.statut(
            v,
            total,
            correction=corrections.get(p.matricule, {}).get("statut"),
            decision=decisions.get(p.matricule, {}).get("decision"),
            validation_manuelle=p.matricule in manuelles,
            motif_a_verifier=doubles.get(p.id),
        )
        actifs = [a.id for a in appareils if a.personne_id == p.id and a.actif]
        utilises = [
            a
            for a in proprietaire
            if proprietaire[a] == p.id
            and any(e.appareil == a for e in evts if e.type == "ATTESTATION")
        ]
        calc.appareils[p.matricule] = (utilises or actifs or [None])[0]
    return calc


def compter_presents(calc: Calcul) -> int:
    return sum(1 for p in calc.presences.values() if p.statut in ("PRESENT", "RETARD"))


# --- Actions de l'enseignant ---------------------------------------------------------------


def presence_enseignant(db: Session, personne: Personne) -> tuple[int, int] | None:
    """(salle, depuis_ms) si un appareil de l'enseignant a été attesté il y a moins de 3 min."""
    maintenant = horloge.maintenant_ms()
    ids = [a.id for a in personne.appareils if a.actif]
    if not ids:
        return None
    dernier = db.scalars(
        select(Evenement)
        .where(Evenement.type == "ATTESTATION", Evenement.appareil.in_(ids))
        .order_by(Evenement.horodatage.desc())
        .limit(1)
    ).first()
    if dernier is None or maintenant - dernier.horodatage > DELAI_PRESENCE_ENSEIGNANT_MS:
        return None
    # Début de la présence continue dans cette salle
    depuis = dernier.horodatage
    for e in db.scalars(
        select(Evenement)
        .where(
            Evenement.type == "ATTESTATION",
            Evenement.appareil.in_(ids),
            Evenement.horodatage <= dernier.horodatage,
        )
        .order_by(Evenement.horodatage.desc())
        .limit(200)
    ):
        if e.salle_id != dernier.salle_id or depuis - e.horodatage > DELAI_PRESENCE_ENSEIGNANT_MS:
            break
        depuis = e.horodatage
    return dernier.salle_id, depuis


def seance_active_salle(db: Session, salle_id: int) -> Seance | None:
    return db.scalars(
        select(Seance).where(Seance.salle_id == salle_id, Seance.etat.in_(("ACTIVE", "PAUSE")))
    ).first()


def demarrer(db: Session, s: Signataire, code_cours: str) -> Seance:
    p = s.personne
    assert p is not None
    cours = db.scalar(select(Cours).where(Cours.code == code_cours))
    if cours is None:
        raise HTTPException(404, "Cours introuvable")
    if cours.enseignant_id != p.id:
        raise HTTPException(403, "Ce cours n'est pas le vôtre")
    lieu = presence_enseignant(db, p)
    if lieu is None:
        raise HTTPException(409, "Vous n'êtes détecté dans aucune salle")
    salle_id, _ = lieu
    if seance_active_salle(db, salle_id) is not None:
        raise HTTPException(409, "Une séance est déjà ouverte dans cette salle")
    maintenant = horloge.maintenant_ms()
    jour = datetime.fromtimestamp(maintenant / 1000, tz=UTC).strftime("%Y%m%d")
    n_jour = (
        db.scalar(
            select(func.count())
            .select_from(Seance)
            .where(Seance.salle_id == salle_id, Seance.id.like(f"S-{jour}-{salle_id}-%"))
        )
        or 0
    )
    numero = (db.scalar(select(func.max(Seance.numero))) or 0) + 1
    seance = Seance(
        id=f"S-{jour}-{salle_id}-{n_jour + 1:02d}",
        numero=numero,
        cours_id=cours.id,
        salle_id=salle_id,
        enseignant_id=p.id,
        etat="ACTIVE",
        debut_ms=maintenant,
    )
    db.add(seance)
    db.flush()
    evenements.enregistrer(
        db,
        seance=seance,
        type="DEBUT",
        auteur=f"ens:{p.matricule}",
        horodatage=maintenant,
        contenu={"cours": cours.code, "enseignant": p.matricule, "numero": numero},
        preuves={"requete": s.preuve()},
        salle_id=salle_id,
        matricule=p.matricule,
    )
    return seance


def _controle_enseignant(seance: Seance, s: Signataire) -> None:
    if s.personne is None or seance.enseignant_id != s.personne.id:
        raise HTTPException(403, "Cette séance n'est pas la vôtre")


def changer_etat(db: Session, seance: Seance, s: Signataire, action: str) -> Seance:
    _controle_enseignant(seance, s)
    transitions = {
        "PAUSE": ("ACTIVE", "PAUSE"),
        "REPRISE": ("PAUSE", "ACTIVE"),
        "CLOTURE": (None, "CLOTUREE"),
    }
    depart, arrivee = transitions[action]
    if action == "CLOTURE":
        if seance.etat not in ("ACTIVE", "PAUSE"):
            raise HTTPException(409, "La séance est déjà clôturée")
    elif seance.etat != depart:
        raise HTTPException(409, f"Action impossible dans l'état {seance.etat}")
    maintenant = horloge.maintenant_ms()
    assert s.personne is not None
    evenements.enregistrer(
        db,
        seance=seance,
        type=action,
        auteur=f"ens:{s.personne.matricule}",
        horodatage=maintenant,
        contenu={"enseignant": s.personne.matricule},
        preuves={"requete": s.preuve()},
        salle_id=seance.salle_id,
        matricule=s.personne.matricule,
    )
    seance.etat = arrivee
    if action == "CLOTURE":
        seance.fin_ms = maintenant
        calc = calculer(db, seance, maintenant)
        seance.presents, seance.inscrits = compter_presents(calc), len(calc.presences)
    return seance


def _inscrit(db: Session, seance: Seance, matricule: str) -> Personne:
    p = db.scalar(
        select(Personne)
        .join(Inscription, Inscription.personne_id == Personne.id)
        .where(Inscription.cours_id == seance.cours_id, Personne.matricule == matricule)
    )
    if p is None:
        raise HTTPException(404, "Étudiant non inscrit à ce cours")
    return p


def decider(
    db: Session,
    seance: Seance,
    s: Signataire,
    matricule: str,
    motif: str,
    decision: str | None = None,
) -> None:
    """Validation manuelle (decision None) ou décision sur un cas « à vérifier »."""
    _controle_enseignant(seance, s)
    if seance.etat == "SCELLEE":
        raise HTTPException(409, "Séance scellée : passer par une correction de la scolarité")
    if decision is not None and decision not in ("PRESENT", "ABSENT"):
        raise HTTPException(422, "Décision attendue : PRESENT ou ABSENT")
    p = _inscrit(db, seance, matricule)
    assert s.personne is not None
    contenu = {"matricule": p.matricule, "motif": motif}
    if decision is not None:
        contenu["decision"] = decision
    evenements.enregistrer(
        db,
        seance=seance,
        type="DECISION" if decision else "VALIDATION_MANUELLE",
        auteur=f"ens:{s.personne.matricule}",
        horodatage=horloge.maintenant_ms(),
        contenu=contenu,
        preuves={"requete": s.preuve()},
        salle_id=seance.salle_id,
        matricule=p.matricule,
    )
    if seance.etat == "CLOTUREE":
        calc = calculer(db, seance)
        seance.presents = compter_presents(calc)


# --- Scellement ----------------------------------------------------------------------------


def _feuilles(evts: list[Evenement]) -> list[bytes]:
    feuilles = []
    for e in evts:
        brut = e.canonique.encode("utf-8")
        if merkle.hacher_feuille(brut).hex() != e.hachage:
            raise HTTPException(500, f"Événement {e.id} incohérent avec son hachage")
        feuilles.append(brut)
    return feuilles


def racine_base(db: Session, seance_id: str) -> tuple[str, int]:
    evts = evenements.de_la_seance(db, seance_id)
    feuilles = [e.canonique.encode("utf-8") for e in evts]
    return merkle.racine(feuilles).hex(), len(feuilles)


def sceller(db: Session, seance: Seance) -> Seance:
    if seance.etat == "SCELLEE":
        raise HTTPException(409, "Séance déjà scellée")
    if seance.etat != "CLOTUREE":
        raise HTTPException(409, "Clôturer la séance avant de la sceller")
    calc = calculer(db, seance)
    restants = [m for m, p in calc.presences.items() if p.statut == "A_VERIFIER"]
    if restants:
        raise HTTPException(409, f"{len(restants)} cas à vérifier sans décision")
    evts = evenements.de_la_seance(db, seance.id)
    feuilles = _feuilles(evts)
    racine = merkle.racine(feuilles).hex()
    observateurs = sorted({e.auteur[4:] for e in evts if e.type == "ATTESTATION"})
    r = registre()
    transaction = r.ancrer_seance(
        seance.id,
        seance.salle_id,
        seance.debut_ms,
        seance.fin_ms or seance.debut_ms,
        racine,
        len(feuilles),
        observateurs,
    )
    seance.racine = racine
    seance.nb_evenements = len(feuilles)
    seance.ancrage = {"registre": r.nom, "transaction": transaction, "corrections": []}
    seance.etat = "SCELLEE"
    seance.presents, seance.inscrits = compter_presents(calc), len(calc.presences)
    return seance


def corriger(
    db: Session, seance: Seance, auteur: Personne, matricule: str, statut: str, motif: str
) -> Seance:
    from app.constantes import STATUTS

    if statut not in STATUTS:
        raise HTTPException(422, "Statut inconnu")
    p = _inscrit(db, seance, matricule)
    evenements.enregistrer(
        db,
        seance=seance,
        type="CORRECTION",
        auteur=f"sco:{auteur.matricule}",
        horodatage=horloge.maintenant_ms(),
        contenu={"matricule": p.matricule, "statut": statut, "motif": motif},
        preuves={"par": auteur.matricule},
        salle_id=seance.salle_id,
        matricule=p.matricule,
    )
    if seance.etat == "SCELLEE":
        evts = evenements.de_la_seance(db, seance.id)
        nouvelle = merkle.racine(_feuilles(evts)).hex()
        r = registre()
        ancien = seance.racine or ""
        transaction = r.ancrer_correction(seance.id, nouvelle, ancien, motif)
        ancrage = dict(seance.ancrage or {})
        ancrage["corrections"] = [
            *ancrage.get("corrections", []),
            {"racine": nouvelle, "transaction": transaction},
        ]
        ancrage["transaction"] = transaction
        seance.ancrage = ancrage
        seance.racine = nouvelle
        seance.nb_evenements = len(evts)
    calc = calculer(db, seance)
    seance.presents = compter_presents(calc)
    return seance


def lire_ancrage(seance_id: str) -> dict | None:
    return registre().lire(seance_id)


def racine_ancree(seance_id: str) -> str | None:
    a = lire_ancrage(seance_id)
    return racine_courante(a) if a else None


# --- Présentation --------------------------------------------------------------------------


def resume(db: Session, seance: Seance, calc: Calcul | None = None) -> dict:
    if seance.etat in ("ACTIVE", "PAUSE") or seance.presents is None:
        calc = calc or calculer(db, seance)
        presents, nb = compter_presents(calc), len(calc.presences)
        duree = calc.duree_active_ms
    else:
        presents, nb = seance.presents, seance.inscrits or 0
        calc = calc or calculer(db, seance)
        duree = calc.duree_active_ms
    salle = db.get(Salle, seance.salle_id)
    return {
        "id": seance.id,
        "numero": seance.numero,
        "cours": {
            "code": seance.cours.code,
            "intitule": seance.cours.intitule,
            "groupe": seance.cours.groupe,
        },
        "salle": {"id": seance.salle_id, "nom": salle.nom if salle else str(seance.salle_id)},
        "enseignant": {
            "matricule": seance.enseignant.matricule,
            "nom": seance.enseignant.nom,
            "prenom": seance.enseignant.prenom,
        },
        "etat": seance.etat,
        "debut_ms": seance.debut_ms,
        "fin_ms": seance.fin_ms,
        "duree_active_ms": duree,
        "presents": presents,
        "inscrits": nb,
        "racine": seance.racine,
    }


def detail(db: Session, seance: Seance) -> dict:
    maintenant = horloge.maintenant_ms()
    calc = calculer(db, seance, maintenant)
    base = resume(db, seance, calc)
    personnes = {p.matricule: p for p in inscrits(db, seance)}
    ancrage = seance.ancrage or {}
    base.update(
        {
            "maintenant_ms": maintenant,
            "intervalles": [{"debut_ms": d, "fin_ms": f} for d, f in calc.intervalles],
            "fenetres": {"total": calc.total, "duree_ms": DUREE_FENETRE_MS},
            "etudiants": [
                {
                    "matricule": m,
                    "nom": personnes[m].nom,
                    "prenom": personnes[m].prenom,
                    "appareil": calc.appareils.get(m),
                    "statut": p.statut,
                    "fenetres_validees": p.validees,
                    "manuel": p.manuel,
                    "corrige": p.corrige,
                    "motif_a_verifier": p.motif_a_verifier,
                }
                for m, p in calc.presences.items()
            ],
            "integrite": {
                "racine": seance.racine,
                "nb_evenements": seance.nb_evenements
                if seance.nb_evenements is not None
                else len(evenements.de_la_seance(db, seance.id)),
                "registre": ancrage.get("registre"),
                "transaction": ancrage.get("transaction"),
                "corrections": len(ancrage.get("corrections", [])),
            },
        }
    )
    return base
