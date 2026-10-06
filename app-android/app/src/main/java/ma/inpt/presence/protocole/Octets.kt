package ma.inpt.presence.protocole

import java.security.MessageDigest

object Octets {
    private val CHIFFRES = "0123456789abcdef".toCharArray()

    fun versHex(octets: ByteArray): String {
        val sortie = CharArray(octets.size * 2)
        for (i in octets.indices) {
            val v = octets[i].toInt() and 0xFF
            sortie[2 * i] = CHIFFRES[v ushr 4]
            sortie[2 * i + 1] = CHIFFRES[v and 0x0F]
        }
        return String(sortie)
    }

    fun depuisHex(hex: String): ByteArray {
        val propre = hex.trim().lowercase()
        require(propre.length % 2 == 0) { "Longueur hexadécimale impaire" }
        return ByteArray(propre.length / 2) { i ->
            val haut = Character.digit(propre[2 * i], 16)
            val bas = Character.digit(propre[2 * i + 1], 16)
            require(haut >= 0 && bas >= 0) { "Caractère hexadécimal invalide" }
            ((haut shl 4) or bas).toByte()
        }
    }

    fun sha256(donnees: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(donnees)

    fun sha256Hex(donnees: ByteArray): String = versHex(sha256(donnees))

    /** Identifiant d'appareil : 8 premiers octets de SHA-256(SPKI DER), en hexadécimal. */
    fun idAppareil(spkiDer: ByteArray): String = versHex(sha256(spkiDer).copyOfRange(0, 8))

    fun egalesEnTempsConstant(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
}
