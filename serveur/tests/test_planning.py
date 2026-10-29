"""Planning : import, modifications locales, écarts avec l'école, notifications par promotion."""

from app.simulation import Telephone
from tests.test_parcours import _admin

HEURE = 3_600_000
JOUR = 24 * HEURE

PERSONNES = """matricule,nom,prenom,role,cours_code,cours_intitule,groupe,filiere,niveau
P001,Rami,Karim,ENSEIGNANT,SECRES,Sécurité des réseaux,INE3,SESNUM,INE3
P002,Saadi,Lina,ENSEIGNANT,BLOCK,Blockchain,INE2,SESNUM,INE2
E001,Alaoui,Yasmine,ETUDIANT,SECRES,Sécurité des réseaux,INE3,SESNUM,INE3
E002,Bennani,Omar,ETUDIANT,SECRES,Sécurité des réseaux,INE3,SESNUM,INE3
E003,Chraibi,Salma,ETUDIANT,BLOCK,Blockchain,INE2,SESNUM,INE2
"""


def _planning(*lignes):
    en_tete = "ref,cours_code,salle,debut,fin,enseignant,etat\n"
    return en_tete + "\n".join(lignes) + "\n"


def _importer(client, adm, contenu, chemin="/admin/planning/import"):
    r = client.post(chemin, files={"fichier": ("p.csv", contenu.encode(), "text/csv")}, headers=adm)
    assert r.status_code == 200, r.text
    return r.json()


def _installer(client):
    adm = _admin(client)
    client.post("/admin/salles", json={"id": 204, "nom": "Salle 204"}, headers=adm)
    client.post("/admin/salles", json={"id": 103, "nom": "Salle 103"}, headers=adm)
    rapport = _importer(client, adm, PERSONNES, "/admin/import")
    codes = {c["matricule"]: c["code"] for c in rapport["codes"]}
    tels = {m: Telephone(m) for m in codes}
    for m, t in tels.items():
        assert t.enroler(client, codes[m]).status_code == 200
    return adm, tels


def _creneau(client, adm, ref):
    liste = client.get("/admin/planning", headers=adm).json()["creneaux"]
    return next(c for c in liste if c["ref"] == ref)


def test_import_puis_modification_locale(client, horloge_test):
    h = horloge_test
    adm, tels = _installer(client)
    t0 = h() + JOUR

    def iso(ms):
        return ms  # l'import accepte aussi des millisecondes

    r = _importer(
        client,
        adm,
        _planning(
            f"R1,SECRES,204,{iso(t0 + 9 * HEURE)},{iso(t0 + 11 * HEURE)},,",
            f"R2,BLOCK,103,{iso(t0 + 14 * HEURE)},{iso(t0 + 16 * HEURE)},,",
        ),
    )
    assert (r["crees"], r["modifies"], r["erreurs"]) == (2, 0, [])

    # Première importation : rien à notifier
    e1 = tels["E001"]
    assert e1.requete(client, h, "GET", "/moi/changements").json()["changements"] == []
    plan = e1.requete(client, h, "GET", "/moi/planning").json()["creneaux"]
    assert [c["ref"] for c in plan] == ["R1"]  # E001 n'a que SECRES
    assert (
        tels["P001"].requete(client, h, "GET", "/moi/planning").json()["creneaux"][0]["ref"] == "R1"
    )

    # Même fichier : aucun changement
    r = _importer(
        client,
        adm,
        _planning(
            f"R1,SECRES,204,{iso(t0 + 9 * HEURE)},{iso(t0 + 11 * HEURE)},,",
            f"R2,BLOCK,103,{iso(t0 + 14 * HEURE)},{iso(t0 + 16 * HEURE)},,",
        ),
    )
    assert (r["crees"], r["modifies"], r["inchanges"]) == (0, 0, 2)

    # La scolarité déplace R1 dans PRESENCE
    c1 = _creneau(client, adm, "R1")
    r = client.patch(
        f"/admin/planning/{c1['id']}",
        headers=adm,
        json={"debut": t0 + 13 * HEURE, "motif": "Enseignant en réunion"},
    )
    assert r.status_code == 200, r.text
    v = r.json()
    assert v["debut_ms"] == t0 + 13 * HEURE and v["fin_ms"] == t0 + 15 * HEURE  # durée conservée
    assert v["origine"] == "IMPORT"

    # Notification : E001 et P001 la voient, E003 (autre cours) non
    ch = e1.requete(client, h, "GET", "/moi/changements").json()["changements"]
    assert [c["type"] for c in ch] == ["DEPLACE"] and ch[0]["motif"] == "Enseignant en réunion"
    assert ch[0]["avant"]["debut_ms"] == t0 + 9 * HEURE
    assert tels["P001"].requete(client, h, "GET", "/moi/changements").json()["changements"]
    assert tels["E003"].requete(client, h, "GET", "/moi/changements").json()["changements"] == []
    # Lecture incrémentale
    dernier = ch[-1]["id"]
    assert e1.requete(client, h, "GET", f"/moi/changements?apres={dernier}").json() == {
        "changements": []
    }

    # Le planning du téléphone est à jour
    assert e1.requete(client, h, "GET", "/moi/planning").json()["creneaux"][0]["debut_ms"] == (
        t0 + 13 * HEURE
    )

    # Annulation puis rétablissement
    assert (
        client.post(
            f"/admin/planning/{c1['id']}/annulation", headers=adm, json={"motif": "Grève"}
        ).json()["etat"]
        == "ANNULE"
    )
    assert (
        client.patch(
            f"/admin/planning/{c1['id']}", headers=adm, json={"debut": t0, "motif": "x"}
        ).status_code
        == 409
    )
    assert (
        client.post(f"/admin/planning/{c1['id']}/retablissement", headers=adm, json={}).json()[
            "etat"
        ]
        == "PREVU"
    )
    types = [
        c["type"] for c in e1.requete(client, h, "GET", "/moi/changements").json()["changements"]
    ]
    assert types == ["DEPLACE", "ANNULE", "RETABLI"]

    # Motif obligatoire
    assert (
        client.post(
            f"/admin/planning/{c1['id']}/annulation", headers=adm, json={"motif": " "}
        ).status_code
        == 422
    )

    # Un créneau ajouté ici est notifié et marqué local
    r = client.post(
        "/admin/planning",
        headers=adm,
        json={
            "cours": "SECRES",
            "salle": 204,
            "debut": t0 + 2 * JOUR,
            "fin": t0 + 2 * JOUR + HEURE,
        },
    )
    assert r.json()["origine"] == "LOCAL"
    types = [
        c["type"] for c in e1.requete(client, h, "GET", "/moi/changements").json()["changements"]
    ]
    assert types[-1] == "CREE"


def test_import_ne_ecrase_pas_une_modification_locale(client, horloge_test):
    h = horloge_test
    adm, tels = _installer(client)
    t0 = h() + JOUR
    base = f"R1,SECRES,204,{t0 + 9 * HEURE},{t0 + 11 * HEURE},,"
    _importer(client, adm, _planning(base))
    c = _creneau(client, adm, "R1")
    client.patch(
        f"/admin/planning/{c['id']}", headers=adm, json={"salle": 103, "motif": "Salle 204 occupée"}
    )

    # L'école ne change rien : la modification faite ici reste
    r = _importer(client, adm, _planning(base))
    assert r["ecarts"] == [] and _creneau(client, adm, "R1")["salle"]["id"] == 103

    # L'école déplace le même créneau : écart signalé, rien d'appliqué
    r = _importer(client, adm, _planning(f"R1,SECRES,204,{t0 + 10 * HEURE},{t0 + 12 * HEURE},,"))
    assert len(r["ecarts"]) == 1 and r["modifies"] == 0
    assert r["ecarts"][0]["ecole"]["debut_ms"] == t0 + 10 * HEURE
    v = _creneau(client, adm, "R1")
    assert v["salle"]["id"] == 103 and v["debut_ms"] == t0 + 9 * HEURE

    # Un créneau absent du nouvel import est signalé, pas supprimé
    r = _importer(client, adm, _planning(f"R9,BLOCK,103,{t0 + 20 * HEURE},{t0 + 21 * HEURE},,"))
    assert r["non_retrouves"] == ["R1"]
    assert _creneau(client, adm, "R1")["etat"] == "PREVU"
    assert tels  # E001 reste inscrit : le journal garde l'historique


def test_erreurs_d_import_et_dates_locales(client, horloge_test):
    adm, _ = _installer(client)
    r = _importer(
        client,
        adm,
        _planning(
            "R1,INCONNU,204,2026-11-03T09:00,2026-11-03T11:00,,",
            "R2,SECRES,999,2026-11-03T09:00,2026-11-03T11:00,,",
            "R3,SECRES,204,2026-11-03T11:00,2026-11-03T09:00,,",
            "R4,SECRES,204,demain,2026-11-03T09:00,,",
            "R5,SECRES,204,2026-11-03T09:00,2026-11-03T11:00,P999,",
            "R6,SECRES,204,2026-11-03T09:00,2026-11-03T11:00,,",
        ),
    )
    assert r["crees"] == 1 and len(r["erreurs"]) == 5
    assert [e["ligne"] for e in r["erreurs"]] == [2, 3, 4, 5, 6]
    # 09:00 à Rabat en novembre = 09:00 UTC (heure légale UTC+1 hors Ramadan : 08:00 UTC)
    c = _creneau(client, adm, "R6")
    assert c["fin_ms"] - c["debut_ms"] == 2 * HEURE
    r = client.post(
        "/admin/planning/import", headers=adm, files={"fichier": ("x.csv", b"a,b\n1,2\n")}
    )
    assert r.status_code == 422


def test_filtres_filiere_niveau_et_codes(client, horloge_test):
    adm, _ = _installer(client)
    f = client.get("/admin/filieres", headers=adm).json()["filieres"]
    assert {(e["filiere"], e["niveau"]): e["etudiants"] for e in f if e["filiere"]} == {
        ("SESNUM", "INE3"): 2,
        ("SESNUM", "INE2"): 1,
    }
    assert (
        len(
            client.get("/admin/personnes?filiere=SESNUM&niveau=INE3", headers=adm).json()[
                "personnes"
            ]
        )
        == 3
    )  # 2 étudiants + P001
    cours = client.get("/admin/cours?niveau=INE3", headers=adm).json()["cours"]
    assert [(c["code"], c["inscrits"], c["enseignant"]["matricule"]) for c in cours] == [
        ("SECRES", 2, "P001")
    ]
    t0 = 1_800_000_000_000
    _importer(
        client,
        adm,
        _planning(
            f"R1,SECRES,204,{t0},{t0 + HEURE},,",
            f"R2,BLOCK,103,{t0},{t0 + HEURE},,",
        ),
    )
    ine3 = client.get("/admin/planning?niveau=INE3", headers=adm).json()["creneaux"]
    assert [c["ref"] for c in ine3] == ["R1"]


def test_code_expire_et_export(client, horloge_test):
    h = horloge_test
    adm = _admin(client)
    rapport = _importer(client, adm, PERSONNES, "/admin/import")
    codes = {c["matricule"]: c["code"] for c in rapport["codes"]}
    export = client.get("/admin/codes.csv?niveau=INE3", headers=adm)
    assert export.status_code == 200
    lignes = export.text.strip().splitlines()
    assert lignes[0].startswith("matricule,nom,prenom,filiere,niveau,code")
    assert len(lignes) == 4 and codes["E001"] in export.text and codes["E003"] not in export.text

    h.avancer(15 * JOUR)  # au-delà de 14 jours
    r = Telephone("E001").enroler(client, codes["E001"])
    assert r.status_code == 403 and "expiré" in r.json()["detail"]
    # Un nouveau code remet les choses en ordre (le jeton de la scolarité a aussi expiré)
    r = client.post("/auth/connexion", json={"matricule": "A001", "mot_de_passe": "secret"})
    adm = {"Authorization": f"Bearer {r.json()['jeton']}"}
    nouveau = client.post("/admin/personnes/E001/code", headers=adm).json()["code"]
    assert Telephone("E001").enroler(client, nouveau).status_code == 200
