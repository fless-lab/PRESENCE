from fastapi.testclient import TestClient

from app.main import app


def test_sante():
    reponse = TestClient(app).get("/sante")
    assert reponse.status_code == 200
    assert reponse.json()["statut"] == "ok"
