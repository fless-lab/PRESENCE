#!/usr/bin/env python3
"""Vérificateur indépendant d'un reçu de présence PRESENCE.

N'utilise ni le code du serveur ni sa base : seulement le reçu, la clé publique du
serveur et la racine lue sur le registre. Il recalcule lui-même le statut.

    python verifier.py recu.json --serveur http://localhost:8000
    python verifier.py recu.json --cle <base64> --racine <hex>     (hors ligne)

Dépendance : cryptography.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import sys
import urllib.request

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

W, RESTE, SEUIL, RETARD = 300_000, 120_000, 0.8, 3


# --- Merkle (RFC 9162) -----------------------------------------------------------------------


def feuille(d: bytes) -> bytes:
    return hashlib.sha256(b"\x00" + d).digest()


def noeud(g: bytes, d: bytes) -> bytes:
    return hashlib.sha256(b"\x01" + g + d).digest()


def verifier_inclusion(index: int, taille: int, donnee: bytes, preuve: list[bytes],
                       racine: bytes) -> bool:
    if not 0 <= index < taille:
        return False
    fn, sn, r = index, taille - 1, feuille(donnee)
    for p in preuve:
        if sn == 0:
            return False
        if fn & 1 or fn == sn:
            r = noeud(p, r)
            if not fn & 1:
                while not fn & 1 and fn != 0:
                    fn >>= 1
                    sn >>= 1
        else:
            r = noeud(r, p)
        fn >>= 1
        sn >>= 1
    return sn == 0 and r == racine


# --- Statut (SPEC §7) ------------------------------------------------------------------------


def recalculer_statut(recu: dict) -> tuple[str, int, int]:
    evts = [json.loads(e["canonique"]) for e in recu["evenements"]]
    ivs, ouvert = [], None
    for e in evts:
        t = e["type"]
        if t in ("DEBUT", "REPRISE") and ouvert is None:
            ouvert = e["horodatage"]
        elif t in ("PAUSE", "CLOTURE") and ouvert is not None:
            ivs.append((ouvert, e["horodatage"]))
            ouvert = None
        if t == "CLOTURE":
            break
    if ouvert is not None:
        ivs.append((ouvert, recu["emis_ms"]))
    a = sum(f - d for d, f in ivs)
    n = a // W + (1 if a % W >= RESTE else 0)
    if a > 0 and n == 0:
        n = 1

    def position(t: int):
        cumul = 0
        for d, f in ivs:
            if d <= t < f:
                return cumul + t - d
            cumul += f - d
        return None

    titulaire = recu["titulaire"]
    v = sorted({
        p // W for e in evts
        if e["type"] == "ATTESTATION" and e["contenu"]["appareil"] == titulaire["appareil"]
        and (p := position(e["horodatage"])) is not None and p // W < n
    })
    perso = [e for e in evts if e["contenu"].get("matricule") == titulaire["matricule"]]
    corrections = [e for e in perso if e["type"] == "CORRECTION"]
    decisions = [e for e in perso if e["type"] == "DECISION"]
    if corrections:
        return corrections[-1]["contenu"]["statut"], len(v), n
    if decisions:
        return decisions[-1]["contenu"]["decision"], len(v), n
    if any(e["type"] == "VALIDATION_MANUELLE" for e in perso):
        return "PRESENT", len(v), n
    if not v or n == 0:
        return "ABSENT", len(v), n
    if len(v) / n >= SEUIL:
        return "PRESENT", len(v), n
    f, last = v[0], v[-1]
    if f >= RETARD and len(v) / (n - f) >= SEUIL:
        return "RETARD", len(v), n
    if last <= n - RETARD and len(v) / (last + 1) >= SEUIL:
        return "DEPART_ANTICIPE", len(v), n
    return "PARTIEL", len(v), n


# --- Signature et registre -------------------------------------------------------------------


def canonique(o) -> bytes:
    return json.dumps(o, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()


def verifier_signature(recu: dict, cle_b64: str) -> bool:
    cle = serialization.load_der_public_key(base64.b64decode(cle_b64))
    corps = {k: v for k, v in recu.items() if k != "sig_serveur"}
    try:
        cle.verify(base64.b64decode(recu["sig_serveur"]), canonique(corps),
                   ec.ECDSA(hashes.SHA256()))
        return True
    except InvalidSignature:
        return False


def lire_json(url: str):
    with urllib.request.urlopen(url, timeout=15) as r:
        return json.loads(r.read())


def main() -> int:
    ap = argparse.ArgumentParser(description="Vérifie un reçu de présence PRESENCE")
    ap.add_argument("recu")
    ap.add_argument("--serveur", help="adresse du serveur, pour lire la clé et l'ancrage")
    ap.add_argument("--cle", help="clé publique du serveur (base64 SPKI), hors ligne")
    ap.add_argument("--racine", help="racine lue sur le registre, hors ligne")
    args = ap.parse_args()

    recu = json.loads(open(args.recu, encoding="utf-8").read())
    seance = recu["seance"]["id"]
    cle = args.cle or (lire_json(f"{args.serveur}/cle-serveur")["cle_publique"]
                       if args.serveur else None)
    racine_registre = args.racine
    if racine_registre is None and args.serveur:
        a = lire_json(f"{args.serveur}/ancrages/{seance}")
        racine_registre = a["corrections"][-1]["racine"] if a["corrections"] else a["racine"]

    taille, racine = recu["arbre"]["taille"], bytes.fromhex(recu["arbre"]["racine"])
    preuves_ok = all(
        verifier_inclusion(e["index"], taille, e["canonique"].encode(),
                           [bytes.fromhex(p) for p in e["preuve"]], racine)
        for e in recu["evenements"]
    )
    statut, nv, n = recalculer_statut(recu)
    controles = [
        ("Preuves d'inclusion", preuves_ok, f"{len(recu['evenements'])} événements"),
        ("Racine égale à celle du registre",
         racine_registre is not None and racine_registre == recu["arbre"]["racine"],
         racine_registre or "registre non consulté"),
        ("Signature du serveur", cle is not None and verifier_signature(recu, cle),
         "clé non fournie" if cle is None else ""),
        ("Statut recalculé", statut == recu["statut"] or recu["statut"] == "A_VERIFIER",
         f"{statut}, {nv}/{n} fenêtres"),
    ]
    print(f"Séance   {seance}")
    print(f"Titulaire {recu['titulaire']['matricule']}   statut annoncé : {recu['statut']}")
    print(f"Racine   {recu['arbre']['racine']}\n")
    for nom, ok, detail in controles:
        print(f"  {'OK   ' if ok else 'ÉCHEC'}  {nom}  {detail}")
    valide = all(ok for _, ok, _ in controles)
    print("\nReçu valide." if valide else "\nReçu NON valide.")
    return 0 if valide else 1


if __name__ == "__main__":
    sys.exit(main())
