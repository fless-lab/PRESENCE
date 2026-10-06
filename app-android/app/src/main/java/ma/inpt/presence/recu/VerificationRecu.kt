package ma.inpt.presence.recu

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import ma.inpt.presence.protocole.JsonCanonique
import ma.inpt.presence.protocole.Merkle
import ma.inpt.presence.protocole.Octets
import ma.inpt.presence.reseau.ApiClient
import ma.inpt.presence.reseau.ErreurApi
import ma.inpt.presence.reseau.Json
import ma.inpt.presence.reseau.entier
import ma.inpt.presence.reseau.texte
import org.json.JSONObject
import org.json.JSONTokener

/** Résultat de la vérification locale d'un reçu de présence. */
data class Verification(
    val seance: String,
    val statut: String,
    val fenetresValidees: Long,
    val fenetresTotal: Long,
    val racine: String,
    val tailleArbre: Long,
    val signatureValide: Boolean,
    val preuvesValides: Int,
    val preuvesTotal: Int,
    val racineRegistre: String,
    val ancrageConforme: Boolean,
    val transaction: String,
    val registre: String,
    val texteRecu: String,
    val remarques: List<String>,
) {
    val verifie: Boolean
        get() = signatureValide && preuvesTotal > 0 && preuvesValides == preuvesTotal && ancrageConforme
}

/**
 * Vérification d'un reçu (API.md, sections 9 et 11) :
 * signature du serveur sur le JSON canonique sans sig_serveur,
 * preuve d'inclusion de chaque événement, racine comparée à l'ancrage du registre.
 */
object VerificationRecu {

    suspend fun verifier(api: ApiClient, seance: String): Verification {
        val texte = api.getTexte("/moi/recus/$seance")
        val recu = JSONTokener(texte).nextValue() as? JSONObject
            ?: throw ErreurApi(0, "Reçu illisible")
        val cleServeur = api.getObjet("/cle-serveur", signee = false)?.texte("cle_publique")
            ?: throw ErreurApi(0, "Clé du serveur indisponible")
        val idSeance = recu.optJSONObject("seance")?.texte("id") ?: seance
        val ancrage = try {
            api.getObjet("/ancrages/$idSeance", signee = false)
        } catch (e: ErreurApi) {
            null
        }
        return verifierLocalement(texte, recu, cleServeur, ancrage)
    }

    fun verifierLocalement(texte: String, recu: JSONObject, cleServeurBase64: String, ancrage: JSONObject?): Verification {
        val remarques = mutableListOf<String>()

        // 1. Signature du serveur
        val signatureValide = try {
            @Suppress("UNCHECKED_CAST")
            val sansSignature = (Json.versKotlin(recu) as Map<String, Any?>).toMutableMap()
            sansSignature.remove("sig_serveur")
            val canonique = JsonCanonique.octets(sansSignature)
            val cle = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(cleServeurBase64)))
            val verificateur = Signature.getInstance("SHA256withECDSA")
            verificateur.initVerify(cle)
            verificateur.update(canonique)
            verificateur.verify(Base64.getDecoder().decode(recu.optString("sig_serveur")))
        } catch (e: Exception) {
            remarques.add("Signature non vérifiable : ${e.message ?: e.javaClass.simpleName}")
            false
        }
        if (!signatureValide && remarques.isEmpty()) remarques.add("La signature du serveur ne correspond pas au reçu")

        // 2. Preuves d'inclusion
        val arbre = recu.optJSONObject("arbre") ?: JSONObject()
        val racine = arbre.texte("racine") ?: ""
        val taille = arbre.entier("taille") ?: 0L
        val racineOctets = try {
            Octets.depuisHex(racine)
        } catch (e: IllegalArgumentException) {
            ByteArray(0)
        }
        val evenements = Json.listeObjets(recu, "evenements")
        var valides = 0
        for (ev in evenements) {
            val index = ev.entier("index") ?: -1L
            val canonique = ev.optString("canonique").toByteArray(Charsets.UTF_8)
            val preuveJson = ev.optJSONArray("preuve")
            val preuve = try {
                (0 until (preuveJson?.length() ?: 0)).map { Octets.depuisHex(preuveJson!!.getString(it)) }
            } catch (e: Exception) {
                null
            }
            if (preuve != null && Merkle.verifierInclusion(index, taille, canonique, preuve, racineOctets)) {
                valides++
            } else {
                remarques.add("Preuve d'inclusion invalide pour l'événement $index")
            }
        }
        if (evenements.isEmpty()) remarques.add("Le reçu ne contient aucun événement")

        // 3. Ancrage sur le registre
        val racinesRegistre = mutableListOf<String>()
        ancrage?.texte("racine")?.let { racinesRegistre.add(it.lowercase()) }
        Json.listeObjets(ancrage, "corrections").forEach { c ->
            c.texte("nouvelle_racine", "racine")?.let { racinesRegistre.add(it.lowercase()) }
        }
        val ancrageConforme = racine.isNotBlank() && racinesRegistre.contains(racine.lowercase())
        when {
            ancrage == null -> remarques.add("Ancrage introuvable sur le registre")
            !ancrageConforme -> remarques.add("La racine du reçu diffère de celle du registre")
        }

        val fenetres = recu.optJSONObject("fenetres")
        val recuAncrage = recu.optJSONObject("ancrage")
        return Verification(
            seance = recu.optJSONObject("seance")?.texte("id") ?: "",
            statut = recu.texte("statut") ?: "",
            fenetresValidees = fenetres?.entier("validees") ?: 0L,
            fenetresTotal = fenetres?.entier("total") ?: 0L,
            racine = racine,
            tailleArbre = taille,
            signatureValide = signatureValide,
            preuvesValides = valides,
            preuvesTotal = evenements.size,
            racineRegistre = ancrage?.texte("racine") ?: "",
            ancrageConforme = ancrageConforme,
            transaction = recuAncrage?.texte("transaction") ?: ancrage?.texte("transaction") ?: "",
            registre = recuAncrage?.texte("registre") ?: ancrage?.texte("registre") ?: "",
            texteRecu = texte,
            remarques = remarques,
        )
    }
}
