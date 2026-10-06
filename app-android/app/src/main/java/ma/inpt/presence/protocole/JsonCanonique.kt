package ma.inpt.presence.protocole

import java.math.BigDecimal
import java.math.BigInteger

/**
 * JSON canonique de PRESENCE : clés triées récursivement, aucun espace, UTF-8,
 * entiers uniquement. Produit la même sortie que
 * json.dumps(x, sort_keys=True, separators=(",", ":"), ensure_ascii=False) en Python.
 *
 * Valeurs acceptées : Map<String, *>, List<*>, String, Boolean, entiers, null.
 */
object JsonCanonique {

    fun encoder(valeur: Any?): String {
        val sb = StringBuilder()
        ecrire(sb, valeur)
        return sb.toString()
    }

    fun octets(valeur: Any?): ByteArray = encoder(valeur).toByteArray(Charsets.UTF_8)

    private fun ecrire(sb: StringBuilder, valeur: Any?) {
        when (valeur) {
            null -> sb.append("null")
            is String -> ecrireChaine(sb, valeur)
            is Boolean -> sb.append(if (valeur) "true" else "false")
            is Int, is Long, is Short, is Byte, is BigInteger -> sb.append(valeur.toString())
            is Double -> sb.append(entierDepuisDecimal(BigDecimal.valueOf(valeur)))
            is Float -> sb.append(entierDepuisDecimal(BigDecimal.valueOf(valeur.toDouble())))
            is BigDecimal -> sb.append(entierDepuisDecimal(valeur))
            is Map<*, *> -> {
                val cles = valeur.keys.map { cle ->
                    require(cle is String) { "Clé JSON non textuelle" }
                    cle
                }.sorted()
                sb.append('{')
                cles.forEachIndexed { i, cle ->
                    if (i > 0) sb.append(',')
                    ecrireChaine(sb, cle)
                    sb.append(':')
                    ecrire(sb, valeur[cle])
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                valeur.forEachIndexed { i, element ->
                    if (i > 0) sb.append(',')
                    ecrire(sb, element)
                }
                sb.append(']')
            }
            is Array<*> -> ecrire(sb, valeur.toList())
            else -> throw IllegalArgumentException("Type JSON non pris en charge : ${valeur::class.java.simpleName}")
        }
    }

    private fun entierDepuisDecimal(d: BigDecimal): String {
        val entier = try {
            d.toBigIntegerExact()
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("Nombre flottant interdit dans le JSON canonique")
        }
        return entier.toString()
    }

    private fun ecrireChaine(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') {
                    sb.append("\\u")
                    val hex = Integer.toHexString(c.code)
                    repeat(4 - hex.length) { sb.append('0') }
                    sb.append(hex)
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }
}
