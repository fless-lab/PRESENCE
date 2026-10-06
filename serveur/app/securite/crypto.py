"""Outils cryptographiques : clés P-256, signatures DER, identifiants, clé du serveur."""

from __future__ import annotations

import base64
import hashlib
from pathlib import Path

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


def b64(donnees: bytes) -> str:
    return base64.b64encode(donnees).decode("ascii")


def de_b64(texte: str) -> bytes:
    return base64.b64decode(texte, validate=True)


def sha256(donnees: bytes) -> bytes:
    return hashlib.sha256(donnees).digest()


def sha256_hex(donnees: bytes) -> str:
    return hashlib.sha256(donnees).hexdigest()


def id_appareil(spki_der: bytes) -> str:
    """8 premiers octets de SHA-256(SPKI DER), en hexadécimal."""
    return sha256(spki_der)[:8].hex()


def charger_cle_publique(spki_b64: str) -> ec.EllipticCurvePublicKey:
    cle = serialization.load_der_public_key(de_b64(spki_b64))
    if not isinstance(cle, ec.EllipticCurvePublicKey) or not isinstance(cle.curve, ec.SECP256R1):
        raise ValueError("clé P-256 attendue")
    return cle


def verifier(cle: ec.EllipticCurvePublicKey | str, message: bytes, signature: bytes) -> bool:
    if isinstance(cle, str):
        cle = charger_cle_publique(cle)
    try:
        cle.verify(signature, message, ec.ECDSA(hashes.SHA256()))
        return True
    except (InvalidSignature, ValueError):
        return False


def spki(cle: ec.EllipticCurvePublicKey) -> bytes:
    return cle.public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )


class CleServeur:
    """Clé P-256 du serveur, créée au premier lancement. Elle signe les reçus."""

    def __init__(self, dossier: Path):
        dossier.mkdir(parents=True, exist_ok=True)
        chemin = dossier / "cle_serveur.pem"
        if chemin.exists():
            cle = serialization.load_pem_private_key(chemin.read_bytes(), password=None)
            assert isinstance(cle, ec.EllipticCurvePrivateKey)
        else:
            cle = ec.generate_private_key(ec.SECP256R1())
            chemin.write_bytes(
                cle.private_bytes(
                    serialization.Encoding.PEM,
                    serialization.PrivateFormat.PKCS8,
                    serialization.NoEncryption(),
                )
            )
            chemin.chmod(0o600)
        self._cle = cle

    def signer(self, message: bytes) -> bytes:
        return self._cle.sign(message, ec.ECDSA(hashes.SHA256()))

    @property
    def publique_b64(self) -> str:
        return b64(spki(self._cle.public_key()))
