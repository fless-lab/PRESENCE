package ma.inpt.presence.protocole

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerkleTest {

    private fun feuilles(n: Int): List<ByteArray> =
        (0 until n).map { "evenement-$it".toByteArray(Charsets.UTF_8) }

    @Test
    fun racineIdentiqueAuServeur() {
        // Valeur calculée par serveur/app/domaine/merkle.py sur les mêmes feuilles.
        val racine = Merkle.racine(feuilles(5))
        assertEquals("2f21da4ca5aa2cd8e5e4537ebaf3272f05223b8ccd57047b6f298a8bdb63f69a", Octets.versHex(racine))
    }

    @Test
    fun preuveIdentiqueAuServeur() {
        val preuve = Merkle.preuveInclusion(4, feuilles(5)).map { Octets.versHex(it) }
        assertEquals(listOf("63ae79445e22a17608cd3e28a988dc2454a6b97cababc03d65470b05b417d9b4"), preuve)
    }

    @Test
    fun toutesLesPreuvesSontValides() {
        val f = feuilles(5)
        val racine = Merkle.racine(f)
        for (i in f.indices) {
            val preuve = Merkle.preuveInclusion(i, f)
            assertTrue("feuille $i", Merkle.verifierInclusion(i.toLong(), 5, f[i], preuve, racine))
        }
    }

    @Test
    fun preuvesValidesPourToutesLesTailles() {
        for (n in 1..33) {
            val f = feuilles(n)
            val racine = Merkle.racine(f)
            for (i in 0 until n) {
                assertTrue(Merkle.verifierInclusion(i.toLong(), n.toLong(), f[i], Merkle.preuveInclusion(i, f), racine))
            }
        }
    }

    @Test
    fun feuilleModifieeRejetee() {
        val f = feuilles(5)
        val racine = Merkle.racine(f)
        for (i in f.indices) {
            val preuve = Merkle.preuveInclusion(i, f)
            val falsifiee = "evenement-$i ".toByteArray(Charsets.UTF_8)
            assertFalse(Merkle.verifierInclusion(i.toLong(), 5, falsifiee, preuve, racine))
        }
    }

    @Test
    fun mauvaisIndexRejete() {
        val f = feuilles(5)
        val racine = Merkle.racine(f)
        assertFalse(Merkle.verifierInclusion(3, 5, f[2], Merkle.preuveInclusion(2, f), racine))
        assertFalse(Merkle.verifierInclusion(5, 5, f[4], Merkle.preuveInclusion(4, f), racine))
    }

    @Test
    fun uneFeuille() {
        val f = feuilles(1)
        assertArrayEquals(Merkle.hacherFeuille(f[0]), Merkle.racine(f))
        assertTrue(Merkle.verifierInclusion(0, 1, f[0], emptyList(), Merkle.racine(f)))
    }
}
