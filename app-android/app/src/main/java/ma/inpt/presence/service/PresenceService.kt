package ma.inpt.presence.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ma.inpt.presence.R
import ma.inpt.presence.ble.AttestationGatt
import ma.inpt.presence.donnees.EntreeJournal
import ma.inpt.presence.donnees.Identite
import ma.inpt.presence.donnees.Journal
import ma.inpt.presence.donnees.Stockage
import ma.inpt.presence.protocole.Constantes
import ma.inpt.presence.protocole.Trames
import ma.inpt.presence.ui.Format

/**
 * Service de premier plan : recherche Bluetooth filtrée sur le service PRESENCE
 * et file d'attestations GATT traitées une par une.
 */
@SuppressLint("MissingPermission")
class PresenceService : Service() {

    private class Candidat(val appareil: BluetoothDevice)

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val file = Channel<Candidat>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val derniersEssais = ConcurrentHashMap<String, Long>()

    private lateinit var stockage: Stockage
    private lateinit var gatt: AttestationGatt
    private var identite: Identite? = null

    private var scanner: BluetoothLeScanner? = null
    private var rechercheEnCours = false
    private var relance: Job? = null
    private var recepteurEnregistre = false
    private var pret = false

    private val rappelRecherche = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            traiter(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { traiter(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            rechercheEnCours = false
            Journal.signalerRecherche(false)
            Journal.signalerProbleme("Recherche Bluetooth en échec (code $errorCode)")
            portee.launch {
                delay(DELAI_REPRISE_MS)
                demarrerRecherche()
            }
        }
    }

    private val recepteurBluetooth = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> demarrerRecherche()
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    arreterRecherche()
                    Journal.signalerProbleme("Bluetooth désactivé")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        stockage = Stockage(this)
        identite = stockage.identite()
        gatt = AttestationGatt(this, stockage).apply {
            surDeverrouillageRequis = { Notifications.deverrouillageRequis(this@PresenceService) }
        }
        Journal.charger(this)

        if (!passerAuPremierPlan()) {
            stopSelf()
            return
        }
        if (identite == null || !Diagnostic.bluetoothPermis(this)) {
            stopSelf()
            return
        }
        Journal.signalerService(true)

        ContextCompat.registerReceiver(
            this,
            recepteurBluetooth,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        recepteurEnregistre = true

        portee.launch { consommer() }
        demarrerRecherche()
        relance = portee.launch {
            // Android rétrograde les recherches qui durent plus de 30 minutes : on relance avant.
            while (true) {
                delay(RELANCE_RECHERCHE_MS)
                arreterRecherche()
                delay(1_000)
                demarrerRecherche()
            }
        }
        pret = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!pret) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        arreterRecherche()
        if (recepteurEnregistre) {
            try {
                unregisterReceiver(recepteurBluetooth)
            } catch (e: IllegalArgumentException) {
                // Déjà retiré.
            }
        }
        portee.cancel()
        Journal.signalerService(false)
        super.onDestroy()
    }

    private fun passerAuPremierPlan(): Boolean {
        val notification = Notifications.notificationService(this, getString(R.string.notif_en_attente))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, Notifications.ID_SERVICE, notification, type)
            true
        } catch (e: Exception) {
            // Permissions Bluetooth absentes ou démarrage refusé par le système.
            Journal.signalerProbleme("Service non démarré : ${e.javaClass.simpleName}")
            false
        }
    }

    @Synchronized
    private fun demarrerRecherche() {
        if (rechercheEnCours) return
        if (!Diagnostic.bluetoothPermis(this)) {
            Journal.signalerProbleme("Permissions Bluetooth manquantes")
            return
        }
        val adaptateur = getSystemService(BluetoothManager::class.java)?.adapter
        if (adaptateur == null || !adaptateur.isEnabled) {
            Journal.signalerProbleme("Bluetooth désactivé")
            return
        }
        val leScanner = adaptateur.bluetoothLeScanner ?: return
        val filtre = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(Constantes.SERVICE_UUID))
            .build()
        val reglages = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
        try {
            leScanner.startScan(listOf(filtre), reglages, rappelRecherche)
            scanner = leScanner
            rechercheEnCours = true
            Journal.signalerRecherche(true)
        } catch (e: Exception) {
            Journal.signalerProbleme("Recherche Bluetooth impossible : ${e.javaClass.simpleName}")
        }
    }

    @Synchronized
    private fun arreterRecherche() {
        val s = scanner
        if (s != null && rechercheEnCours) {
            try {
                s.stopScan(rappelRecherche)
            } catch (e: Exception) {
                // Bluetooth déjà arrêté.
            }
        }
        rechercheEnCours = false
        scanner = null
        Journal.signalerRecherche(false)
    }

    private fun traiter(resultat: ScanResult) {
        val moi = identite ?: return
        val donnees = resultat.scanRecord?.getManufacturerSpecificData(Constantes.ID_FABRICANT)
        val annonce = Trames.lireAnnonce(donnees) ?: return
        if (annonce.version != Constantes.VERSION_TRAME) return
        if (!Trames.doitAttester(annonce.etat, moi.estEnseignant)) return

        val adresse = resultat.device.address ?: return
        val maintenant = System.currentTimeMillis()
        val dernier = derniersEssais[adresse] ?: 0L
        if (maintenant - dernier < Constantes.INTERVALLE_ATTESTATION_MS) return
        derniersEssais[adresse] = maintenant
        file.trySend(Candidat(resultat.device))
    }

    private suspend fun consommer() {
        for (candidat in file) {
            val moi = identite ?: continue
            val entree = gatt.attester(candidat.appareil, moi.idAppareil, moi.estEnseignant)
            if (entree.resultat == EntreeJournal.RESULTAT_ECHEC) {
                // Un échec libère l'observateur plus tôt pour un nouvel essai.
                derniersEssais[candidat.appareil.address ?: ""] =
                    System.currentTimeMillis() - Constantes.INTERVALLE_ATTESTATION_MS / 2
            }
            Journal.ajouter(this, entree)
            if (entree.reussie) {
                Notifications.mettreAJourService(
                    this,
                    getString(R.string.notif_derniere_attestation, Format.heure(entree.horodatage), entree.salle.toString()),
                )
            }
        }
    }

    companion object {
        private const val RELANCE_RECHERCHE_MS = 25L * 60 * 1000
        private const val DELAI_REPRISE_MS = 10_000L

        /** Démarre le service si l'appareil est enrôlé et les permissions Bluetooth accordées. */
        fun demarrer(context: Context): Boolean {
            if (Stockage(context).identite() == null) return false
            if (!Diagnostic.bluetoothPermis(context)) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, PresenceService::class.java))
                true
            } catch (e: Exception) {
                Journal.signalerProbleme("Démarrage du service refusé : ${e.javaClass.simpleName}")
                false
            }
        }

        fun arreter(context: Context) {
            context.stopService(Intent(context, PresenceService::class.java))
        }
    }
}
