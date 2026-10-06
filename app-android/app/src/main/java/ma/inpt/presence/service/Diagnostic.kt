package ma.inpt.presence.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Vérifications de l'environnement nécessaires à l'attestation. */
object Diagnostic {

    /** Permissions d'exécution à demander selon la version d'Android. */
    fun permissionsBluetooth(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun permissionsADemander(): Array<String> {
        val liste = permissionsBluetooth().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            liste.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return liste.toTypedArray()
    }

    private fun accorde(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun bluetoothPermis(context: Context): Boolean = permissionsBluetooth().all { accorde(context, it) }

    fun notificationsPermises(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || accorde(context, Manifest.permission.POST_NOTIFICATIONS)

    @SuppressLint("MissingPermission")
    fun bluetoothActif(context: Context): Boolean = try {
        val gestionnaire = context.getSystemService(BluetoothManager::class.java)
        gestionnaire?.adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    fun batterieExclue(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun intentionExclusionBatterie(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun intentionReglagesApplication(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
