package ma.inpt.presence.protocole

/**
 * Arbre de Merkle du RFC 6962 / RFC 9162, identique à serveur/app/domaine/merkle.py.
 * Feuille : SHA-256(0x00 | données). Nœud : SHA-256(0x01 | gauche | droite).
 */
object Merkle {

    fun hacherFeuille(donnees: ByteArray): ByteArray = Octets.sha256(byteArrayOf(0x00) + donnees)

    fun hacherNoeud(gauche: ByteArray, droite: ByteArray): ByteArray =
        Octets.sha256(byteArrayOf(0x01) + gauche + droite)

    private fun decoupe(n: Int): Int {
        var k = 1
        while (k * 2 < n) k *= 2
        return k
    }

    fun racine(feuilles: List<ByteArray>): ByteArray {
        val n = feuilles.size
        if (n == 0) return Octets.sha256(ByteArray(0))
        if (n == 1) return hacherFeuille(feuilles[0])
        val k = decoupe(n)
        return hacherNoeud(racine(feuilles.subList(0, k)), racine(feuilles.subList(k, n)))
    }

    fun preuveInclusion(index: Int, feuilles: List<ByteArray>): List<ByteArray> {
        val n = feuilles.size
        require(index in 0 until n) { "Index hors de l'arbre" }
        if (n == 1) return emptyList()
        val k = decoupe(n)
        return if (index < k) {
            preuveInclusion(index, feuilles.subList(0, k)) + listOf(racine(feuilles.subList(k, n)))
        } else {
            preuveInclusion(index - k, feuilles.subList(k, n)) + listOf(racine(feuilles.subList(0, k)))
        }
    }

    /**
     * Vérification d'une preuve d'inclusion (RFC 9162, section 2.1.3.2).
     * [feuille] est la donnée brute (JSON canonique en UTF-8), hachée ici.
     */
    fun verifierInclusion(
        index: Long,
        taille: Long,
        feuille: ByteArray,
        preuve: List<ByteArray>,
        racineAttendue: ByteArray,
    ): Boolean {
        if (index < 0 || index >= taille) return false
        var fn = index
        var sn = taille - 1
        var r = hacherFeuille(feuille)
        for (p in preuve) {
            if (sn == 0L) return false
            if (fn and 1L == 1L || fn == sn) {
                r = hacherNoeud(p, r)
                if (fn and 1L == 0L) {
                    while (fn and 1L == 0L && fn != 0L) {
                        fn = fn shr 1
                        sn = sn shr 1
                    }
                }
            } else {
                r = hacherNoeud(r, p)
            }
            fn = fn shr 1
            sn = sn shr 1
        }
        return sn == 0L && Octets.egalesEnTempsConstant(r, racineAttendue)
    }
}
