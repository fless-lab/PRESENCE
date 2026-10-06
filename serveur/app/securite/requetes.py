"""Vérification des requêtes signées par les téléphones et les équipements."""

from __future__ import annotations

import threading
from dataclasses import dataclass

from fastapi import Depends, HTTPException, Request
from sqlalchemy.orm import Session

from app import horloge
from app.constantes import ETIQUETTE_REQUETE, TOLERANCE_HORLOGE_MS
from app.db import session
from app.modeles import Appareil, Equipement, Personne
from app.securite.crypto import de_b64, sha256_hex, verifier


def chaine_signee(methode: str, chemin: str, horodatage: int, corps: bytes) -> bytes:
    return "\n".join(
        [ETIQUETTE_REQUETE, methode.upper(), chemin, str(horodatage), sha256_hex(corps)]
    ).encode("utf-8")


class _AntiRejeu:
    """Mémorise les signatures vues pendant 5 minutes."""

    def __init__(self, duree_ms: int = 300_000):
        self.duree_ms = duree_ms
        self._vues: dict[str, int] = {}
        self._verrou = threading.Lock()

    def deja_vue(self, signature: str, maintenant: int) -> bool:
        with self._verrou:
            for s, t in list(self._vues.items()):
                if maintenant - t > self.duree_ms:
                    del self._vues[s]
            if signature in self._vues:
                return True
            self._vues[signature] = maintenant
            return False


anti_rejeu = _AntiRejeu()


@dataclass
class Signataire:
    type: str  # "app" ou "equ"
    appareil: Appareil | None = None
    personne: Personne | None = None
    equipement: Equipement | None = None
    signature: str = ""
    horodatage: int = 0
    corps: bytes = b""
    chemin: str = ""
    methode: str = ""

    def preuve(self) -> dict:
        """Requête signée, conservée dans l'événement pour pouvoir la revérifier."""
        return {
            "methode": self.methode,
            "chemin": self.chemin,
            "horodatage": self.horodatage,
            "corps": self.corps.decode("utf-8"),
            "sig": self.signature,
        }


async def signataire(request: Request, db: Session = Depends(session)) -> Signataire:
    ident = request.headers.get("x-presence-id", "")
    horo = request.headers.get("x-presence-horodatage", "")
    sig = request.headers.get("x-presence-signature", "")
    if not ident or not horo or not sig:
        raise HTTPException(401, "Requête non signée")
    try:
        horodatage = int(horo)
        signature = de_b64(sig)
    except ValueError as exc:
        raise HTTPException(401, "En-têtes de signature invalides") from exc
    maintenant = horloge.maintenant_ms()
    if abs(maintenant - horodatage) > TOLERANCE_HORLOGE_MS:
        raise HTTPException(401, "Horloge désynchronisée")

    corps = await request.body()
    chemin = request.url.path + (f"?{request.url.query}" if request.url.query else "")
    message = chaine_signee(request.method, chemin, horodatage, corps)

    s = Signataire(
        type="",
        signature=sig,
        horodatage=horodatage,
        corps=corps,
        chemin=chemin,
        methode=request.method,
    )
    if ident.startswith("app:"):
        appareil = db.get(Appareil, ident[4:])
        if appareil is None or not appareil.actif:
            raise HTTPException(401, "Appareil inconnu ou révoqué")
        cle = appareil.cle_publique
        s.type, s.appareil, s.personne = "app", appareil, appareil.personne
    elif ident.startswith("equ:"):
        equipement = db.get(Equipement, ident[4:])
        if equipement is None or not equipement.actif:
            raise HTTPException(401, "Équipement inconnu ou désactivé")
        cle = equipement.cle_publique
        s.type, s.equipement = "equ", equipement
    else:
        raise HTTPException(401, "Identifiant de signataire invalide")

    if not verifier(cle, message, signature):
        raise HTTPException(401, "Signature invalide")
    if anti_rejeu.deja_vue(sig, maintenant):
        raise HTTPException(401, "Requête déjà reçue")
    if s.equipement is not None:
        s.equipement.vu_ms = maintenant
    return s


async def telephone(s: Signataire = Depends(signataire)) -> Signataire:
    if s.type != "app":
        raise HTTPException(403, "Route réservée aux téléphones")
    return s


async def enseignant(s: Signataire = Depends(telephone)) -> Signataire:
    if s.personne is None or s.personne.role != "ENSEIGNANT":
        raise HTTPException(403, "Route réservée aux enseignants")
    return s


async def equipement(s: Signataire = Depends(signataire)) -> Signataire:
    if s.type != "equ":
        raise HTTPException(403, "Route réservée aux équipements")
    return s
