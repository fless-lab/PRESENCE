"""Acteurs simulés pour les tests : téléphones et observateurs avec de vraies clés P-256."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import struct

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

ATTEST = b"PRESENCE/v0.1/ATTEST"
TEMOIN = b"PRESENCE/v0.1/TEMOIN"


def b64(d: bytes) -> str:
    return base64.b64encode(d).decode()


class Horloge:
    def __init__(self, depart: int = 1_791_270_000_000):
        self.t = depart

    def __call__(self) -> int:
        return self.t

    def avancer(self, ms: int) -> None:
        self.t += ms


class Signataire:
    def __init__(self):
        self.cle = ec.generate_private_key(ec.SECP256R1())
        self.spki = self.cle.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
        )
        self.spki_b64 = b64(self.spki)

    def signer(self, m: bytes) -> bytes:
        return self.cle.sign(m, ec.ECDSA(hashes.SHA256()))

    def entetes(self, ident: str, methode: str, chemin: str, corps: bytes, t: int) -> dict:
        chaine = "\n".join(
            ["PRESENCE/v0.1/REQ", methode, chemin, str(t), hashlib.sha256(corps).hexdigest()]
        ).encode()
        return {
            "X-Presence-Id": ident,
            "X-Presence-Horodatage": str(t),
            "X-Presence-Signature": b64(self.signer(chaine)),
            "Content-Type": "application/json",
        }


class Telephone(Signataire):
    def __init__(self, matricule: str):
        super().__init__()
        self.matricule = matricule
        self.id = hashlib.sha256(self.spki).digest()[:8].hex()
        self.compteur = 0

    def requete(self, client, horloge, methode: str, chemin: str, corps: dict | None = None):
        brut = json.dumps(corps).encode() if corps is not None else b""
        entetes = self.entetes(f"app:{self.id}", methode, chemin, brut, horloge())
        return client.request(methode, chemin, content=brut, headers=entetes)

    def enroler(self, client, code: str):
        defi = client.post("/enrolement/defi", json={"matricule": self.matricule}).json()["defi"]
        return client.post(
            "/enrolement",
            json={
                "matricule": self.matricule,
                "code": code,
                "cle_publique": self.spki_b64,
                "defi": defi,
                "chaine_attestation": [],
                "modele": "Simulé",
                "deverrouillage_recent": False,
            },
        )

    def trame(self, salle: int, seance: int, nonce: bytes, numero: int) -> bytes:
        self.compteur += 1
        m = ATTEST + struct.pack(">HI", salle, seance) + nonce + struct.pack(">I", self.compteur)
        return bytes.fromhex(self.id) + struct.pack(">II", self.compteur, numero) + self.signer(m)


class Observateur(Signataire):
    def __init__(self, nom: str, salle: int):
        super().__init__()
        self.nom, self.salle = nom, salle
        self.numero = 0
        self.nonce = os.urandom(16)

    def tourner(self) -> None:
        self.numero += 1
        self.nonce = os.urandom(16)

    def temoigner(self, tel: Telephone, seance: int, recu_ms: int) -> dict:
        trame = tel.trame(self.salle, seance, self.nonce, self.numero)
        m = (
            TEMOIN
            + struct.pack(">HI", self.salle, seance)
            + self.nonce
            + struct.pack(">IQ", self.numero, recu_ms)
            + trame
        )
        return {
            "seance": seance,
            "nonce": b64(self.nonce),
            "numero_nonce": self.numero,
            "recu_ms": recu_ms,
            "trame": b64(trame),
            "sig": b64(self.signer(m)),
        }

    def envoyer(self, client, horloge, temoignages: list[dict]):
        brut = json.dumps({"temoignages": temoignages}).encode()
        chemin = "/equipements/moi/temoignages"
        return client.post(
            chemin,
            content=brut,
            headers=self.entetes(f"equ:{self.nom}", "POST", chemin, brut, horloge()),
        )

    def config(self, client, horloge):
        chemin = "/equipements/moi/config"
        return client.get(
            chemin, headers=self.entetes(f"equ:{self.nom}", "GET", chemin, b"", horloge())
        )
