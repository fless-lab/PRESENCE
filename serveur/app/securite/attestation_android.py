"""Vérification de l'attestation de clé Android (Key Attestation).

La chaîne de certificats fournie par le Keystore prouve que la clé a été créée
dans le matériel sécurisé du téléphone, avec le défi envoyé par le serveur.

Vérifications faites ici :
- chaque certificat est signé par le suivant, le dernier est auto-signé ;
- la clé publique du premier certificat est celle que le téléphone enregistre ;
- l'extension KeyDescription contient le défi du serveur ;
- le niveau de sécurité est l'environnement sécurisé (TEE) ou StrongBox.

La racine est comparée aux racines Google de confiance si un fichier
`racines_attestation.pem` est présent dans le dossier de données. Sans ce fichier,
l'empreinte de la racine est enregistrée pour un contrôle ultérieur.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization

OID_KEY_DESCRIPTION = x509.ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17")
NIVEAUX = {0: "LOGICIEL", 1: "TEE", 2: "STRONGBOX"}


@dataclass
class ResultatAttestation:
    statut: str  # VERIFIEE, ABSENTE, REFUSEE
    niveau: str | None = None
    racine_empreinte: str | None = None
    racine_reconnue: bool | None = None
    raisons: list[str] = field(default_factory=list)

    def detail(self) -> dict:
        return {
            "niveau": self.niveau,
            "racine_empreinte": self.racine_empreinte,
            "racine_reconnue": self.racine_reconnue,
            "raisons": self.raisons,
        }


def _lire_tlv(d: bytes, i: int) -> tuple[int, bytes, int]:
    """Lit un élément DER : renvoie (étiquette, contenu, position suivante)."""
    etiquette = d[i]
    i += 1
    longueur = d[i]
    i += 1
    if longueur & 0x80:
        n = longueur & 0x7F
        longueur = int.from_bytes(d[i : i + n], "big")
        i += n
    return etiquette, d[i : i + longueur], i + longueur


def lire_key_description(der: bytes) -> tuple[int, bytes]:
    """Renvoie (niveau de sécurité de l'attestation, défi) depuis KeyDescription."""
    etiquette, sequence, _ = _lire_tlv(der, 0)
    if etiquette != 0x30:
        raise ValueError("KeyDescription : SEQUENCE attendue")
    i = 0
    _, _, i = _lire_tlv(sequence, i)  # attestationVersion
    _, niveau, i = _lire_tlv(sequence, i)  # attestationSecurityLevel
    _, _, i = _lire_tlv(sequence, i)  # keyMintVersion
    _, _, i = _lire_tlv(sequence, i)  # keyMintSecurityLevel
    etiquette, defi, i = _lire_tlv(sequence, i)  # attestationChallenge
    if etiquette != 0x04:
        raise ValueError("KeyDescription : défi attendu")
    return int.from_bytes(niveau, "big"), defi


def _racines_connues(dossier: Path) -> set[bytes] | None:
    chemin = dossier / "racines_attestation.pem"
    if not chemin.exists():
        return None
    certs = x509.load_pem_x509_certificates(chemin.read_bytes())
    return {
        c.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
        )
        for c in certs
    }


def verifier_chaine(
    chaine_der: list[bytes], spki_attendu: bytes, defi: bytes, dossier: Path
) -> ResultatAttestation:
    if not chaine_der:
        return ResultatAttestation("ABSENTE", raisons=["aucune chaîne fournie"])
    try:
        certs = [x509.load_der_x509_certificate(c) for c in chaine_der]
    except ValueError:
        return ResultatAttestation("REFUSEE", raisons=["certificat illisible"])

    r = ResultatAttestation("VERIFIEE")
    feuille = certs[0]
    spki_feuille = feuille.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    if spki_feuille != spki_attendu:
        r.raisons.append("la chaîne ne certifie pas cette clé")

    for enfant, parent in zip(certs, certs[1:], strict=False):
        try:
            enfant.verify_directly_issued_by(parent)
        except (ValueError, TypeError, Exception):  # noqa: BLE001
            r.raisons.append("chaîne de signatures rompue")
            break
    racine = certs[-1]
    try:
        racine.verify_directly_issued_by(racine)
    except Exception:  # noqa: BLE001
        r.raisons.append("racine non auto-signée")
    spki_racine = racine.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    r.racine_empreinte = racine.fingerprint(hashes.SHA256()).hex()
    connues = _racines_connues(dossier)
    if connues is not None:
        r.racine_reconnue = spki_racine in connues
        if not r.racine_reconnue:
            r.raisons.append("racine inconnue")

    try:
        ext = feuille.extensions.get_extension_for_oid(OID_KEY_DESCRIPTION)
        niveau, defi_certifie = lire_key_description(ext.value.value)
        r.niveau = NIVEAUX.get(niveau, str(niveau))
        if defi_certifie != defi:
            r.raisons.append("défi différent")
        if niveau == 0:
            r.raisons.append("clé logicielle")
    except x509.ExtensionNotFound:
        r.raisons.append("extension d'attestation absente")
    except (ValueError, IndexError):
        r.raisons.append("extension d'attestation illisible")

    if r.raisons:
        r.statut = "REFUSEE"
    return r
