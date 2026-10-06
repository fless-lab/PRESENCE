package ma.inpt.presence.reseau

import org.json.JSONArray
import org.json.JSONObject

/** Outils de lecture tolérante des réponses JSON. */
object Json {

    /** Convertit une valeur org.json en Map / List / valeurs simples, pour le JSON canonique. */
    fun versKotlin(valeur: Any?): Any? = when (valeur) {
        null -> null
        JSONObject.NULL -> null
        is JSONObject -> {
            val map = LinkedHashMap<String, Any?>()
            val cles = valeur.keys()
            while (cles.hasNext()) {
                val cle = cles.next()
                map[cle] = versKotlin(valeur.opt(cle))
            }
            map
        }
        is JSONArray -> (0 until valeur.length()).map { versKotlin(valeur.opt(it)) }
        else -> valeur
    }

    /** Liste d'objets : la réponse elle-même si c'est un tableau, sinon le premier champ tableau parmi [cles]. */
    fun listeObjets(reponse: Any?, vararg cles: String): List<JSONObject> {
        val tableau: JSONArray? = when (reponse) {
            is JSONArray -> reponse
            is JSONObject -> cles.firstNotNullOfOrNull { reponse.optJSONArray(it) }
            else -> null
        }
        if (tableau == null) return emptyList()
        return (0 until tableau.length()).mapNotNull { tableau.optJSONObject(it) }
    }
}

fun JSONObject.texte(vararg cles: String): String? {
    for (cle in cles) {
        if (!has(cle) || isNull(cle)) continue
        val v = opt(cle)
        if (v is JSONObject || v is JSONArray) continue
        val s = v.toString()
        if (s.isNotBlank()) return s
    }
    return null
}

fun JSONObject.entier(vararg cles: String): Long? {
    for (cle in cles) {
        if (!has(cle) || isNull(cle)) continue
        when (val v = opt(cle)) {
            is Number -> return v.toLong()
            is String -> v.toLongOrNull()?.let { return it }
        }
    }
    return null
}

fun JSONObject.booleen(vararg cles: String): Boolean? {
    for (cle in cles) {
        if (!has(cle) || isNull(cle)) continue
        when (val v = opt(cle)) {
            is Boolean -> return v
            is String -> if (v == "true" || v == "false") return v == "true"
            is Number -> return v.toInt() != 0
        }
    }
    return null
}

fun JSONObject.objet(vararg cles: String): JSONObject? {
    for (cle in cles) {
        optJSONObject(cle)?.let { return it }
    }
    return null
}
