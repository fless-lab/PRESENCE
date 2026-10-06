package ma.inpt.presence.donnees

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Une tentative d'attestation, telle qu'écrite dans le journal de l'expérience 01. */
data class EntreeJournal(
    val horodatage: Long,
    val observateur: String,
    val salle: Int,
    val seance: Long,
    val numeroNonce: Long,
    val resultat: String,
    val erreur: String,
) {
    val reussie: Boolean get() = resultat == RESULTAT_OK

    fun versCsv(): String = listOf(
        horodatage.toString(),
        observateur,
        salle.toString(),
        seance.toString(),
        numeroNonce.toString(),
        resultat,
        nettoyer(erreur),
    ).joinToString(",")

    companion object {
        const val RESULTAT_OK = "OK"
        const val RESULTAT_ECHEC = "ECHEC"
        const val RESULTAT_IGNORE = "IGNORE"

        const val ENTETE_CSV = "horodatage_ms,observateur,salle,seance,numero_nonce,resultat,erreur"

        private fun nettoyer(texte: String): String =
            texte.replace(',', ';').replace('\n', ' ').replace('\r', ' ').take(160)
    }
}

/** État courant de l'attestation, partagé entre le service et l'interface. */
data class EtatAttestation(
    val serviceActif: Boolean = false,
    val rechercheActive: Boolean = false,
    val derniereReussiteMs: Long = 0L,
    val derniereSalle: Int = 0,
    val reussies: Int = 0,
    val echecs: Int = 0,
    val dernierProbleme: String = "",
)

/**
 * Journal local : anneau en mémoire et fichier CSV dans filesDir.
 * Accessible depuis le service et l'interface.
 */
object Journal {

    private const val TAILLE_ANNEAU = 200
    private const val NOM_FICHIER = "journal.csv"
    private const val NOM_ARCHIVE = "journal.1.csv"
    private const val TAILLE_MAX_FICHIER = 2L * 1024 * 1024

    private val _entrees = MutableStateFlow<List<EntreeJournal>>(emptyList())
    val entrees: StateFlow<List<EntreeJournal>> = _entrees.asStateFlow()

    private val _etat = MutableStateFlow(EtatAttestation())
    val etat: StateFlow<EtatAttestation> = _etat.asStateFlow()

    @Volatile
    private var charge = false

    fun fichier(context: Context): File = File(context.filesDir, NOM_FICHIER)

    /** Recharge les dernières lignes du fichier au premier accès. */
    @Synchronized
    fun charger(context: Context) {
        if (charge) return
        charge = true
        val f = fichier(context)
        if (!f.exists()) return
        val lignes = try {
            f.readLines().drop(1).takeLast(TAILLE_ANNEAU)
        } catch (e: Exception) {
            emptyList()
        }
        val lues = lignes.mapNotNull { analyser(it) }
        _entrees.value = lues.reversed()
    }

    @Synchronized
    fun ajouter(context: Context, entree: EntreeJournal) {
        _entrees.update { (listOf(entree) + it).take(TAILLE_ANNEAU) }
        if (entree.resultat != EntreeJournal.RESULTAT_IGNORE) {
            _etat.update {
                if (entree.reussie) {
                    it.copy(
                        reussies = it.reussies + 1,
                        derniereReussiteMs = entree.horodatage,
                        derniereSalle = entree.salle,
                        dernierProbleme = "",
                    )
                } else {
                    it.copy(echecs = it.echecs + 1, dernierProbleme = entree.erreur)
                }
            }
        }
        try {
            val f = fichier(context)
            if (f.exists() && f.length() > TAILLE_MAX_FICHIER) {
                val archive = File(context.filesDir, NOM_ARCHIVE)
                archive.delete()
                f.renameTo(archive)
            }
            if (!f.exists()) f.writeText(EntreeJournal.ENTETE_CSV + "\n")
            f.appendText(entree.versCsv() + "\n")
        } catch (e: Exception) {
            // Le journal en mémoire reste disponible.
        }
    }

    fun signalerService(actif: Boolean) {
        _etat.update { it.copy(serviceActif = actif, rechercheActive = if (actif) it.rechercheActive else false) }
    }

    fun signalerRecherche(active: Boolean) {
        _etat.update { it.copy(rechercheActive = active) }
    }

    fun signalerProbleme(message: String) {
        _etat.update { it.copy(dernierProbleme = message) }
    }

    @Synchronized
    fun effacer(context: Context) {
        _entrees.value = emptyList()
        _etat.value = EtatAttestation(serviceActif = _etat.value.serviceActif)
        fichier(context).delete()
        File(context.filesDir, NOM_ARCHIVE).delete()
    }

    private fun analyser(ligne: String): EntreeJournal? {
        val c = ligne.split(",", limit = 7)
        if (c.size < 7) return null
        return try {
            EntreeJournal(
                horodatage = c[0].toLong(),
                observateur = c[1],
                salle = c[2].toInt(),
                seance = c[3].toLong(),
                numeroNonce = c[4].toLong(),
                resultat = c[5],
                erreur = c[6],
            )
        } catch (e: NumberFormatException) {
            null
        }
    }
}
