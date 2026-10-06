"""Comptes du personnel : mots de passe et jetons de session."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import secrets
from pathlib import Path

from fastapi import Depends, Header, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import horloge
from app.config import config
from app.db import session
from app.modeles import Personne

DUREE_JETON_MS = 12 * 3600 * 1000


def hacher_mot_de_passe(mot_de_passe: str) -> str:
    sel = secrets.token_bytes(16)
    h = hashlib.scrypt(mot_de_passe.encode(), salt=sel, n=2**14, r=8, p=1)
    return f"scrypt${sel.hex()}${h.hex()}"


def verifier_mot_de_passe(mot_de_passe: str, stocke: str | None) -> bool:
    if not stocke or not stocke.startswith("scrypt$"):
        return False
    _, sel, attendu = stocke.split("$")
    h = hashlib.scrypt(mot_de_passe.encode(), salt=bytes.fromhex(sel), n=2**14, r=8, p=1)
    return hmac.compare_digest(h.hex(), attendu)


_secret: bytes | None = None


def _cle_secrete() -> bytes:
    global _secret
    if _secret is None:
        chemin = Path(config.donnees) / "secret_jetons"
        chemin.parent.mkdir(parents=True, exist_ok=True)
        if not chemin.exists():
            chemin.write_bytes(secrets.token_bytes(32))
            chemin.chmod(0o600)
        _secret = chemin.read_bytes()
    return _secret


def _b64u(d: bytes) -> str:
    return base64.urlsafe_b64encode(d).rstrip(b"=").decode()


def _de_b64u(t: str) -> bytes:
    return base64.urlsafe_b64decode(t + "=" * (-len(t) % 4))


def emettre_jeton(personne: Personne) -> str:
    charge = json.dumps(
        {
            "m": personne.matricule,
            "r": personne.role,
            "e": horloge.maintenant_ms() + DUREE_JETON_MS,
        },
        separators=(",", ":"),
    ).encode()
    sig = hmac.new(_cle_secrete(), charge, hashlib.sha256).digest()
    return f"{_b64u(charge)}.{_b64u(sig)}"


def lire_jeton(jeton: str) -> dict | None:
    try:
        charge_t, sig_t = jeton.split(".")
        charge = _de_b64u(charge_t)
        attendu = hmac.new(_cle_secrete(), charge, hashlib.sha256).digest()
        if not hmac.compare_digest(attendu, _de_b64u(sig_t)):
            return None
        donnees = json.loads(charge)
        if donnees["e"] < horloge.maintenant_ms():
            return None
        return donnees
    except (ValueError, KeyError):
        return None


def personnel(*roles: str):
    """Dépendance : jeton valide d'un membre du personnel ayant l'un des rôles."""

    def dependance(
        authorization: str = Header(default=""), db: Session = Depends(session)
    ) -> Personne:
        if not authorization.startswith("Bearer "):
            raise HTTPException(401, "Connexion requise")
        donnees = lire_jeton(authorization[7:])
        if donnees is None:
            raise HTTPException(401, "Session expirée")
        p = db.scalar(select(Personne).where(Personne.matricule == donnees["m"]))
        if p is None or (roles and p.role not in roles):
            raise HTTPException(403, "Accès refusé")
        return p

    return dependance
