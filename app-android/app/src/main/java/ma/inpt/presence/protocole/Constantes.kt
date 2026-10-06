package ma.inpt.presence.protocole

import java.util.UUID

/**
 * Valeurs reprises de protocole/constantes.yaml (version 0.1).
 * Toute modification doit suivre ce fichier source.
 */
object Constantes {
    const val VERSION_PROTOCOLE = "0.1"
    const val VERSION_TRAME = 0x01

    val SERVICE_UUID: UUID = UUID.fromString("05c39e32-50d8-4214-9d1c-9ecb2c8f9364")
    val CARACTERISTIQUE_INFO: UUID = UUID.fromString("d6bed148-c226-4d44-a6fc-b1c2242f8cce")
    val CARACTERISTIQUE_NONCE: UUID = UUID.fromString("32f15e73-099d-475c-b214-2a497b29ce2d")
    val CARACTERISTIQUE_ATTEST: UUID = UUID.fromString("47ed4924-b297-4e43-8e4c-91b5665dfb9c")

    const val MTU_DEMANDE = 185
    const val ID_FABRICANT = 0xFFFF

    const val TAILLE_NONCE = 16
    const val TAILLE_ID_APPAREIL = 8
    const val TAILLE_INFO = 8
    const val TAILLE_NONCE_CARACTERISTIQUE = 20
    const val TAILLE_ANNONCE = 4

    const val ETIQUETTE_ATTESTATION = "PRESENCE/v0.1/ATTEST"
    const val ETIQUETTE_REQUETE = "PRESENCE/v0.1/REQ"

    const val ETAT_AUCUNE = 0
    const val ETAT_ACTIVE = 1
    const val ETAT_PAUSE = 2
    const val ETAT_CLOTUREE = 3

    /** Au plus une attestation par observateur pendant cet intervalle. */
    const val INTERVALLE_ATTESTATION_MS = 40_000L

    /** Durée maximale d'un échange GATT complet. */
    const val DELAI_GATT_MS = 8_000L

    const val DUREE_FENETRE_MS = 300_000L
}
