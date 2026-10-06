package ma.inpt.presence.protocole

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Données fabricant de l'annonce : version (1) | salle (u16) | etat (1). */
data class Annonce(val version: Int, val salle: Int, val etat: Int)

/** Caractéristique info : version (1) | salle (u16) | seance (u32) | etat (1). */
data class InfoSalle(val version: Int, val salle: Int, val seance: Long, val etat: Int)

/** Caractéristique nonce : nonce (16) | numero_nonce (u32). */
class NonceSalle(val nonce: ByteArray, val numero: Long)

object Trames {

    private const val U32_MAX = 0xFFFF_FFFFL

    fun lireAnnonce(octets: ByteArray?): Annonce? {
        if (octets == null || octets.size < Constantes.TAILLE_ANNONCE) return null
        val b = ByteBuffer.wrap(octets).order(ByteOrder.BIG_ENDIAN)
        val version = b.get().toInt() and 0xFF
        val salle = b.short.toInt() and 0xFFFF
        val etat = b.get().toInt() and 0xFF
        return Annonce(version, salle, etat)
    }

    fun lireInfo(octets: ByteArray?): InfoSalle? {
        if (octets == null || octets.size < Constantes.TAILLE_INFO) return null
        val b = ByteBuffer.wrap(octets).order(ByteOrder.BIG_ENDIAN)
        val version = b.get().toInt() and 0xFF
        val salle = b.short.toInt() and 0xFFFF
        val seance = b.int.toLong() and U32_MAX
        val etat = b.get().toInt() and 0xFF
        return InfoSalle(version, salle, seance, etat)
    }

    fun lireNonce(octets: ByteArray?): NonceSalle? {
        if (octets == null || octets.size < Constantes.TAILLE_NONCE_CARACTERISTIQUE) return null
        val nonce = octets.copyOfRange(0, Constantes.TAILLE_NONCE)
        val b = ByteBuffer.wrap(octets, Constantes.TAILLE_NONCE, 4).order(ByteOrder.BIG_ENDIAN)
        val numero = b.int.toLong() and U32_MAX
        return NonceSalle(nonce, numero)
    }

    /**
     * Message signé par le téléphone :
     * "PRESENCE/v0.1/ATTEST" | salle (u16) | seance (u32) | nonce (16) | compteur (u32).
     */
    fun messageAttestation(salle: Int, seance: Long, nonce: ByteArray, compteur: Long): ByteArray {
        require(nonce.size == Constantes.TAILLE_NONCE) { "Nonce de taille incorrecte" }
        require(salle in 0..0xFFFF) { "Salle hors de l'intervalle u16" }
        require(seance in 0..U32_MAX) { "Séance hors de l'intervalle u32" }
        require(compteur in 0..U32_MAX) { "Compteur hors de l'intervalle u32" }
        val etiquette = Constantes.ETIQUETTE_ATTESTATION.toByteArray(Charsets.US_ASCII)
        val b = ByteBuffer.allocate(etiquette.size + 2 + 4 + Constantes.TAILLE_NONCE + 4)
            .order(ByteOrder.BIG_ENDIAN)
        b.put(etiquette)
        b.putShort(salle.toShort())
        b.putInt(seance.toInt())
        b.put(nonce)
        b.putInt(compteur.toInt())
        return b.array()
    }

    /** Trame écrite dans attest : id_appareil (8) | compteur (u32) | numero_nonce (u32) | signature DER. */
    fun trameAttestation(idAppareil: ByteArray, compteur: Long, numeroNonce: Long, signatureDer: ByteArray): ByteArray {
        require(idAppareil.size == Constantes.TAILLE_ID_APPAREIL) { "Identifiant d'appareil de taille incorrecte" }
        require(compteur in 0..U32_MAX) { "Compteur hors de l'intervalle u32" }
        require(numeroNonce in 0..U32_MAX) { "Numéro de nonce hors de l'intervalle u32" }
        val b = ByteBuffer.allocate(8 + 4 + 4 + signatureDer.size).order(ByteOrder.BIG_ENDIAN)
        b.put(idAppareil)
        b.putInt(compteur.toInt())
        b.putInt(numeroNonce.toInt())
        b.put(signatureDer)
        return b.array()
    }

    /**
     * Décision d'attestation à partir de l'état annoncé : séance active pour tous,
     * hors séance pour les enseignants seulement, jamais en pause ou après clôture.
     */
    fun doitAttester(etat: Int, enseignant: Boolean): Boolean = when (etat) {
        Constantes.ETAT_ACTIVE -> true
        Constantes.ETAT_AUCUNE -> enseignant
        else -> false
    }
}
