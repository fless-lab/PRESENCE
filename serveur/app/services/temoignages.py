"""Traitement des témoignages envoyés par les observateurs.

Chaque témoignage contient la trame écrite par le téléphone et la signature de
l'observateur. Le serveur revérifie les deux signatures avant d'enregistrer
l'événement ATTESTATION : un observateur compromis ne peut pas fabriquer une
attestation sans la clé du téléphone.
"""

from __future__ import annotations

import base64
import struct

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app import horloge
from app.constantes import ETIQUETTE_ATTEST, ETIQUETTE_TEMOIN
from app.modeles import Appareil, Cours, Equipement, Evenement, Inscription, Seance
from app.securite.crypto import b64, verifier
from app.services import evenements

AVANCE_MAX_MS = 60_000
RETARD_MAX_MS = 6 * 3600 * 1000


class Rejet(Exception):
    pass


def message_temoin(
    salle: int, seance: int, nonce: bytes, numero: int, recu_ms: int, trame: bytes
) -> bytes:
    return (
        ETIQUETTE_TEMOIN
        + struct.pack(">HI", salle, seance)
        + nonce
        + struct.pack(">IQ", numero, recu_ms)
        + trame
    )


def message_attest(salle: int, seance: int, nonce: bytes, compteur: int) -> bytes:
    return (
        ETIQUETTE_ATTEST + struct.pack(">HI", salle, seance) + nonce + struct.pack(">I", compteur)
    )


def _b64(texte: str) -> bytes:
    return base64.b64decode(texte, validate=True)


def traiter_un(db: Session, equipement: Equipement, t: dict) -> Evenement:
    try:
        numero_seance = int(t["seance"])
        nonce = _b64(t["nonce"])
        numero_nonce = int(t["numero_nonce"])
        recu_ms = int(t["recu_ms"])
        trame = _b64(t["trame"])
        sig_temoin = _b64(t["sig"])
    except (KeyError, ValueError, TypeError) as exc:
        raise Rejet("format invalide") from exc
    if len(nonce) != 16 or len(trame) < 16 + 8:
        raise Rejet("tailles invalides")

    salle = equipement.salle_id
    maintenant = horloge.maintenant_ms()
    if recu_ms > maintenant + AVANCE_MAX_MS or recu_ms < maintenant - RETARD_MAX_MS:
        raise Rejet("horodatage hors limites")
    if not verifier(
        equipement.cle_publique,
        message_temoin(salle, numero_seance, nonce, numero_nonce, recu_ms, trame),
        sig_temoin,
    ):
        raise Rejet("signature de l'observateur invalide")

    id_app = trame[:8].hex()
    compteur, numero_trame = struct.unpack(">II", trame[8:16])
    sig_app = trame[16:]
    if numero_trame != numero_nonce:
        raise Rejet("numéro de nonce incohérent")

    appareil = db.get(Appareil, id_app)
    if appareil is None or not appareil.actif:
        raise Rejet("appareil inconnu ou révoqué")
    if not verifier(
        appareil.cle_publique, message_attest(salle, numero_seance, nonce, compteur), sig_app
    ):
        raise Rejet("signature du téléphone invalide")

    seance: Seance | None = None
    if numero_seance == 0:
        if appareil.personne.role != "ENSEIGNANT":
            raise Rejet("hors séance, seuls les enseignants s'attestent")
    else:
        seance = db.scalar(select(Seance).where(Seance.numero == numero_seance))
        if seance is None or seance.salle_id != salle:
            raise Rejet("séance inconnue dans cette salle")
        if seance.etat not in ("ACTIVE", "PAUSE"):
            raise Rejet("séance fermée")
        if recu_ms < seance.debut_ms - AVANCE_MAX_MS:
            raise Rejet("attestation antérieure à la séance")
        est_enseignant = db.get(Cours, seance.cours_id).enseignant_id == appareil.personne_id
        inscrit = db.get(Inscription, (seance.cours_id, appareil.personne_id)) is not None
        if not (inscrit or est_enseignant):
            raise Rejet("non inscrit à ce cours")

    try:
        with db.begin_nested():
            return evenements.enregistrer(
                db,
                seance=seance,
                type="ATTESTATION",
                auteur=f"obs:{equipement.nom}",
                horodatage=recu_ms,
                contenu={
                    "appareil": id_app,
                    "compteur": compteur,
                    "numero_nonce": numero_nonce,
                    "nonce": b64(nonce),
                },
                preuves={"trame": b64(trame), "sig_temoin": b64(sig_temoin)},
                salle_id=salle,
                appareil=id_app,
                compteur=compteur,
            )
    except IntegrityError as exc:
        raise Rejet("attestation déjà reçue (compteur rejoué)") from exc


def traiter(db: Session, equipement: Equipement, temoignages: list[dict]) -> dict:
    acceptes, rejetes = 0, []
    for i, t in enumerate(temoignages):
        try:
            traiter_un(db, equipement, t)
            acceptes += 1
        except Rejet as r:
            rejetes.append({"index": i, "raison": str(r)})
    return {"acceptes": acceptes, "rejetes": rejetes}
