from fastapi import APIRouter

from app import __version__

router = APIRouter(tags=["santé"])


@router.get("/sante")
def sante() -> dict:
    return {"statut": "ok", "version": __version__, "protocole": "0.1"}
