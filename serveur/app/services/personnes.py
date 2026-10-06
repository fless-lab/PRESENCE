"""Import des cours et des inscrits, codes et enrôlement des téléphones."""

from __future__ import annotations

import csv
import io
import secrets

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import horloge
from app.config import config
from app.constantes import ROLES
from app.modeles import Appareil, Cours, Defi, Inscription, Personne
from app.securite import attestation_android
from app.securite.crypto import charger_cle_publique, de_b64, id_appareil

ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
COLONNES = ("matricule", "nom", "prenom", "role", "cours_code", "cours_intitule", "groupe")
DUREE_DEFI_MS = 5 * 60 * 1000


def nouveau_code() -> str:
    c = "".join(secrets.choice(ALPHABET) for _ in range(6))
    return f"{c[:3]}-{c[3:]}"


def attribuer_code(db: Session, p: Personne) -> str:
    """Nouveau code d'enrôlement ; révoque l'appareil actif."""
    maintenant = horloge.maintenant_ms()
    for a in p.appareils:
        if a.actif:
            a.actif, a.revoque_ms = False, maintenant
    p.code_enrolement = nouveau_code()
    return p.code_enrolement


def importer_csv(db: Session, contenu: str) -> dict:
    lecteur = csv.DictReader(io.StringIO(contenu.lstrip("﻿")))
    manquantes = [
        c for c in ("matricule", "nom", "prenom", "role") if c not in (lecteur.fieldnames or [])
    ]
    if manquantes:
        raise HTTPException(422, f"Colonnes manquantes : {', '.join(manquantes)}")
    rapport = {
        "personnes_creees": 0,
        "personnes_mises_a_jour": 0,
        "cours_crees": 0,
        "inscriptions": 0,
        "codes": [],
        "erreurs": [],
    }
    vus: set[str] = set()
    for n, ligne in enumerate(lecteur, start=2):
        ligne = {k: (v or "").strip() for k, v in ligne.items() if k}
        matricule, role = ligne.get("matricule", ""), ligne.get("role", "").upper()
        if not matricule or role not in ROLES:
            rapport["erreurs"].append({"ligne": n, "message": "matricule ou rôle invalide"})
            continue
        p = db.scalar(select(Personne).where(Personne.matricule == matricule))
        if p is None:
            p = Personne(
                matricule=matricule,
                nom=ligne.get("nom", ""),
                prenom=ligne.get("prenom", ""),
                role=role,
            )
            db.add(p)
            db.flush()
            rapport["personnes_creees"] += 1
        elif matricule not in vus:
            p.nom, p.prenom, p.role = ligne.get("nom", p.nom), ligne.get("prenom", p.prenom), role
            rapport["personnes_mises_a_jour"] += 1
        if matricule not in vus and role in ("ETUDIANT", "ENSEIGNANT"):
            a_appareil = any(a.actif for a in p.appareils)
            if not a_appareil and not p.code_enrolement:
                p.code_enrolement = nouveau_code()
                rapport["codes"].append(
                    {
                        "matricule": p.matricule,
                        "nom": p.nom,
                        "prenom": p.prenom,
                        "code": p.code_enrolement,
                    }
                )
        vus.add(matricule)

        code_cours = ligne.get("cours_code", "")
        if not code_cours:
            continue
        cours = db.scalar(select(Cours).where(Cours.code == code_cours))
        if cours is None:
            cours = Cours(
                code=code_cours,
                intitule=ligne.get("cours_intitule") or code_cours,
                groupe=ligne.get("groupe", ""),
            )
            db.add(cours)
            db.flush()
            rapport["cours_crees"] += 1
        if role == "ENSEIGNANT":
            cours.enseignant_id = p.id
        elif role == "ETUDIANT" and db.get(Inscription, (cours.id, p.id)) is None:
            db.add(Inscription(cours_id=cours.id, personne_id=p.id))
            rapport["inscriptions"] += 1
    db.flush()
    return rapport


def emettre_defi(db: Session, matricule: str) -> dict:
    p = db.scalar(select(Personne).where(Personne.matricule == matricule))
    if p is None or p.role not in ("ETUDIANT", "ENSEIGNANT"):
        raise HTTPException(404, "Matricule inconnu")
    defi = secrets.token_bytes(32)
    from app.securite.crypto import b64

    texte = b64(defi)
    expire = horloge.maintenant_ms() + DUREE_DEFI_MS
    db.add(Defi(defi=texte, matricule=matricule, expire_ms=expire))
    return {"defi": texte, "expire_ms": expire}


def enroler(db: Session, demande: dict) -> dict:
    matricule = demande.get("matricule", "")
    p = db.scalar(select(Personne).where(Personne.matricule == matricule))
    if p is None or not p.code_enrolement:
        raise HTTPException(403, "Aucun enrôlement en attente pour ce matricule")
    if (demande.get("code") or "").strip().upper() != p.code_enrolement:
        raise HTTPException(403, "Code d'enrôlement incorrect")
    defi = db.get(Defi, demande.get("defi", ""))
    if defi is None or defi.matricule != matricule or defi.expire_ms < horloge.maintenant_ms():
        raise HTTPException(403, "Défi expiré, recommencer")
    try:
        cle_b64 = demande["cle_publique"]
        charger_cle_publique(cle_b64)
        spki = de_b64(cle_b64)
        chaine = [de_b64(c) for c in demande.get("chaine_attestation") or []]
    except (KeyError, ValueError) as exc:
        raise HTTPException(422, "Clé publique invalide") from exc

    resultat = attestation_android.verifier_chaine(chaine, spki, de_b64(defi.defi), config.donnees)
    if resultat.statut == "REFUSEE" or (
        config.attestation == "stricte" and resultat.statut != "VERIFIEE"
    ):
        raise HTTPException(403, "Attestation de clé refusée : " + "; ".join(resultat.raisons))

    ident = id_appareil(spki)
    if db.get(Appareil, ident) is not None:
        raise HTTPException(409, "Cette clé est déjà enregistrée")
    maintenant = horloge.maintenant_ms()
    for a in p.appareils:
        if a.actif:
            a.actif, a.revoque_ms = False, maintenant
    db.add(
        Appareil(
            id=ident,
            personne_id=p.id,
            cle_publique=cle_b64,
            modele=(demande.get("modele") or "")[:120] or None,
            attestation=resultat.statut,
            attestation_detail=resultat.detail(),
            deverrouillage_recent=bool(demande.get("deverrouillage_recent")),
            actif=True,
            enrole_ms=maintenant,
        )
    )
    p.code_enrolement = None
    db.delete(defi)
    db.flush()
    return {
        "id_appareil": ident,
        "personne": {"matricule": p.matricule, "nom": p.nom, "prenom": p.prenom, "role": p.role},
        "attestation": resultat.statut,
    }
