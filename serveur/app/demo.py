"""`presence demo` : remplit la base avec un historique réaliste.

Tout passe par les vraies routes de l'API, avec de vraies signatures : téléphones et
observateurs simulés, séances des jours précédents (horloge déplacée dans le passé),
scellement et ancrage sur le registre configuré. Les clés simulées sont gardées dans
`demo_cles.json` pour que `presence simuler` puisse jouer une séance en direct.
"""

from __future__ import annotations

import json
import random
from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient
from sqlalchemy import select

from app import horloge
from app.config import config
from app.db import nouvelle_session
from app.modeles import Personne
from app.securite.jetons import hacher_mot_de_passe
from app.simulation import Horloge, Observateur, Telephone, exporter_cle, importer_cle

MINUTE = 60_000

ENSEIGNANTS = [
    ("P001", "Rami", "Karim", "SECRES", "Sécurité des réseaux"),
    ("P002", "Saadi", "Lina", "BLOCK", "Blockchain et registres distribués"),
]
ETUDIANTS = [
    ("Alaoui", "Yasmine"),
    ("Bennani", "Omar"),
    ("Chraibi", "Salma"),
    ("Daoudi", "Adam"),
    ("El Fassi", "Nour"),
    ("Fikri", "Ilyas"),
    ("Ghazali", "Sara"),
    ("Haddad", "Mehdi"),
    ("Idrissi", "Imane"),
    ("Jabri", "Youssef"),
    ("Kettani", "Rim"),
    ("Lahlou", "Hamza"),
    ("Mansouri", "Aya"),
    ("Naciri", "Zakaria"),
    ("Ouazzani", "Kenza"),
    ("Rahmouni", "Anas"),
    ("Sefrioui", "Lina"),
    ("Tazi", "Amine"),
]
SALLES = [(204, "Salle 204"), (103, "Salle 103")]
OBSERVATEURS = [("204-A", 204), ("204-B", 204), ("103-A", 103)]


def _csv() -> str:
    lignes = ["matricule,nom,prenom,role,cours_code,cours_intitule,groupe"]
    for m, n, p, code, intitule in ENSEIGNANTS:
        lignes.append(f"{m},{n},{p},ENSEIGNANT,{code},{intitule},SESNUM 5")
    for i, (n, p) in enumerate(ETUDIANTS, start=1):
        for _, _, _, code, intitule in ENSEIGNANTS:
            lignes.append(f"E{i:03d},{n},{p},ETUDIANT,{code},{intitule},SESNUM 5")
    return "\n".join(lignes) + "\n"


class Demo:
    def __init__(self, client, h: Horloge, graine: int = 7):
        self.c, self.h = client, h
        self.alea = random.Random(graine)
        self.tels: dict[str, Telephone] = {}
        self.obs: dict[str, Observateur] = {}
        self.adm: dict = {}

    def installer(self, mot_de_passe: str) -> None:
        s = nouvelle_session()
        if s.scalar(select(Personne).where(Personne.matricule == "A001")) is None:
            s.add(
                Personne(
                    matricule="A001",
                    nom="Scolarité",
                    prenom="Admin",
                    role="ADMIN",
                    mot_de_passe=hacher_mot_de_passe(mot_de_passe),
                )
            )
            s.commit()
        s.close()
        self.connecter(mot_de_passe)
        for sid, nom in SALLES:
            self.c.post("/admin/salles", json={"id": sid, "nom": nom}, headers=self.adm)
        rapport = self.c.post(
            "/admin/import",
            headers=self.adm,
            files={"fichier": ("demo.csv", _csv().encode(), "text/csv")},
        ).json()
        for nom, salle in OBSERVATEURS:
            o = Observateur(nom, salle)
            self.c.post(
                "/admin/equipements",
                headers=self.adm,
                json={"nom": nom, "salle": salle, "cle_publique": o.spki_b64},
            )
            self.obs[nom] = o
        for code in rapport["codes"]:
            t = Telephone(code["matricule"])
            t.enroler(self.c, code["code"]).raise_for_status()
            self.tels[t.matricule] = t

    def connecter(self, mot_de_passe: str) -> None:
        r = self.c.post("/auth/connexion", json={"matricule": "A001", "mot_de_passe": mot_de_passe})
        r.raise_for_status()
        self.adm = {"Authorization": f"Bearer {r.json()['jeton']}"}

    def sauvegarder(self) -> None:
        donnees = {
            "telephones": {
                m: {"cle": exporter_cle(t), "compteur": t.compteur} for m, t in self.tels.items()
            },
            "observateurs": {
                n: {"cle": exporter_cle(o), "salle": o.salle, "numero": o.numero}
                for n, o in self.obs.items()
            },
        }
        (config.donnees / "demo_cles.json").write_text(json.dumps(donnees, indent=1))

    def _comportement(self) -> str:
        return self.alea.choices(
            ["PRESENT", "RETARD", "DEPART", "PARTIEL", "ABSENT"], [78, 8, 5, 3, 6]
        )[0]

    def seance(
        self,
        prof: str,
        cours: str,
        salle: int,
        duree_min: int,
        pause: bool = True,
        sceller: bool = True,
    ) -> str:
        t_prof = self.tels[prof]
        obs = [o for o in self.obs.values() if o.salle == salle]
        obs[0].tourner()
        obs[0].envoyer(self.c, self.h, [obs[0].temoigner(t_prof, 0, self.h())])
        r = t_prof.requete(self.c, self.h, "POST", "/seances", {"cours": cours})
        r.raise_for_status()
        sid, num = r.json()["id"], r.json()["numero"]
        etudiants = [m for m in self.tels if m.startswith("E")]
        profils = {m: self._comportement() for m in etudiants}
        debut_pause = duree_min // 2 if pause else None
        for minute in range(duree_min + (15 if pause else 0)):
            en_pause = pause and debut_pause <= minute < debut_pause + 15
            if pause and minute == debut_pause:
                t_prof.requete(self.c, self.h, "POST", f"/seances/{sid}/pause")
            if pause and minute == debut_pause + 15:
                t_prof.requete(self.c, self.h, "POST", f"/seances/{sid}/reprise")
            if not en_pause and minute % 2 == 0:
                for o in obs:
                    o.tourner()
                lots: dict[str, list] = {o.nom: [] for o in obs}
                actif = minute - (15 if pause and minute >= debut_pause + 15 else 0)
                for m in etudiants + [prof]:
                    p = profils.get(m, "PRESENT")
                    if (
                        p == "ABSENT"
                        or (p == "RETARD" and actif < 22)
                        or (p == "DEPART" and actif > duree_min - 25)
                        or (p == "PARTIEL" and (actif // 10) % 2 == 1)
                        or self.alea.random() < 0.06
                    ):
                        continue
                    o = self.alea.choice(obs)
                    recu = self.h() + self.alea.randint(0, 50_000)
                    lots[o.nom].append(o.temoigner(self.tels[m], num, recu))
                for o in obs:
                    if lots[o.nom]:
                        o.envoyer(self.c, self.h, lots[o.nom])
            self.h.avancer(MINUTE)
        t_prof.requete(self.c, self.h, "POST", f"/seances/{sid}/cloture").raise_for_status()
        direct = t_prof.requete(self.c, self.h, "GET", f"/seances/{sid}/direct").json()
        for e in direct["inscrits"]:
            if e["statut"] == "A_VERIFIER":
                t_prof.requete(
                    self.c,
                    self.h,
                    "POST",
                    f"/seances/{sid}/decisions",
                    {
                        "matricule": e["matricule"],
                        "decision": "PRESENT",
                        "motif": "Présence confirmée par l'enseignant",
                    },
                )
        absents = [e["matricule"] for e in direct["inscrits"] if e["statut"] == "ABSENT"]
        if absents and self.alea.random() < 0.5:
            t_prof.requete(
                self.c,
                self.h,
                "POST",
                f"/seances/{sid}/validations",
                {"matricule": absents[0], "motif": "Téléphone déchargé"},
            )
        if sceller:
            t_prof.requete(self.c, self.h, "POST", f"/seances/{sid}/scellement").raise_for_status()
        return sid


def executer(mot_de_passe: str, jours: int = 5) -> list[str]:
    from app import etat, registre
    from app.main import creer_app

    registre.reinitialiser()
    etat.reinitialiser()
    vrai = horloge.maintenant_ms
    aujourd_hui = datetime.now(UTC).replace(hour=0, minute=0, second=0, microsecond=0)
    h = Horloge(int((aujourd_hui - timedelta(days=jours)).timestamp() * 1000))
    horloge.maintenant_ms = h  # type: ignore[assignment]
    seances = []
    try:
        app = creer_app()
        with TestClient(app) as c:
            d = Demo(c, h)
            d.installer(mot_de_passe)
            for j in range(jours, 0, -1):
                jour = aujourd_hui - timedelta(days=j)
                if jour.weekday() >= 5:
                    continue
                h.t = int((jour + timedelta(hours=8, minutes=58)).timestamp() * 1000)
                seances.append(d.seance("P001", "SECRES", 204, 90))
                h.t = int((jour + timedelta(hours=14, minutes=2)).timestamp() * 1000)
                seances.append(d.seance("P002", "BLOCK", 103, 75))
            if len(seances) >= 2:
                d.connecter(mot_de_passe)
                r = c.post(
                    f"/admin/seances/{seances[0]}/corrections",
                    headers=d.adm,
                    json={
                        "matricule": "E004",
                        "statut": "PRESENT",
                        "motif": "Justificatif médical accepté",
                    },
                )
                r.raise_for_status()
            d.sauvegarder()
    finally:
        horloge.maintenant_ms = vrai  # type: ignore[assignment]
    return seances


def charger_cles() -> tuple[dict[str, Telephone], dict[str, Observateur]]:
    donnees = json.loads((config.donnees / "demo_cles.json").read_text())
    tels, obs = {}, {}
    for m, v in donnees["telephones"].items():
        t = Telephone(m)
        importer_cle(t, v["cle"])
        t.compteur = v["compteur"]
        tels[m] = t
    for n, v in donnees["observateurs"].items():
        o = Observateur(n, v["salle"])
        importer_cle(o, v["cle"])
        o.numero = v["numero"]
        obs[n] = o
    return tels, obs
