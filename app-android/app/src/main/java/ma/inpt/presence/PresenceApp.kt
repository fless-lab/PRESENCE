package ma.inpt.presence

import android.app.Application
import ma.inpt.presence.donnees.Journal
import ma.inpt.presence.service.Notifications

class PresenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.creerCanaux(this)
        Journal.charger(this)
    }
}
