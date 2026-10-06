package ma.inpt.presence.ui

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import ma.inpt.presence.ui.theme.Palette

/** Mise en forme des dates, durées et statuts, en français. */
object Format {

    private val fr = Locale.FRENCH
    private val formatHeure = DateTimeFormatter.ofPattern("H'h'mm", fr)
    private val formatDate = DateTimeFormatter.ofPattern("EEE d MMM", fr)
    private val formatDateLongue = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", fr)
    private val formatHorodatage = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss", fr)

    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun heure(ms: Long): String =
        if (ms <= 0) "" else formatHeure.format(Instant.ofEpochMilli(ms).atZone(zone()))

    fun date(ms: Long): String =
        if (ms <= 0) "" else formatDate.format(Instant.ofEpochMilli(ms).atZone(zone())).replaceFirstChar { it.uppercase() }

    fun dateLongue(ms: Long): String =
        if (ms <= 0) "" else formatDateLongue.format(Instant.ofEpochMilli(ms).atZone(zone())).replaceFirstChar { it.uppercase() }

    fun horodatage(ms: Long): String =
        if (ms <= 0) "" else formatHorodatage.format(Instant.ofEpochMilli(ms).atZone(zone()))

    fun plage(debutMs: Long, finMs: Long): String = when {
        debutMs <= 0 -> ""
        finMs <= 0 -> "depuis ${heure(debutMs)}"
        else -> "${heure(debutMs)} à ${heure(finMs)}"
    }

    /** « il y a 2 min », « il y a 1 h 05 ». */
    fun ilYa(ms: Long, maintenant: Long = System.currentTimeMillis()): String {
        if (ms <= 0) return "jamais"
        val secondes = ((maintenant - ms) / 1000).coerceAtLeast(0)
        return when {
            secondes < 60 -> "il y a ${secondes} s"
            secondes < 3600 -> "il y a ${secondes / 60} min"
            secondes < 86_400 -> "il y a ${secondes / 3600} h ${"%02d".format(fr, (secondes % 3600) / 60)}"
            else -> "le ${date(ms)}"
        }
    }

    fun abrege(hex: String, debut: Int = 10, fin: Int = 6): String =
        if (hex.length <= debut + fin + 1) hex else "${hex.take(debut)}…${hex.takeLast(fin)}"

    fun libelleStatut(statut: String): String = when (statut.uppercase()) {
        "PRESENT" -> "Présent"
        "RETARD" -> "En retard"
        "DEPART_ANTICIPE" -> "Départ anticipé"
        "PARTIEL" -> "Partiel"
        "A_VERIFIER" -> "À vérifier"
        "ABSENT" -> "Absent"
        "NON_DETECTE" -> "Non détecté"
        "" -> "Sans statut"
        else -> statut.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun couleurStatut(statut: String): Color = when (statut.uppercase()) {
        "PRESENT" -> Palette.Present
        "RETARD", "DEPART_ANTICIPE" -> Palette.Retard
        "A_VERIFIER" -> Palette.AVerifier
        "ABSENT" -> Palette.Absent
        else -> Palette.Partiel
    }

    fun fondStatut(statut: String): Color = when (statut.uppercase()) {
        "PRESENT" -> Palette.PresentFond
        "RETARD", "DEPART_ANTICIPE" -> Palette.RetardFond
        "A_VERIFIER" -> Palette.AVerifierFond
        "ABSENT" -> Palette.AbsentFond
        else -> Palette.PartielFond
    }

    fun libelleEtat(etat: String): String = when (etat.uppercase()) {
        "ACTIVE" -> "En cours"
        "PAUSE" -> "En pause"
        "CLOTUREE" -> "Clôturée"
        "SCELLEE" -> "Scellée"
        "" -> ""
        else -> etat.lowercase().replaceFirstChar { it.uppercase() }
    }

    /** Statut visuel associé à un état de séance, pour réutiliser les pastilles. */
    fun statutEtat(etat: String): String = when (etat.uppercase()) {
        "ACTIVE" -> "PRESENT"
        "PAUSE" -> "RETARD"
        "SCELLEE" -> "A_VERIFIER"
        else -> "PARTIEL"
    }

    fun role(role: String): String = when (role.uppercase()) {
        "ENSEIGNANT" -> "Enseignant"
        "ETUDIANT" -> "Étudiant"
        else -> role.lowercase().replaceFirstChar { it.uppercase() }
    }
}
