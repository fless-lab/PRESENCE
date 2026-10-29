package ma.inpt.presence.reseau

import org.json.JSONObject

/** Un cours planifié (créneau) tel que le serveur le renvoie. */
data class Creneau(
    val id: Long,
    val cours: String,
    val intitule: String,
    val nomSalle: String,
    val enseignant: String,
    val debutMs: Long,
    val finMs: Long,
    val annule: Boolean,
) {
    val libelleCours: String get() = intitule.ifBlank { cours }
}

/** Valeurs d'un créneau avant ou après un changement. */
data class ValeursCreneau(val debutMs: Long, val finMs: Long, val salle: String)

/** Un changement du planning : création, déplacement, annulation ou rétablissement. */
data class ChangementCours(
    val id: Long,
    val type: String,
    val cours: String,
    val intitule: String,
    val avant: ValeursCreneau?,
    val apres: ValeursCreneau?,
    val motif: String,
    val horodatage: Long,
) {
    val libelleCours: String get() = intitule.ifBlank { cours }
}

object LecturePlanning {

    fun creneau(o: JSONObject): Creneau {
        val cours = o.optJSONObject("cours")
        val ens = o.optJSONObject("enseignant")
        val nomEns = listOfNotNull(ens?.texte("prenom"), ens?.texte("nom")).joinToString(" ")
        return Creneau(
            id = o.entier("id") ?: 0L,
            cours = cours?.texte("code") ?: "",
            intitule = cours?.texte("intitule") ?: "",
            nomSalle = o.optJSONObject("salle")?.texte("nom") ?: "",
            enseignant = nomEns,
            debutMs = o.entier("debut_ms") ?: 0L,
            finMs = o.entier("fin_ms") ?: 0L,
            annule = o.texte("etat").equals("ANNULE", ignoreCase = true),
        )
    }

    private fun valeurs(o: JSONObject?): ValeursCreneau? {
        if (o == null) return null
        val debut = o.entier("debut_ms") ?: return null
        val salle = o.optJSONObject("salle")?.texte("nom") ?: o.texte("salle_nom", "salle") ?: ""
        return ValeursCreneau(debut, o.entier("fin_ms") ?: 0L, salle)
    }

    fun changement(o: JSONObject): ChangementCours {
        val cours = o.optJSONObject("cours")
        return ChangementCours(
            id = o.entier("id") ?: 0L,
            type = o.texte("type") ?: "",
            cours = cours?.texte("code") ?: "",
            intitule = cours?.texte("intitule") ?: "",
            avant = valeurs(o.optJSONObject("avant")),
            apres = valeurs(o.optJSONObject("apres")),
            motif = o.texte("motif") ?: "",
            horodatage = o.entier("horodatage") ?: 0L,
        )
    }
}
