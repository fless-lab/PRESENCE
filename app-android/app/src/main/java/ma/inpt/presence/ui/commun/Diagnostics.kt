package ma.inpt.presence.ui.commun

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ma.inpt.presence.R
import ma.inpt.presence.donnees.Journal
import ma.inpt.presence.service.Diagnostic
import ma.inpt.presence.service.PresenceService
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.composants.Bloc
import ma.inpt.presence.ui.composants.Filet
import ma.inpt.presence.ui.composants.LigneDiagnostic
import ma.inpt.presence.ui.composants.Point
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles

fun ouvrir(context: Context, intention: Intent) {
    try {
        context.startActivity(intention)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Diagnostic.intentionReglagesApplication(context))
        } catch (e2: ActivityNotFoundException) {
            // Aucun écran de réglages disponible.
        }
    }
}

/**
 * Bloc d'état de la présence : service, dernière attestation, diagnostics.
 * Se met à jour au retour sur l'écran.
 */
@Composable
fun BlocPresence(afficherCompteurs: Boolean = true) {
    val context = LocalContext.current
    val etat by Journal.etat.collectAsStateWithLifecycle()
    var verification by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { verification++ }
    // Rafraîchit les textes relatifs (« il y a 2 min ») et les diagnostics.
    Sondage(15_000L) { verification++ }

    val bluetoothActif = remember(verification) { Diagnostic.bluetoothActif(context) }
    val bluetoothPermis = remember(verification) { Diagnostic.bluetoothPermis(context) }
    val batterieExclue = remember(verification) { Diagnostic.batterieExclue(context) }
    val toutVa = etat.serviceActif && bluetoothActif && bluetoothPermis

    Bloc {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Point(if (toutVa) Palette.Present else Palette.Absent, 9.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (toutVa) R.string.presence_active else R.string.presence_inactive),
                style = Styles.SousTitre,
            )
        }
        Spacer(Modifier.height(6.dp))
        val derniere = if (etat.derniereReussiteMs > 0) {
            stringResource(
                R.string.derniere_attestation,
                Format.ilYa(etat.derniereReussiteMs),
                etat.derniereSalle.toString(),
            )
        } else {
            stringResource(R.string.aucune_attestation)
        }
        Text(derniere, style = Styles.Corps.copy(color = Palette.Secondaire, fontFeatureSettings = "tnum"))
        if (afficherCompteurs && (etat.reussies > 0 || etat.echecs > 0)) {
            Text(
                stringResource(R.string.compteurs_attestations, etat.reussies, etat.echecs),
                style = Styles.Petit.copy(fontFeatureSettings = "tnum"),
            )
        }
        if (etat.dernierProbleme.isNotBlank() && !toutVa) {
            Spacer(Modifier.height(4.dp))
            Text(etat.dernierProbleme, style = Styles.Petit.copy(color = Palette.Absent))
        }

        Spacer(Modifier.height(12.dp))
        Filet()
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth()) {
            LigneDiagnostic(
                libelle = stringResource(R.string.diag_bluetooth),
                ok = bluetoothActif,
                detail = stringResource(if (bluetoothActif) R.string.diag_bluetooth_ok else R.string.diag_bluetooth_ko),
                action = if (bluetoothActif) null else stringResource(R.string.action_activer),
                surAction = { ouvrir(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            )
            if (!bluetoothPermis) {
                LigneDiagnostic(
                    libelle = stringResource(R.string.diag_permissions),
                    ok = false,
                    detail = stringResource(R.string.diag_permissions_ko),
                    action = stringResource(R.string.action_reglages),
                    surAction = { ouvrir(context, Diagnostic.intentionReglagesApplication(context)) },
                )
            }
            LigneDiagnostic(
                libelle = stringResource(R.string.diag_batterie),
                ok = batterieExclue,
                detail = stringResource(if (batterieExclue) R.string.diag_batterie_ok else R.string.diag_batterie_ko),
                action = if (batterieExclue) null else stringResource(R.string.action_exclure),
                surAction = { ouvrir(context, Diagnostic.intentionExclusionBatterie(context)) },
            )
            LigneDiagnostic(
                libelle = stringResource(R.string.diag_service),
                ok = etat.serviceActif,
                detail = stringResource(
                    when {
                        !etat.serviceActif -> R.string.diag_service_ko
                        etat.rechercheActive -> R.string.diag_service_recherche
                        else -> R.string.diag_service_attente
                    },
                ),
                action = if (etat.serviceActif) null else stringResource(R.string.action_demarrer),
                surAction = {
                    PresenceService.demarrer(context)
                    verification++
                },
            )
        }
    }
}
