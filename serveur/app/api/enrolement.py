from fastapi import APIRouter, Body, Depends
from sqlalchemy.orm import Session

from app.db import session
from app.services import personnes

router = APIRouter(prefix="/enrolement", tags=["enrôlement"])


@router.post("/defi")
def defi(matricule: str = Body(..., embed=True), db: Session = Depends(session)) -> dict:
    return personnes.emettre_defi(db, matricule)


@router.post("")
def enroler(demande: dict = Body(...), db: Session = Depends(session)) -> dict:
    return personnes.enroler(db, demande)
