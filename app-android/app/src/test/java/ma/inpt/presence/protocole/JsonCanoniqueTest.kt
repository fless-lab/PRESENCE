package ma.inpt.presence.protocole

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonCanoniqueTest {

    @Test
    fun objetImbriqueCommePython() {
        val objet = linkedMapOf<String, Any?>(
            "z" to 1,
            "a" to linkedMapOf(
                "y" to listOf(3, linkedMapOf("c" to null, "b" to true)),
                "x" to "é\"\\\n\t\u0001/",
            ),
            "m" to 1791289000000L,
            "k" to false,
            "L" to "Zz",
        )
        // Sortie de json.dumps(objet, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        val attendu = "{\"L\":\"Zz\",\"a\":{\"x\":\"é\\\"\\\\\\n\\t\\u0001/\",\"y\":[3,{\"b\":true,\"c\":null}]}," +
            "\"k\":false,\"m\":1791289000000,\"z\":1}"
        assertEquals(attendu, JsonCanonique.encoder(objet))
    }

    @Test
    fun decimalEntierAccepte() {
        assertEquals("{\"n\":28}", JsonCanonique.encoder(mapOf("n" to 28.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun flottantRefuse() {
        JsonCanonique.encoder(mapOf("n" to 0.5))
    }

    @Test
    fun utf8() {
        val octets = JsonCanonique.octets(mapOf("nom" to "Séance"))
        assertEquals("{\"nom\":\"Séance\"}", String(octets, Charsets.UTF_8))
        assertEquals(17, octets.size)
    }
}
