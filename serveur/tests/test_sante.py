def test_sante(client):
    r = client.get("/sante")
    assert r.status_code == 200
    assert r.json()["statut"] == "ok"


def test_heure(client, horloge_test):
    assert client.get("/heure").json()["ms"] == horloge_test()
