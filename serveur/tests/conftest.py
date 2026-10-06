import pytest
from fastapi.testclient import TestClient

from app import etat, horloge, registre
from app.config import config
from app.securite import jetons
from tests.simulation import Horloge


@pytest.fixture
def horloge_test(monkeypatch):
    h = Horloge()
    monkeypatch.setattr(horloge, "maintenant_ms", h)
    return h


@pytest.fixture
def client(tmp_path, monkeypatch, horloge_test):
    monkeypatch.setattr(config, "donnees", tmp_path)
    monkeypatch.setattr(config, "registre", "local")
    monkeypatch.setattr(config, "attestation", "souple")
    registre.reinitialiser()
    etat.reinitialiser()
    jetons._secret = None
    from app.main import creer_app

    app = creer_app(f"sqlite:///{tmp_path / 'test.db'}")
    with TestClient(app) as c:
        yield c
    registre.reinitialiser()
    etat.reinitialiser()
