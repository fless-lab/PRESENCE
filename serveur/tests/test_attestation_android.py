import datetime

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID

from app.securite.attestation_android import OID_KEY_DESCRIPTION, verifier_chaine


def _tlv(t: int, v: bytes) -> bytes:
    assert len(v) < 128
    return bytes([t, len(v)]) + v


def _key_description(defi: bytes, niveau: int) -> bytes:
    corps = (
        _tlv(0x02, b"\x64")
        + _tlv(0x0A, bytes([niveau]))
        + _tlv(0x02, b"\x64")
        + _tlv(0x0A, bytes([niveau]))
        + _tlv(0x04, defi)
        + _tlv(0x04, b"")
    )
    return _tlv(0x30, corps)


def _cert(sujet, cle_publique, emetteur, cle_emetteur, extension=None):
    maintenant = datetime.datetime.now(datetime.UTC)
    b = (
        x509.CertificateBuilder()
        .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, sujet)]))
        .issuer_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, emetteur)]))
        .public_key(cle_publique)
        .serial_number(x509.random_serial_number())
        .not_valid_before(maintenant)
        .not_valid_after(maintenant + datetime.timedelta(days=1))
    )
    if extension is not None:
        b = b.add_extension(x509.UnrecognizedExtension(OID_KEY_DESCRIPTION, extension), False)
    return b.sign(cle_emetteur, hashes.SHA256()).public_bytes(serialization.Encoding.DER)


def _chaine(defi: bytes, niveau: int = 1):
    racine = ec.generate_private_key(ec.SECP256R1())
    appareil = ec.generate_private_key(ec.SECP256R1())
    spki = appareil.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    feuille = _cert("cle", appareil.public_key(), "racine", racine, _key_description(defi, niveau))
    rac = _cert("racine", racine.public_key(), "racine", racine)
    return [feuille, rac], spki


def test_chaine_valide(tmp_path):
    chaine, spki = _chaine(b"d" * 32)
    r = verifier_chaine(chaine, spki, b"d" * 32, tmp_path)
    assert r.statut == "VERIFIEE" and r.niveau == "TEE" and r.racine_reconnue is None


def test_defi_different_ou_cle_logicielle(tmp_path):
    chaine, spki = _chaine(b"d" * 32)
    assert verifier_chaine(chaine, spki, b"x" * 32, tmp_path).statut == "REFUSEE"
    chaine, spki = _chaine(b"d" * 32, niveau=0)
    assert "clé logicielle" in verifier_chaine(chaine, spki, b"d" * 32, tmp_path).raisons


def test_chaine_pour_une_autre_cle(tmp_path):
    chaine, _ = _chaine(b"d" * 32)
    _, autre = _chaine(b"d" * 32)
    assert verifier_chaine(chaine, autre, b"d" * 32, tmp_path).statut == "REFUSEE"


def test_absente(tmp_path):
    assert verifier_chaine([], b"", b"", tmp_path).statut == "ABSENTE"
