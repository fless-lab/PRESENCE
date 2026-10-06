package ma.inpt.presence.protocole

import org.junit.Assert.assertEquals
import org.junit.Test

class RequeteSigneeTest {

    @Test
    fun corpsVide() {
        val chaine = RequeteSignee.chaineASigner("post", "/seances/S-1/pause", 1791278400000L, ByteArray(0))
        val attendu = "PRESENCE/v0.1/REQ\n" +
            "POST\n" +
            "/seances/S-1/pause\n" +
            "1791278400000\n" +
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(attendu, chaine)
    }

    @Test
    fun corpsJson() {
        val corps = "{\"cours\":\"SECRES\"}".toByteArray(Charsets.UTF_8)
        val chaine = RequeteSignee.chaineASigner("POST", "/seances", 42L, corps)
        assertEquals(
            "PRESENCE/v0.1/REQ\nPOST\n/seances\n42\nf4156cfb8d182a8fee6ea7e1c18e692211671fc5f5e65c42796609c2f6d087cb",
            chaine,
        )
    }

    @Test
    fun cheminAvecRequete() {
        val chaine = RequeteSignee.chaineASigner("GET", "/moi/historique?limite=20", 1L, ByteArray(0))
        assertEquals("/moi/historique?limite=20", chaine.split("\n")[2])
        assertEquals(5, chaine.split("\n").size)
    }

    @Test
    fun identifiant() {
        assertEquals("app:a7f3c1d09e2b4f60", RequeteSignee.identifiant("a7f3c1d09e2b4f60"))
    }
}
