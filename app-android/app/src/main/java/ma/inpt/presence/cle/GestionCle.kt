package ma.inpt.presence.cle

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Résultat de la création de la clé de l'appareil. */
class CleCreee(
    val clePublique: PublicKey,
    val chaineAttestation: List<ByteArray>,
    val strongBox: Boolean,
    val deverrouillageRecent: Boolean,
)

/** Levée quand la clé exige un déverrouillage récent du téléphone. */
class DeverrouillageRequis(cause: Throwable) : Exception("Déverrouillage requis", cause)

/**
 * Clé ECDSA P-256 de l'appareil, non exportable, dans l'Android Keystore.
 */
object GestionCle {

    const val ALIAS = "presence"
    private const val FOURNISSEUR = "AndroidKeyStore"
    private const val DUREE_DEVERROUILLAGE_S = 4 * 3600

    private fun keyStore(): KeyStore = KeyStore.getInstance(FOURNISSEUR).apply { load(null) }

    fun existe(): Boolean = try {
        keyStore().containsAlias(ALIAS)
    } catch (e: Exception) {
        false
    }

    fun supprimer() {
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (e: Exception) {
            // Rien à supprimer.
        }
    }

    /**
     * Crée la clé avec le défi du serveur comme challenge d'attestation.
     * Essaie d'abord StrongBox, puis le TEE ; avec puis sans exigence de déverrouillage récent.
     */
    fun creer(context: Context, defi: ByteArray): CleCreee {
        supprimer()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val verrouSecurise = keyguard?.isDeviceSecure == true
        val essaisStrongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) listOf(true, false) else listOf(false)
        val essaisDeverrouillage =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && verrouSecurise) listOf(true, false) else listOf(false)

        var derniereErreur: Exception? = null
        for (strongBox in essaisStrongBox) {
            for (deverrouillage in essaisDeverrouillage) {
                try {
                    val publique = generer(defi, strongBox, deverrouillage)
                    val chaine = keyStore().getCertificateChain(ALIAS)?.map { it.encoded } ?: emptyList()
                    return CleCreee(publique, chaine, strongBox, deverrouillage)
                } catch (e: Exception) {
                    // StrongBoxUnavailableException hérite de ProviderException : on passe à l'essai suivant.
                    derniereErreur = e
                    supprimer()
                }
            }
        }
        throw IllegalStateException("Impossible de créer la clé de l'appareil", derniereErreur)
    }

    private fun generer(defi: ByteArray, strongBox: Boolean, deverrouillage: Boolean): PublicKey {
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(defi)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        if (deverrouillage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationRequired(true)
            builder.setUserAuthenticationParameters(
                DUREE_DEVERROUILLAGE_S,
                KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }
        val generateur = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, FOURNISSEUR)
        generateur.initialize(builder.build())
        return generateur.generateKeyPair().public
    }

    fun clePublique(): PublicKey? = try {
        keyStore().getCertificate(ALIAS)?.publicKey
    } catch (e: Exception) {
        null
    }

    /** Signature ECDSA P-256 / SHA-256, encodage DER. */
    @Throws(DeverrouillageRequis::class)
    fun signer(message: ByteArray): ByteArray {
        val privee = keyStore().getKey(ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Clé de l'appareil absente")
        try {
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privee)
            signature.update(message)
            return signature.sign()
        } catch (e: UserNotAuthenticatedException) {
            throw DeverrouillageRequis(e)
        }
    }

    fun signerBase64(message: ByteArray): String = Base64.getEncoder().encodeToString(signer(message))
}
