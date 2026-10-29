package ma.inpt.presence.reseau

/** Texte lu par l'étudiant ou l'enseignant quand un cours change. Sans dépendance Android. */
class MessageChangement(val titre: String, val corps: String)

object MessagesChangement {

    /**
     * [jour] et [heure] mettent en forme un instant (ex. « Ven. 30 oct. », « 14h00 »).
     * Le motif saisi par l'administration est toujours repris.
     */
    fun composer(c: ChangementCours, jour: (Long) -> String, heure: (Long) -> String): MessageChangement {
        val quand = c.apres?.let { "${jour(it.debutMs)} à ${heure(it.debutMs)}" }
        val titre = when (c.type.uppercase()) {
            "ANNULE" -> "Cours annulé : ${c.libelleCours}"
            "RETABLI" -> "Cours rétabli : ${c.libelleCours}"
            "CREE" -> "Nouveau cours : ${c.libelleCours}"
            else -> "Cours modifié : ${c.libelleCours}"
        }
        val details = mutableListOf<String>()
        when (c.type.uppercase()) {
            "ANNULE" -> c.avant?.let { details += "Prévu ${jour(it.debutMs)} à ${heure(it.debutMs)}" }
            "DEPLACE" -> {
                val a = c.avant
                val n = c.apres
                if (a != null && n != null) {
                    if (a.debutMs != n.debutMs || a.finMs != n.finMs) {
                        details += "${jour(a.debutMs)} ${heure(a.debutMs)} devient ${jour(n.debutMs)} ${heure(n.debutMs)}"
                    }
                    if (a.salle != n.salle && n.salle.isNotBlank()) details += "Salle ${n.salle}"
                } else if (quand != null) {
                    details += quand
                }
            }
            else -> {
                if (quand != null) details += quand
                c.apres?.salle?.takeIf { it.isNotBlank() }?.let { details += "Salle $it" }
            }
        }
        if (c.motif.isNotBlank()) details += "Motif : ${c.motif}"
        return MessageChangement(titre, details.joinToString(". "))
    }
}
