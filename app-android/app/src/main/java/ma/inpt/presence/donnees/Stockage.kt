package ma.inpt.presence.donnees

import android.content.Context
import android.content.SharedPreferences

/** Identité de l'appareil enrôlé. */
data class Identite(
    val idAppareil: String,
    val role: String,
    val matricule: String,
    val nom: String,
    val prenom: String,
    val serveur: String,
    val modele: String,
    val deverrouillageRecent: Boolean,
    val strongBox: Boolean,
) {
    val estEnseignant: Boolean get() = role.equals("ENSEIGNANT", ignoreCase = true)
    val nomComplet: String get() = listOf(prenom, nom).filter { it.isNotBlank() }.joinToString(" ")
}

/** Préférences locales : identité, adresse du serveur, compteur d'attestation. */
class Stockage(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    var serveur: String
        get() = prefs.getString(CLE_SERVEUR, SERVEUR_PAR_DEFAUT) ?: SERVEUR_PAR_DEFAUT
        set(value) {
            prefs.edit().putString(CLE_SERVEUR, value).apply()
        }

    var permissionsVues: Boolean
        get() = prefs.getBoolean(CLE_PERMISSIONS_VUES, false)
        set(value) {
            prefs.edit().putBoolean(CLE_PERMISSIONS_VUES, value).apply()
        }

    fun identite(): Identite? {
        val id = prefs.getString(CLE_ID, null) ?: return null
        return Identite(
            idAppareil = id,
            role = prefs.getString(CLE_ROLE, "ETUDIANT") ?: "ETUDIANT",
            matricule = prefs.getString(CLE_MATRICULE, "") ?: "",
            nom = prefs.getString(CLE_NOM, "") ?: "",
            prenom = prefs.getString(CLE_PRENOM, "") ?: "",
            serveur = serveur,
            modele = prefs.getString(CLE_MODELE, "") ?: "",
            deverrouillageRecent = prefs.getBoolean(CLE_DEVERROUILLAGE, false),
            strongBox = prefs.getBoolean(CLE_STRONGBOX, false),
        )
    }

    fun enregistrerIdentite(identite: Identite) {
        prefs.edit()
            .putString(CLE_ID, identite.idAppareil)
            .putString(CLE_ROLE, identite.role)
            .putString(CLE_MATRICULE, identite.matricule)
            .putString(CLE_NOM, identite.nom)
            .putString(CLE_PRENOM, identite.prenom)
            .putString(CLE_SERVEUR, identite.serveur)
            .putString(CLE_MODELE, identite.modele)
            .putBoolean(CLE_DEVERROUILLAGE, identite.deverrouillageRecent)
            .putBoolean(CLE_STRONGBOX, identite.strongBox)
            .commit()
    }

    /**
     * Incrémente le compteur d'attestation et l'écrit de façon synchrone avant usage,
     * pour qu'il reste strictement croissant même après un arrêt brutal.
     */
    @Synchronized
    fun prochainCompteur(): Long {
        val actuel = prefs.getLong(CLE_COMPTEUR, 0L)
        val suivant = actuel + 1
        check(suivant <= 0xFFFF_FFFFL) { "Compteur d'attestation épuisé" }
        prefs.edit().putLong(CLE_COMPTEUR, suivant).commit()
        return suivant
    }

    fun compteurActuel(): Long = prefs.getLong(CLE_COMPTEUR, 0L)

    /** Efface l'identité. L'adresse du serveur est conservée pour un nouvel enrôlement. */
    fun effacer() {
        val adresse = serveur
        prefs.edit().clear().putString(CLE_SERVEUR, adresse).commit()
    }

    companion object {
        const val SERVEUR_PAR_DEFAUT = "http://192.168.1.10:8000"

        private const val FICHIER = "presence"
        private const val CLE_SERVEUR = "serveur"
        private const val CLE_ID = "id_appareil"
        private const val CLE_ROLE = "role"
        private const val CLE_MATRICULE = "matricule"
        private const val CLE_NOM = "nom"
        private const val CLE_PRENOM = "prenom"
        private const val CLE_MODELE = "modele"
        private const val CLE_DEVERROUILLAGE = "deverrouillage_recent"
        private const val CLE_STRONGBOX = "strongbox"
        private const val CLE_COMPTEUR = "compteur"
        private const val CLE_PERMISSIONS_VUES = "permissions_vues"
    }
}
