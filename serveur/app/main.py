"""Point d'entrée de l'API PRESENCE."""

from fastapi import FastAPI

from app import __version__
from app.api import sante

app = FastAPI(title="PRESENCE", version=__version__)
app.include_router(sante.router)
