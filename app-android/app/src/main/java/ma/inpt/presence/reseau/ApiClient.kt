package ma.inpt.presence.reseau

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.net.ConnectException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ma.inpt.presence.cle.DeverrouillageRequis
import ma.inpt.presence.cle.GestionCle
import ma.inpt.presence.protocole.RequeteSignee
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Erreur renvoyée par le serveur ou par le réseau, avec un message lisible. */
class ErreurApi(val code: Int, message: String) : Exception(message)

/**
 * Client HTTP de l'API PRESENCE. Les routes du téléphone sont signées
 * avec la clé de l'appareil (API.md, section 2).
 */
class ApiClient(
    serveur: String,
    private val idAppareil: String? = null,
) {
    private val base: String = serveur.trim().trimEnd('/')

    suspend fun get(chemin: String, signee: Boolean = true): Any? =
        appel("GET", chemin, null, signee)

    suspend fun post(chemin: String, corps: JSONObject? = null, signee: Boolean = true): Any? =
        appel("POST", chemin, corps, signee)

    suspend fun getObjet(chemin: String, signee: Boolean = true): JSONObject? =
        get(chemin, signee) as? JSONObject

    /** Texte brut de la réponse, utilisé pour conserver un reçu tel qu'il a été émis. */
    suspend fun getTexte(chemin: String, signee: Boolean = true): String =
        withContext(Dispatchers.IO) { executer("GET", chemin, null, signee) }

    private suspend fun appel(methode: String, chemin: String, corps: JSONObject?, signee: Boolean): Any? =
        withContext(Dispatchers.IO) {
            val texte = executer(methode, chemin, corps, signee)
            analyser(texte)
        }

    private fun executer(methode: String, chemin: String, corps: JSONObject?, signee: Boolean): String {
        require(chemin.startsWith("/")) { "Chemin relatif attendu" }
        val octets = corps?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val connexion = try {
            URL(base + chemin).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ErreurApi(0, "Adresse du serveur invalide")
        }
        try {
            connexion.requestMethod = methode
            connexion.connectTimeout = DELAI_CONNEXION_MS
            connexion.readTimeout = DELAI_LECTURE_MS
            connexion.useCaches = false
            connexion.setRequestProperty("Accept", "application/json")
            if (signee) {
                val id = idAppareil ?: throw ErreurApi(0, "Appareil non enrôlé")
                val horodatage = System.currentTimeMillis()
                val chaine = RequeteSignee.chaineASigner(methode, chemin, horodatage, octets)
                val signature = try {
                    GestionCle.signerBase64(chaine.toByteArray(Charsets.UTF_8))
                } catch (e: DeverrouillageRequis) {
                    throw ErreurApi(0, "Déverrouillez votre téléphone puis réessayez")
                } catch (e: Exception) {
                    throw ErreurApi(0, "Signature impossible : clé de l'appareil indisponible")
                }
                connexion.setRequestProperty(RequeteSignee.ENTETE_ID, RequeteSignee.identifiant(id))
                connexion.setRequestProperty(RequeteSignee.ENTETE_HORODATAGE, horodatage.toString())
                connexion.setRequestProperty(RequeteSignee.ENTETE_SIGNATURE, signature)
            }
            if (methode == "POST") {
                connexion.doOutput = true
                if (corps != null) {
                    connexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connexion.setFixedLengthStreamingMode(octets.size)
                connexion.outputStream.use { it.write(octets) }
            }
            val code = connexion.responseCode
            val flux = if (code in 200..299) connexion.inputStream else connexion.errorStream
            val texte = flux?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) {
                throw ErreurApi(code, messageErreur(code, texte))
            }
            return texte
        } catch (e: ErreurApi) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw ErreurApi(0, "Le serveur ne répond pas")
        } catch (e: UnknownHostException) {
            throw ErreurApi(0, "Serveur introuvable, vérifiez l'adresse")
        } catch (e: ConnectException) {
            throw ErreurApi(0, "Connexion au serveur impossible")
        } catch (e: IOException) {
            throw ErreurApi(0, "Erreur réseau : ${e.message ?: "inconnue"}")
        } finally {
            connexion.disconnect()
        }
    }

    private fun analyser(texte: String): Any? {
        val propre = texte.trim()
        if (propre.isEmpty()) return null
        val valeur = try {
            JSONTokener(propre).nextValue()
        } catch (e: Exception) {
            throw ErreurApi(0, "Réponse du serveur illisible")
        }
        return if (valeur == JSONObject.NULL) null else valeur
    }

    private fun messageErreur(code: Int, texte: String): String {
        val detail = try {
            val json = JSONTokener(texte).nextValue()
            when (json) {
                is JSONObject -> {
                    when (val d = json.opt("detail")) {
                        is String -> d
                        is JSONObject -> d.optString("message", d.toString())
                        is JSONArray -> (d.opt(0) as? JSONObject)?.optString("msg")
                        else -> json.optString("message").ifBlank { null }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        if (!detail.isNullOrBlank()) return detail
        return when (code) {
            400 -> "Requête refusée par le serveur"
            401 -> "Signature refusée par le serveur"
            403 -> "Action non autorisée pour ce compte"
            404 -> "Élément introuvable"
            409 -> "Conflit : l'état a changé entre-temps"
            in 500..599 -> "Erreur du serveur ($code)"
            else -> "Erreur $code"
        }
    }

    companion object {
        private const val DELAI_CONNEXION_MS = 8_000
        private const val DELAI_LECTURE_MS = 15_000
    }
}
