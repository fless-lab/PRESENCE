"""Point d'entrée de l'API PRESENCE."""

from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app import __version__, db
from app.api import admin, enrolement, enseignant, equipements, moi, publiques
from app.config import config


def creer_app(base_url: str | None = None) -> FastAPI:
    db.initialiser(base_url or config.base_url)
    app = FastAPI(title="PRESENCE", version=__version__)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=config.cors,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    for module in (publiques, enrolement, moi, enseignant, equipements, admin):
        app.include_router(module.router)
    return app


_app: FastAPI | None = None


def __getattr__(nom: str):
    """`uvicorn app.main:app` crée l'application au premier accès."""
    global _app
    if nom == "app":
        if _app is None:
            _app = creer_app()
        return _app
    raise AttributeError(nom)
