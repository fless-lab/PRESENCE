"""Scénario complet : import, enrôlement, séance, attestations, clôture, scellement, reçu, audit."""

import json
import sqlite3

from app.config import config
from app.db import nouvelle_session
from app.domaine import merkle, presence
from app.modeles import Personne
from app.securite.jetons import hacher_mot_de_passe
from app.services import recus
from tests.simulation import Observateur, Telephone

MINUTE = 60_000
CSV = """matricule,nom,prenom,role,cours_code,cours_intitule,groupe
P001,Rami,Karim,ENSEIGNANT,SECRES,Sécurité des réseaux,SESNUM 5
E001,Alaoui,Yasmine,ETUDIANT,SECRES,Sécurité des réseaux,SESNUM 5
E002,Bennani,Omar,ETUDIANT,SECRES,Sécurité des réseaux,SESNUM 5
E003,Chraibi,Salma,ETUDIANT,SECRES,Sécurité des réseaux,SESNUM 5
E004,Daoudi,Adam,ETUDIANT,SECRES,Sécurité des réseaux,SESNUM 5
E005,El Fassi,Nour,ETUDIANT,SECRES,Sécurité des réseaux,SESNUM 5
"""


def _admin(client):
    s = nouvelle_session()
    s.add(
        Personne(
            matricule="A001",
            nom="Admin",
            prenom="Test",
            role="ADMIN",
            mot_de_passe=hacher_mot_de_passe("secret"),
        )
    )
    s.commit()
    s.close()
    r = client.post("/auth/connexion", json={"matricule": "A001", "mot_de_passe": "secret"})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['jeton']}"}


def _installer(client, h):
    adm = _admin(client)
    for sid, nom in ((204, "Salle 204"), (103, "Salle 103")):
        assert (
            client.post("/admin/salles", json={"id": sid, "nom": nom}, headers=adm).status_code
            == 200
        )
    rapport = client.post(
        "/admin/import", files={"fichier": ("i.csv", CSV.encode(), "text/csv")}, headers=adm
    ).json()
    assert rapport["personnes_creees"] == 6 and rapport["inscriptions"] == 5
    codes = {c["matricule"]: c["code"] for c in rapport["codes"]}

    obs_a, obs_b = Observateur("204-A", 204), Observateur("103-A", 103)
    for o in (obs_a, obs_b):
        r = client.post(
            "/admin/equipements",
            headers=adm,
            json={"nom": o.nom, "salle": o.salle, "cle_publique": o.spki_b64},
        )
        assert r.status_code == 200, r.text

    tels = {m: Telephone(m) for m in codes}
    for m, t in tels.items():
        r = t.enroler(client, codes[m])
        assert r.status_code == 200, r.text
        assert r.json()["id_appareil"] == t.id
    return adm, obs_a, obs_b, tels


def test_parcours_complet(client, horloge_test):
    h = horloge_test
    adm, obs_a, obs_b, tels = _installer(client, h)
    prof = tels["P001"]

    # Hors séance : l'observateur ne connaît que les enseignants
    cfg = obs_a.config(client, h).json()
    assert cfg["seance"] is None and [c["id"] for c in cfg["cles"]] == [prof.id]

    # Démarrage refusé tant que l'enseignant n'est pas attesté dans une salle
    assert prof.requete(client, h, "POST", "/seances", {"cours": "SECRES"}).status_code == 409
    r = obs_a.envoyer(client, h, [obs_a.temoigner(prof, 0, h())])
    assert r.json() == {"acceptes": 1, "rejetes": []}
    assert prof.requete(client, h, "GET", "/enseignant/presence").json()["salle"] == 204
    # Un étudiant ne peut pas s'attester hors séance
    r = obs_a.envoyer(client, h, [obs_a.temoigner(tels["E001"], 0, h())])
    assert r.json()["rejetes"][0]["raison"].startswith("hors séance")

    r = prof.requete(client, h, "POST", "/seances", {"cours": "SECRES"})
    assert r.status_code == 200, r.text
    seance = r.json()
    sid, num = seance["id"], seance["numero"]
    cfg = obs_a.config(client, h).json()
    assert cfg["seance"]["numero"] == num and len(cfg["cles"]) == 6

    # 75 minutes simulées : pause de 15 minutes à la 30e minute
    debut = h()
    for minute in range(0, 75):
        if minute == 30:
            prof.requete(client, h, "POST", f"/seances/{sid}/pause")
        if minute == 45:
            prof.requete(client, h, "POST", f"/seances/{sid}/reprise")
        en_pause = 30 <= minute < 45
        if not en_pause:
            obs_a.tourner()
            lot = [obs_a.temoigner(tels[m], num, h()) for m in ("E001", "E002", "E003")]
            if minute >= 20:
                lot.append(obs_a.temoigner(tels["E004"], num, h()))
            r = obs_a.envoyer(client, h, lot)
            assert r.json()["rejetes"] == [], r.json()
        h.avancer(MINUTE)
    assert h() - debut == 75 * MINUTE

    direct = prof.requete(client, h, "GET", f"/seances/{sid}/direct").json()
    statuts = {e["matricule"]: e["statut"] for e in direct["inscrits"]}
    assert statuts == {
        "E001": "PRESENT",
        "E002": "PRESENT",
        "E003": "PRESENT",
        "E004": "RETARD",
        "E005": "ABSENT",
    }, statuts

    r = prof.requete(client, h, "POST", f"/seances/{sid}/cloture")
    assert r.json()["etat"] == "CLOTUREE"
    # Plus aucune attestation acceptée après la clôture
    r = obs_a.envoyer(client, h, [obs_a.temoigner(tels["E005"], num, h())])
    assert r.json()["rejetes"][0]["raison"] == "séance fermée"

    r = prof.requete(
        client,
        h,
        "POST",
        f"/seances/{sid}/validations",
        {"matricule": "E005", "motif": "Téléphone déchargé"},
    )
    assert r.status_code == 200
    r = prof.requete(client, h, "POST", f"/seances/{sid}/scellement")
    assert r.status_code == 200, r.text
    racine = r.json()["racine"]
    assert client.get(f"/ancrages/{sid}").json()["racine"] == racine

    detail = client.get(f"/admin/seances/{sid}", headers=adm).json()
    assert {e["matricule"]: e["statut"] for e in detail["etudiants"]}["E005"] == "PRESENT"
    assert detail["etat"] == "SCELLEE" and detail["presents"] == 5

    # Reçu de E004 : signature, preuves, statut recalculé de façon indépendante
    recu = tels["E004"].requete(client, h, "GET", f"/moi/recus/{sid}").json()
    cle = client.get("/cle-serveur").json()["cle_publique"]
    assert recus.verifier_signature(recu, cle)
    taille, rac = recu["arbre"]["taille"], bytes.fromhex(recu["arbre"]["racine"])
    for e in recu["evenements"]:
        assert merkle.verifier_inclusion(
            e["index"],
            taille,
            e["canonique"].encode(),
            [bytes.fromhex(p) for p in e["preuve"]],
            rac,
        )
    evts = [json.loads(e["canonique"]) for e in recu["evenements"]]
    ivs = presence.fermer(
        presence.intervalles(
            (e["type"], e["horodatage"])
            for e in evts
            if e["type"] in ("DEBUT", "PAUSE", "REPRISE", "CLOTURE")
        ),
        0,
    )
    n = presence.nb_fenetres(presence.duree_active(ivs))
    mes = [
        e["horodatage"]
        for e in evts
        if e["type"] == "ATTESTATION" and e["contenu"]["appareil"] == recu["titulaire"]["appareil"]
    ]
    assert (
        presence.statut(presence.fenetres_validees(mes, ivs, n), n).statut
        == recu["statut"]
        == "RETARD"
    )
    # Un reçu modifié ne passe plus
    falsifie = dict(recu, statut="PRESENT")
    assert not recus.verifier_signature(falsifie, cle)

    audit = client.get("/audit", headers=adm).json()["seances"]
    assert audit[0]["conforme"]

    # Correction de la scolarité après scellement : nouvelle racine ancrée et chaînée
    r = client.post(
        f"/admin/seances/{sid}/corrections",
        headers=adm,
        json={"matricule": "E004", "statut": "PRESENT", "motif": "Justificatif"},
    )
    assert r.status_code == 200, r.text
    ancrage = client.get(f"/ancrages/{sid}").json()
    assert ancrage["corrections"][0]["racine_precedente"] == racine
    assert client.get("/audit", headers=adm).json()["seances"][0]["conforme"]

    # Falsification directe de la base : l'audit la détecte
    base = sqlite3.connect(config.donnees / "test.db")
    ligne = base.execute(
        "SELECT id, canonique FROM evenements WHERE seance_id=? AND type='ATTESTATION' LIMIT 1",
        (sid,),
    ).fetchone()
    base.execute(
        "UPDATE evenements SET canonique=? WHERE id=?",
        (ligne[1].replace('"compteur":', '"compteur":9'), ligne[0]),
    )
    base.commit()
    base.close()
    assert not client.get("/audit", headers=adm).json()["seances"][0]["conforme"]


def test_rejets(client, horloge_test):
    h = horloge_test
    adm, obs_a, obs_b, tels = _installer(client, h)
    prof = tels["P001"]
    obs_a.envoyer(client, h, [obs_a.temoigner(prof, 0, h())])
    num = prof.requete(client, h, "POST", "/seances", {"cours": "SECRES"}).json()["numero"]

    t = obs_a.temoigner(tels["E001"], num, h())
    assert obs_a.envoyer(client, h, [t]).json()["acceptes"] == 1
    # Rejeu du même témoignage
    assert "rejoué" in obs_a.envoyer(client, h, [t]).json()["rejetes"][0]["raison"]
    # Signature de l'observateur falsifiée
    t2 = obs_a.temoigner(tels["E002"], num, h())
    t2["recu_ms"] += 1
    assert "observateur" in obs_a.envoyer(client, h, [t2]).json()["rejetes"][0]["raison"]
    # Observateur d'une autre salle qui prétend témoigner pour cette séance
    t3 = obs_b.temoigner(tels["E003"], num, h())
    assert "séance inconnue" in obs_b.envoyer(client, h, [t3]).json()["rejetes"][0]["raison"]
    # Téléphone inconnu
    intrus = Telephone("X999")
    t4 = obs_a.temoigner(intrus, num, h())
    assert "inconnu" in obs_a.envoyer(client, h, [t4]).json()["rejetes"][0]["raison"]
    # Requête signée rejouée
    corps = json.dumps({"temoignages": []}).encode()
    entetes = obs_a.entetes("equ:204-A", "POST", "/equipements/moi/temoignages", corps, h())
    assert (
        client.post("/equipements/moi/temoignages", content=corps, headers=entetes).status_code
        == 200
    )
    assert (
        client.post("/equipements/moi/temoignages", content=corps, headers=entetes).status_code
        == 401
    )
    # Un étudiant ne peut pas piloter une séance
    assert (
        tels["E001"].requete(client, h, "POST", "/seances", {"cours": "SECRES"}).status_code == 403
    )


def test_double_presence(client, horloge_test):
    h = horloge_test
    adm, obs_a, obs_b, tels = _installer(client, h)
    prof = tels["P001"]
    # Deuxième cours dans la salle 103, même étudiant inscrit, autre enseignant
    csv = (
        "matricule,nom,prenom,role,cours_code,cours_intitule,groupe\n"
        "P002,Saadi,Lina,ENSEIGNANT,BLOCK,Blockchain,SESNUM 5\n"
        "E002,Bennani,Omar,ETUDIANT,BLOCK,Blockchain,SESNUM 5\n"
    )
    rapport = client.post(
        "/admin/import", files={"fichier": ("i.csv", csv.encode())}, headers=adm
    ).json()
    prof2 = Telephone("P002")
    assert prof2.enroler(client, rapport["codes"][0]["code"]).status_code == 200

    obs_a.envoyer(client, h, [obs_a.temoigner(prof, 0, h())])
    obs_b.envoyer(client, h, [obs_b.temoigner(prof2, 0, h())])
    s1 = prof.requete(client, h, "POST", "/seances", {"cours": "SECRES"}).json()
    s2 = prof2.requete(client, h, "POST", "/seances", {"cours": "BLOCK"}).json()
    for _ in range(20):
        obs_a.tourner()
        obs_b.tourner()
        obs_a.envoyer(client, h, [obs_a.temoigner(tels["E002"], s1["numero"], h())])
        h.avancer(30_000)
        obs_b.envoyer(client, h, [obs_b.temoigner(tels["E002"], s2["numero"], h())])
        h.avancer(30_000)
    d = prof.requete(client, h, "GET", f"/seances/{s1['id']}/direct").json()
    e2 = next(e for e in d["inscrits"] if e["matricule"] == "E002")
    assert e2["statut"] == "A_VERIFIER" and "103" in e2["motif_a_verifier"]
    prof.requete(client, h, "POST", f"/seances/{s1['id']}/cloture")
    # Scellement refusé tant que le cas n'est pas tranché
    assert prof.requete(client, h, "POST", f"/seances/{s1['id']}/scellement").status_code == 409
    prof.requete(
        client,
        h,
        "POST",
        f"/seances/{s1['id']}/decisions",
        {"matricule": "E002", "decision": "ABSENT", "motif": "Vu dans l'autre cours"},
    )
    assert prof.requete(client, h, "POST", f"/seances/{s1['id']}/scellement").status_code == 200


def test_enrolement(client, horloge_test):
    adm, *_, tels = _installer(client, horloge_test)
    # Code à usage unique
    t = Telephone("E001")
    assert t.enroler(client, "AAA-AAA").status_code == 403
    # Nouveau code : l'ancien appareil est révoqué
    code = client.post("/admin/personnes/E001/code", headers=adm).json()["code"]
    assert t.enroler(client, code).status_code == 200
    ancien = tels["E001"]
    assert ancien.requete(client, horloge_test, "GET", "/moi").status_code == 401
    assert t.requete(client, horloge_test, "GET", "/moi").json()["matricule"] == "E001"
