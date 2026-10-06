package ma.inpt.presence.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relance le service de présence au démarrage du téléphone et après une mise à jour. */
class DemarrageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> PresenceService.demarrer(context)
        }
    }
}
