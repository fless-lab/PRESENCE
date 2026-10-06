"""`presence simuler` : une séance jouée en direct contre un serveur en marche.

Téléphones et observateurs simulés (clés créées par `presence demo`) envoient de vraies
attestations signées, au rythme réel. Le tableau de bord montre la séance en direct.
"""

from __future__ import annotations

import json
import random
import time
from pathlib import Path

import httpx

from app.config import config
from app.demo import charger_cles

PROFILS = {"PRESENT": 0.80, "RETARD": 0.08, "DEPART": 0.05, "ABSENT": 0.07}


class HorlogeReelle:
    def __call__(self) -> int:
        return int(time.time() * 1000)


def executer(
    url: str,
    duree_min: int,
    periode_s: int,
    cours: str,
    prof: str,
    salle: int,
    cloturer: bool,
    sortie: Path,
) -> None:
    tels, obs = charger_cles()
    h = HorlogeReelle()
    c = httpx.Client(base_url=url, timeout=30)
    observateurs = [o for o in obs.values() if o.salle == salle]
    if not observateurs:
        raise SystemExit(f"Aucun observateur simulé pour la salle {salle}")
    alea = random.Random()
    etudiants = [m for m in tels if m.startswith("E")]
    profils = {m: alea.choices(list(PROFILS), list(PROFILS.values()))[0] for m in etudiants}

    def attester(numero: int, qui: list[str]) -> None:
        for o in observateurs:
            o.tourner()
        lots: dict[str, list] = {o.nom: [] for o in observateurs}
        for m in qui:
            o = alea.choice(observateurs)
            lots[o.nom].append(o.temoigner(tels[m], numero, h()))
        for o in observateurs:
            if lots[o.nom]:
                r = o.envoyer(c, h, lots[o.nom]).json()
                if r.get("rejetes"):
                    print("  rejets :", r["rejetes"][:3])

    for o in observateurs:
        o.config(c, h).raise_for_status()
    attester(0, [prof])
    r = tels[prof].requete(c, h, "POST", "/seances", {"cours": cours})
    if r.status_code != 200:
        raise SystemExit(f"Démarrage refusé : {r.text}")
    seance = r.json()
    sid, numero = seance["id"], seance["numero"]
    print(f"Séance {sid} démarrée, salle {salle}, {len(etudiants)} inscrits")
    debut = time.time()
    try:
        while (ecoule := (time.time() - debut) / 60) < duree_min:
            presents = [prof]
            for m in etudiants:
                p = profils[m]
                if p == "ABSENT" or (p == "RETARD" and ecoule < duree_min * 0.3):
                    continue
                if p == "DEPART" and ecoule > duree_min * 0.6:
                    continue
                if alea.random() < 0.05:
                    continue
                presents.append(m)
            attester(numero, presents)
            print(f"  {ecoule:5.1f} min : {len(presents) - 1} téléphones attestés")
            time.sleep(periode_s)
    except KeyboardInterrupt:
        print("Interrompu")
    finally:
        _sauver_compteurs(tels)

    if not cloturer:
        print("Séance laissée ouverte.")
        return
    tels[prof].requete(c, h, "POST", f"/seances/{sid}/cloture").raise_for_status()
    direct = tels[prof].requete(c, h, "GET", f"/seances/{sid}/direct").json()
    for e in direct["inscrits"]:
        if e["statut"] == "A_VERIFIER":
            tels[prof].requete(
                c,
                h,
                "POST",
                f"/seances/{sid}/decisions",
                {
                    "matricule": e["matricule"],
                    "decision": "PRESENT",
                    "motif": "Confirmé par l'enseignant",
                },
            )
    r = tels[prof].requete(c, h, "POST", f"/seances/{sid}/scellement")
    r.raise_for_status()
    print(f"Séance scellée. Racine : {r.json()['racine']}")
    recu = tels["E001"].requete(c, h, "GET", f"/moi/recus/{sid}").json()
    sortie.write_text(json.dumps(recu, ensure_ascii=False, indent=2))
    print(f"Reçu de E001 écrit dans {sortie}")


def _sauver_compteurs(tels) -> None:
    chemin = config.donnees / "demo_cles.json"
    donnees = json.loads(chemin.read_text())
    for m, t in tels.items():
        donnees["telephones"][m]["compteur"] = t.compteur
    chemin.write_text(json.dumps(donnees, indent=1))
