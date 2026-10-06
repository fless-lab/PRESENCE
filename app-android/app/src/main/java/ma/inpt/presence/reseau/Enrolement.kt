package ma.inpt.presence.reseau

import android.content.Context
import android.os.Build
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ma.inpt.presence.cle.GestionCle
import ma.inpt.presence.donnees.Identite
import ma.inpt.presence.donnees.Stockage
import ma.inpt.presence.protocole.Octets
import org.json.JSONArray
import org.json.JSONObject

/** Enrôlement du téléphone (API.md, section 5). */
object Enrolement {

    fun modele(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    suspend fun enroler(context: Context, serveur: String, matricule: String, code: String): Identite =
        withContext(Dispatchers.IO) {
            val api = ApiClient(serveur)

            val reponseDefi = api.post(
                "/enrolement/defi",
                JSONObject().put("matricule", matricule),
                signee = false,
            ) as? JSONObject ?: throw ErreurApi(0, "Réponse inattendue du serveur")
            val defiTexte = reponseDefi.texte("defi") ?: throw ErreurApi(0, "Défi absent de la réponse")
            val defi = try {
                Base64.getDecoder().decode(defiTexte)
            } catch (e: IllegalArgumentException) {
                throw ErreurApi(0, "Défi illisible")
            }

            val cle = try {
                GestionCle.creer(context, defi)
            } catch (e: Exception) {
                throw ErreurApi(0, "Ce téléphone n'a pas pu créer de clé sécurisée")
            }

            val spki = cle.clePublique.encoded
            val encodeur = Base64.getEncoder()
            val corps = JSONObject()
                .put("matricule", matricule)
                .put("code", code)
                .put("cle_publique", encodeur.encodeToString(spki))
                .put("defi", defiTexte)
                .put("chaine_attestation", JSONArray(cle.chaineAttestation.map { encodeur.encodeToString(it) }))
                .put("modele", modele())
                .put("deverrouillage_recent", cle.deverrouillageRecent)

            val reponse = try {
                api.post("/enrolement", corps, signee = false) as? JSONObject
                    ?: throw ErreurApi(0, "Réponse inattendue du serveur")
            } catch (e: Exception) {
                GestionCle.supprimer()
                throw e
            }

            val personne = reponse.optJSONObject("personne") ?: JSONObject()
            val identite = Identite(
                idAppareil = reponse.texte("id_appareil") ?: Octets.idAppareil(spki),
                role = personne.texte("role") ?: "ETUDIANT",
                matricule = personne.texte("matricule") ?: matricule,
                nom = personne.texte("nom") ?: "",
                prenom = personne.texte("prenom") ?: "",
                serveur = serveur.trim().trimEnd('/'),
                modele = modele(),
                deverrouillageRecent = cle.deverrouillageRecent,
                strongBox = cle.strongBox,
            )
            Stockage(context).enregistrerIdentite(identite)
            identite
        }
}
