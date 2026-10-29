package ma.inpt.presence.service

import android.content.Context
import ma.inpt.presence.donnees.Stockage
import ma.inpt.presence.reseau.ApiClient
import ma.inpt.presence.reseau.ChangementCours
import ma.inpt.presence.reseau.Json
import ma.inpt.presence.reseau.LecturePlanning
import ma.inpt.presence.reseau.MessagesChangement
import ma.inpt.presence.ui.Format

/**
 * Interroge le serveur pour les changements de planning des cours de la personne
 * et les notifie. Au premier passage, l'historique existant est marqué comme lu
 * sans notification.
 */
object SuiviPlanning {

    suspend fun verifier(context: Context, api: ApiClient, stockage: Stockage) {
        val premier = stockage.dernierChangement < 0
        var curseur = stockage.dernierChangement.coerceAtLeast(0)
        // Au plus quelques pages par passage : le reste vient au passage suivant.
        repeat(MAX_PAGES) {
            val lot = lire(api, curseur)
            if (lot.isEmpty()) {
                if (premier) stockage.dernierChangement = curseur
                return
            }
            if (!premier) lot.forEach { notifier(context, it) }
            curseur = lot.maxOf { it.id }
            stockage.dernierChangement = curseur
        }
    }

    private suspend fun lire(api: ApiClient, apres: Long): List<ChangementCours> =
        Json.listeObjets(api.get("/moi/changements?apres=$apres"), "changements", "elements")
            .map { LecturePlanning.changement(it) }
            .sortedBy { it.id }

    private fun notifier(context: Context, c: ChangementCours) {
        val message = MessagesChangement.composer(c, jour = Format::date, heure = Format::heure)
        Notifications.changementCours(context, c.id, message.titre, message.corps)
    }

    private const val MAX_PAGES = 5
}
