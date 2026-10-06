package ma.inpt.presence.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ma.inpt.presence.MainActivity
import ma.inpt.presence.R

object Notifications {

    const val CANAL_PRESENCE = "presence"
    const val CANAL_ALERTES = "alertes"
    const val ID_SERVICE = 1
    private const val ID_DEVERROUILLAGE = 2

    fun creerCanaux(context: Context) {
        val gestionnaire = context.getSystemService(NotificationManager::class.java) ?: return
        val presence = NotificationChannel(
            CANAL_PRESENCE,
            context.getString(R.string.canal_presence),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.canal_presence_description)
            setShowBadge(false)
        }
        val alertes = NotificationChannel(
            CANAL_ALERTES,
            context.getString(R.string.canal_alertes),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.canal_alertes_description)
        }
        gestionnaire.createNotificationChannel(presence)
        gestionnaire.createNotificationChannel(alertes)
    }

    private fun ouvrirApplication(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun notificationService(context: Context, texte: String): Notification =
        NotificationCompat.Builder(context, CANAL_PRESENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_presence_active))
            .setContentText(texte)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(ouvrirApplication(context))
            .build()

    fun mettreAJourService(context: Context, texte: String) {
        afficher(context, ID_SERVICE, notificationService(context, texte))
    }

    fun deverrouillageRequis(context: Context) {
        val notification = NotificationCompat.Builder(context, CANAL_ALERTES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_deverrouiller))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(ouvrirApplication(context))
            .build()
        afficher(context, ID_DEVERROUILLAGE, notification)
    }

    private fun afficher(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission retirée entre-temps.
        }
    }
}
