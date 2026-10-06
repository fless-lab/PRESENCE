package ma.inpt.presence.reseau

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modèles lus dans les réponses du serveur. La lecture est tolérante :
 * les champs absents prennent une valeur neutre, plusieurs noms sont acceptés.
 */

data class Fenetres(
    val total: Int,
    val nbValidees: Int,
    /** Indices des fenêtres validées, si le serveur les fournit. */
    val indices: Set<Int>?,
)

data class Seance(
    val id: String,
    val cours: String,
    val intitule: String,
    val salle: Int,
    val nomSalle: String,
    val etat: String,
    val debutMs: Long,
    val finMs: Long,
    val statut: String,
    val fenetres: Fenetres?,
    val racine: String,
    val transaction: String,
) {
    val libelleCours: String get() = intitule.ifBlank { cours }
    val libelleSalle: String get() = nomSalle.ifBlank { if (salle > 0) salle.toString() else "" }
}

data class Cours(
    val code: String,
    val intitule: String,
    val groupe: String,
    val inscrits: Int,
)

data class PresenceEnseignant(
    val salle: Int,
    val nomSalle: String,
    val depuisMs: Long,
) {
    val libelleSalle: String get() = nomSalle.ifBlank { salle.toString() }
}

data class Inscrit(
    val matricule: String,
    val nom: String,
    val prenom: String,
    val statut: String,
    val fenetresValidees: Int,
    val manuel: Boolean,
) {
    val nomComplet: String get() = listOf(prenom, nom).filter { it.isNotBlank() }.joinToString(" ").ifBlank { matricule }
}

data class Direct(
    val seance: Seance,
    val inscrits: List<Inscrit>,
)

data class Scellement(
    val racine: String,
    val transaction: String,
    val registre: String,
)

object Lecture {

    fun fenetres(o: JSONObject?): Fenetres? {
        if (o == null) return null
        val liste = o.optJSONArray("liste") ?: o.optJSONArray("detail")
        if (liste != null && liste.length() > 0 && liste.opt(0) is Boolean) {
            val indices = (0 until liste.length()).filter { liste.optBoolean(it) }.toSet()
            return Fenetres(liste.length(), indices.size, indices)
        }
        val total = (o.entier("total", "nombre", "n") ?: 0L).toInt()
        val validees = o.opt("validees")
        return when (validees) {
            is JSONArray -> {
                val indices = (0 until validees.length()).mapNotNull { i ->
                    when (val v = validees.opt(i)) {
                        is Number -> v.toInt()
                        else -> null
                    }
                }.toSet()
                Fenetres(total, indices.size, indices)
            }
            is Number -> Fenetres(total, validees.toInt(), null)
            else -> Fenetres(total, 0, null)
        }
    }

    fun seance(o: JSONObject): Seance {
        val coursObjet = o.optJSONObject("cours")
        val salleObjet = o.optJSONObject("salle")
        val ancrage = o.optJSONObject("ancrage")
        val fenetresObjet = o.optJSONObject("fenetres")
        val fenetres = when {
            fenetresObjet != null -> fenetres(fenetresObjet)
            o.optJSONArray("fenetres") != null -> fenetres(JSONObject().put("liste", o.optJSONArray("fenetres")))
            else -> null
        }
        return Seance(
            id = o.texte("id", "seance", "seance_id") ?: "",
            cours = coursObjet?.texte("code") ?: o.texte("cours", "code_cours") ?: "",
            intitule = coursObjet?.texte("intitule") ?: o.texte("intitule", "cours_intitule") ?: "",
            salle = (salleObjet?.entier("id", "numero") ?: o.entier("salle")) ?.toInt() ?: 0,
            nomSalle = salleObjet?.texte("nom") ?: o.texte("nom_salle") ?: "",
            etat = o.texte("etat") ?: "",
            debutMs = o.entier("debut_ms", "debut") ?: 0L,
            finMs = o.entier("fin_ms", "fin") ?: 0L,
            statut = o.texte("statut", "statut_provisoire") ?: "",
            fenetres = fenetres,
            racine = o.texte("racine") ?: "",
            transaction = ancrage?.texte("transaction") ?: o.texte("transaction", "ancrage") ?: "",
        )
    }

    /**
     * Séance éventuellement enveloppée : {seance: {...}, statut, fenetres}.
     * Les champs de l'enveloppe complètent ceux de la séance.
     */
    fun seanceEnveloppee(o: JSONObject): Seance {
        val interne = o.optJSONObject("seance")
        if (interne == null || o.has("id")) return seance(o)
        val s = seance(interne)
        val e = seance(o)
        return s.copy(
            cours = s.cours.ifBlank { e.cours },
            intitule = s.intitule.ifBlank { e.intitule },
            salle = if (s.salle != 0) s.salle else e.salle,
            nomSalle = s.nomSalle.ifBlank { e.nomSalle },
            etat = s.etat.ifBlank { e.etat },
            debutMs = if (s.debutMs != 0L) s.debutMs else e.debutMs,
            statut = s.statut.ifBlank { e.statut },
            fenetres = s.fenetres ?: e.fenetres,
            racine = s.racine.ifBlank { e.racine },
            transaction = s.transaction.ifBlank { e.transaction },
        )
    }

    fun cours(o: JSONObject): Cours = Cours(
        code = o.texte("code", "cours", "id") ?: "",
        intitule = o.texte("intitule", "nom") ?: "",
        groupe = o.texte("groupe") ?: "",
        inscrits = (o.entier("inscrits", "nb_inscrits") ?: 0L).toInt(),
    )

    fun presence(o: JSONObject?): PresenceEnseignant? {
        if (o == null) return null
        val salle = o.entier("salle") ?: return null
        return PresenceEnseignant(
            salle = salle.toInt(),
            nomSalle = o.texte("nom_salle") ?: "",
            depuisMs = o.entier("depuis_ms") ?: 0L,
        )
    }

    fun inscrit(o: JSONObject): Inscrit {
        val fenetres = o.optJSONObject("fenetres")
        val validees = o.entier("fenetres_validees") ?: fenetres?.let { f ->
            f.optJSONArray("validees")?.length()?.toLong() ?: f.entier("validees")
        } ?: 0L
        return Inscrit(
            matricule = o.texte("matricule") ?: "",
            nom = o.texte("nom") ?: "",
            prenom = o.texte("prenom") ?: "",
            statut = o.texte("statut", "statut_provisoire") ?: "ABSENT",
            fenetresValidees = validees.toInt(),
            manuel = o.booleen("manuel", "validation_manuelle") ?: false,
        )
    }

    fun direct(o: JSONObject): Direct {
        val inscrits = Json.listeObjets(o, "inscrits", "etudiants").map { inscrit(it) }
        return Direct(seanceEnveloppee(o), inscrits)
    }

    fun scellement(o: JSONObject?): Scellement {
        val ancrage = o?.optJSONObject("ancrage")
        return Scellement(
            racine = o?.texte("racine") ?: "",
            transaction = ancrage?.texte("transaction") ?: o?.texte("ancrage", "transaction") ?: "",
            registre = ancrage?.texte("registre") ?: "",
        )
    }
}
