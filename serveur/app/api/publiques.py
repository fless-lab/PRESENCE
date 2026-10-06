from fastapi import APIRouter, HTTPException

from app import __version__, horloge
from app.constantes import VERSION
from app.etat import cle_serveur
from app.services import seances

router = APIRouter(tags=["public"])


@router.get("/sante")
def sante() -> dict:
    return {"statut": "ok", "version": __version__, "protocole": VERSION}


@router.get("/heure")
def heure() -> dict:
    return {"ms": horloge.maintenant_ms()}


@router.get("/cle-serveur")
def cle() -> dict:
    return {"cle_publique": cle_serveur().publique_b64}


@router.get("/ancrages/{seance_id}")
def ancrage(seance_id: str) -> dict:
    a = seances.lire_ancrage(seance_id)
    if a is None:
        raise HTTPException(404, "Aucun ancrage pour cette séance")
    return a
