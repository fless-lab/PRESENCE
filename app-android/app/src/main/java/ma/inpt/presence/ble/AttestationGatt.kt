package ma.inpt.presence.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import ma.inpt.presence.cle.DeverrouillageRequis
import ma.inpt.presence.cle.GestionCle
import ma.inpt.presence.donnees.EntreeJournal
import ma.inpt.presence.donnees.Stockage
import ma.inpt.presence.protocole.Constantes
import ma.inpt.presence.protocole.Octets
import ma.inpt.presence.protocole.Trames

/** Échec d'une étape de l'échange GATT, avec un message court pour le journal. */
class EchecGatt(message: String) : Exception(message)

private sealed class Evenement {
    class Connexion(val statut: Int, val etat: Int) : Evenement()
    class Mtu(val mtu: Int, val statut: Int) : Evenement()
    class Services(val statut: Int) : Evenement()
    class Lecture(val uuid: UUID, val valeur: ByteArray, val statut: Int) : Evenement()
    class Ecriture(val uuid: UUID, val statut: Int) : Evenement()
}

private suspend inline fun <reified T : Evenement> Channel<Evenement>.attendre(): T {
    while (true) {
        val e = receive()
        if (e is Evenement.Connexion && e.etat == BluetoothProfile.STATE_DISCONNECTED) {
            throw EchecGatt("Déconnexion (statut ${e.statut})")
        }
        if (e is T) return e
    }
}

/**
 * Un échange d'attestation avec un observateur :
 * connexion, MTU, découverte, lecture info et nonce, signature, écriture, déconnexion.
 * L'appelant garantit qu'un seul échange a lieu à la fois.
 */
@SuppressLint("MissingPermission")
class AttestationGatt(
    private val context: Context,
    private val stockage: Stockage,
) {

    /** Appelé quand la clé exige un déverrouillage du téléphone. */
    var surDeverrouillageRequis: (() -> Unit)? = null

    suspend fun attester(appareil: BluetoothDevice, idAppareil: String, enseignant: Boolean): EntreeJournal {
        val debut = System.currentTimeMillis()
        val evenements = Channel<Evenement>(Channel.UNLIMITED)
        val rappel = Rappel(evenements)
        var gattOuvert: BluetoothGatt? = null
        var salle = 0
        var seance = 0L
        var numeroNonce = 0L

        fun entree(resultat: String, erreur: String) = EntreeJournal(
            horodatage = debut,
            observateur = appareil.address ?: "",
            salle = salle,
            seance = seance,
            numeroNonce = numeroNonce,
            resultat = resultat,
            erreur = erreur,
        )

        try {
            return withTimeout(Constantes.DELAI_GATT_MS) {
                val gatt = appareil.connectGatt(context, false, rappel, BluetoothDevice.TRANSPORT_LE)
                    ?: throw EchecGatt("Connexion refusée par le système")
                gattOuvert = gatt

                evenements.attendreConnexion()

                if (gatt.requestMtu(Constantes.MTU_DEMANDE)) {
                    // Un refus de MTU n'est pas bloquant : Android passe alors par une écriture longue.
                    evenements.attendre<Evenement.Mtu>()
                }

                if (!gatt.discoverServices()) throw EchecGatt("Découverte des services refusée")
                val services = evenements.attendre<Evenement.Services>()
                if (services.statut != BluetoothGatt.GATT_SUCCESS) {
                    throw EchecGatt("Découverte des services en échec (statut ${services.statut})")
                }
                val service = gatt.getService(Constantes.SERVICE_UUID)
                    ?: throw EchecGatt("Service PRESENCE absent")
                val carInfo = service.getCharacteristic(Constantes.CARACTERISTIQUE_INFO)
                    ?: throw EchecGatt("Caractéristique info absente")
                val carNonce = service.getCharacteristic(Constantes.CARACTERISTIQUE_NONCE)
                    ?: throw EchecGatt("Caractéristique nonce absente")
                val carAttest = service.getCharacteristic(Constantes.CARACTERISTIQUE_ATTEST)
                    ?: throw EchecGatt("Caractéristique attest absente")

                val info = Trames.lireInfo(lire(gatt, carInfo, evenements))
                    ?: throw EchecGatt("Info illisible")
                salle = info.salle
                seance = info.seance
                if (!Trames.doitAttester(info.etat, enseignant)) {
                    return@withTimeout entree(EntreeJournal.RESULTAT_IGNORE, "État ${info.etat} sans attestation")
                }

                val nonce = Trames.lireNonce(lire(gatt, carNonce, evenements))
                    ?: throw EchecGatt("Nonce illisible")
                numeroNonce = nonce.numero

                val compteur = stockage.prochainCompteur()
                val message = Trames.messageAttestation(info.salle, info.seance, nonce.nonce, compteur)
                val signature = GestionCle.signer(message)
                val trame = Trames.trameAttestation(Octets.depuisHex(idAppareil), compteur, nonce.numero, signature)

                ecrire(gatt, carAttest, trame)
                val ecriture = evenements.attendre<Evenement.Ecriture>()
                if (ecriture.statut != BluetoothGatt.GATT_SUCCESS) {
                    throw EchecGatt("Attestation refusée par l'observateur (statut ${ecriture.statut})")
                }
                entree(EntreeJournal.RESULTAT_OK, "")
            }
        } catch (e: TimeoutCancellationException) {
            return entree(EntreeJournal.RESULTAT_ECHEC, "Délai de ${Constantes.DELAI_GATT_MS / 1000} s dépassé")
        } catch (e: CancellationException) {
            // Arrêt du service : l'annulation doit remonter.
            throw e
        } catch (e: DeverrouillageRequis) {
            surDeverrouillageRequis?.invoke()
            return entree(EntreeJournal.RESULTAT_ECHEC, "Déverrouillage requis")
        } catch (e: EchecGatt) {
            return entree(EntreeJournal.RESULTAT_ECHEC, e.message ?: "Échec GATT")
        } catch (e: SecurityException) {
            return entree(EntreeJournal.RESULTAT_ECHEC, "Permission Bluetooth manquante")
        } catch (e: IllegalStateException) {
            return entree(EntreeJournal.RESULTAT_ECHEC, e.message ?: "État invalide")
        } catch (e: IllegalArgumentException) {
            return entree(EntreeJournal.RESULTAT_ECHEC, e.message ?: "Donnée invalide")
        } finally {
            gattOuvert?.let { fermer(it, evenements) }
        }
    }

    private suspend fun Channel<Evenement>.attendreConnexion() {
        while (true) {
            val e = receive()
            if (e is Evenement.Connexion) {
                if (e.etat == BluetoothProfile.STATE_CONNECTED && e.statut == BluetoothGatt.GATT_SUCCESS) return
                if (e.etat == BluetoothProfile.STATE_DISCONNECTED) throw EchecGatt("Connexion impossible (statut ${e.statut})")
            }
        }
    }

    private suspend fun lire(gatt: BluetoothGatt, car: BluetoothGattCharacteristic, evenements: Channel<Evenement>): ByteArray {
        if (!gatt.readCharacteristic(car)) throw EchecGatt("Lecture refusée")
        val lecture = evenements.attendre<Evenement.Lecture>()
        if (lecture.statut != BluetoothGatt.GATT_SUCCESS) {
            throw EchecGatt("Lecture en échec (statut ${lecture.statut})")
        }
        return lecture.valeur
    }

    private fun ecrire(gatt: BluetoothGatt, car: BluetoothGattCharacteristic, valeur: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = gatt.writeCharacteristic(car, valeur, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            if (code != BluetoothStatusCodes.SUCCESS) throw EchecGatt("Écriture refusée (code $code)")
        } else {
            if (!ecrireAncien(gatt, car, valeur)) throw EchecGatt("Écriture refusée")
        }
    }

    @Suppress("DEPRECATION")
    private fun ecrireAncien(gatt: BluetoothGatt, car: BluetoothGattCharacteristic, valeur: ByteArray): Boolean {
        car.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        car.value = valeur
        return gatt.writeCharacteristic(car)
    }

    /** Déconnexion propre, puis fermeture dans tous les cas. */
    private suspend fun fermer(gatt: BluetoothGatt, evenements: Channel<Evenement>) {
        try {
            gatt.disconnect()
            withTimeoutOrNull(DELAI_DECONNEXION_MS) {
                while (true) {
                    val e = evenements.receive()
                    if (e is Evenement.Connexion && e.etat == BluetoothProfile.STATE_DISCONNECTED) break
                }
            }
        } catch (e: Exception) {
            // La fermeture ci-dessous libère la connexion.
        } finally {
            try {
                gatt.close()
            } catch (e: Exception) {
                // Rien de plus à faire.
            }
            evenements.close()
        }
        // Laisse la pile Bluetooth respirer entre deux échanges.
        delay(PAUSE_ENTRE_ECHANGES_MS)
    }

    private class Rappel(private val evenements: Channel<Evenement>) : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            evenements.trySend(Evenement.Connexion(status, newState))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            evenements.trySend(Evenement.Mtu(mtu, status))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            evenements.trySend(Evenement.Services(status))
        }

        // Android 13 et plus
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            evenements.trySend(Evenement.Lecture(characteristic.uuid, value, status))
        }

        // Android 12 et moins
        @Deprecated("Remplacé par la variante avec valeur à partir d'Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val valeur = characteristic.value ?: ByteArray(0)
            evenements.trySend(Evenement.Lecture(characteristic.uuid, valeur, status))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            evenements.trySend(Evenement.Ecriture(characteristic.uuid, status))
        }
    }

    companion object {
        private const val DELAI_DECONNEXION_MS = 1_000L
        private const val PAUSE_ENTRE_ECHANGES_MS = 300L
    }
}
