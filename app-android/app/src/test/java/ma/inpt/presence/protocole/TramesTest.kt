package ma.inpt.presence.protocole

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TramesTest {

    private val nonce = ByteArray(16) { it.toByte() }

    @Test
    fun messageAttestation() {
        val m = Trames.messageAttestation(salle = 204, seance = 17, nonce = nonce, compteur = 184)
        assertEquals(46, m.size)
        assertEquals(
            "50524553454e43452f76302e312f415454455354" + "00cc" + "00000011" +
                "000102030405060708090a0b0c0d0e0f" + "000000b8",
            Octets.versHex(m),
        )
    }

    @Test
    fun messageAttestationValeursMaximales() {
        val m = Trames.messageAttestation(0xFFFF, 0xFFFF_FFFFL, nonce, 0xFFFF_FFFFL)
        val hex = Octets.versHex(m)
        assertTrue(hex.endsWith("ffffffff"))
        assertEquals("ffff", hex.substring(40, 44))
        assertEquals("ffffffff", hex.substring(44, 52))
    }

    @Test
    fun trameAttestation() {
        val id = Octets.depuisHex("a7f3c1d09e2b4f60")
        val signature = ByteArray(71) { 0x30 }
        val trame = Trames.trameAttestation(id, compteur = 184, numeroNonce = 51, signatureDer = signature)
        assertEquals(8 + 4 + 4 + 71, trame.size)
        assertEquals("a7f3c1d09e2b4f60" + "000000b8" + "00000033", Octets.versHex(trame.copyOfRange(0, 16)))
        assertArrayEquals(signature, trame.copyOfRange(16, trame.size))
    }

    @Test
    fun lectureAnnonce() {
        val a = Trames.lireAnnonce(Octets.depuisHex("0100cc01"))!!
        assertEquals(1, a.version)
        assertEquals(204, a.salle)
        assertEquals(1, a.etat)
        assertNull(Trames.lireAnnonce(Octets.depuisHex("0100cc")))
        assertNull(Trames.lireAnnonce(null))
    }

    @Test
    fun lectureInfo() {
        val info = Trames.lireInfo(Octets.depuisHex("01" + "00cc" + "80000011" + "02"))!!
        assertEquals(1, info.version)
        assertEquals(204, info.salle)
        assertEquals(0x80000011L, info.seance)
        assertEquals(2, info.etat)
    }

    @Test
    fun lectureNonce() {
        val octets = nonce + Octets.depuisHex("00000033")
        val n = Trames.lireNonce(octets)!!
        assertArrayEquals(nonce, n.nonce)
        assertEquals(51L, n.numero)
        assertNull(Trames.lireNonce(nonce))
    }

    @Test
    fun decision() {
        assertTrue(Trames.doitAttester(Constantes.ETAT_ACTIVE, enseignant = false))
        assertTrue(Trames.doitAttester(Constantes.ETAT_ACTIVE, enseignant = true))
        assertFalse(Trames.doitAttester(Constantes.ETAT_AUCUNE, enseignant = false))
        assertTrue(Trames.doitAttester(Constantes.ETAT_AUCUNE, enseignant = true))
        assertFalse(Trames.doitAttester(Constantes.ETAT_PAUSE, enseignant = true))
        assertFalse(Trames.doitAttester(Constantes.ETAT_CLOTUREE, enseignant = true))
    }

    @Test
    fun idAppareil() {
        // 8 premiers octets de SHA-256 de la chaîne vide
        assertEquals("e3b0c44298fc1c14", Octets.idAppareil(ByteArray(0)))
    }
}
