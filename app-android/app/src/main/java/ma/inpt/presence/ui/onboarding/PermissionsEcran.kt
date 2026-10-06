package ma.inpt.presence.ui.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ma.inpt.presence.R
import ma.inpt.presence.service.Diagnostic
import ma.inpt.presence.ui.commun.ouvrir
import ma.inpt.presence.ui.composants.Bloc
import ma.inpt.presence.ui.composants.Filet
import ma.inpt.presence.ui.composants.LigneDiagnostic
import ma.inpt.presence.ui.composants.Lien
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.PrimaryButton
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles

/** Autorisations nécessaires à l'attestation en arrière-plan. */
@Composable
fun PermissionsEcran(surTermine: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var verification by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { verification++ }

    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        verification++
    }

    val bluetoothPermis = remember(verification) { Diagnostic.bluetoothPermis(context) }
    val notificationsPermises = remember(verification) { Diagnostic.notificationsPermises(context) }
    val batterieExclue = remember(verification) { Diagnostic.batterieExclue(context) }
    val bluetoothActif = remember(verification) { Diagnostic.bluetoothActif(context) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeLaterale),
    ) {
        Spacer(Modifier.height(56.dp))
        Text(stringResource(R.string.permissions_titre), style = Styles.Titre)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.permissions_texte), style = Styles.Corps.copy(color = Palette.Secondaire))
        Spacer(Modifier.height(28.dp))

        Bloc {
            LigneDiagnostic(
                libelle = stringResource(R.string.perm_bluetooth),
                ok = bluetoothPermis,
                detail = stringResource(R.string.perm_bluetooth_detail),
                action = if (bluetoothPermis) null else stringResource(R.string.action_autoriser),
                surAction = { lanceur.launch(Diagnostic.permissionsADemander()) },
            )
            Filet()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LigneDiagnostic(
                    libelle = stringResource(R.string.perm_notifications),
                    ok = notificationsPermises,
                    detail = stringResource(R.string.perm_notifications_detail),
                    action = if (notificationsPermises) null else stringResource(R.string.action_autoriser),
                    surAction = { lanceur.launch(Diagnostic.permissionsADemander()) },
                )
                Filet()
            }
            LigneDiagnostic(
                libelle = stringResource(R.string.perm_batterie),
                ok = batterieExclue,
                detail = stringResource(R.string.perm_batterie_detail),
                action = if (batterieExclue) null else stringResource(R.string.action_exclure),
                surAction = { ouvrir(context, Diagnostic.intentionExclusionBatterie(context)) },
            )
            Filet()
            LigneDiagnostic(
                libelle = stringResource(R.string.diag_bluetooth),
                ok = bluetoothActif,
                detail = stringResource(if (bluetoothActif) R.string.diag_bluetooth_ok else R.string.diag_bluetooth_ko),
                action = if (bluetoothActif) null else stringResource(R.string.action_activer),
                surAction = { ouvrir(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            )
        }

        if (!bluetoothPermis) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.permissions_refusees), style = Styles.Petit)
            Lien(
                stringResource(R.string.action_reglages_application),
                { ouvrir(context, Diagnostic.intentionReglagesApplication(context)) },
            )
        }

        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            texte = stringResource(if (bluetoothPermis) R.string.action_continuer else R.string.action_continuer_sans),
            onClick = surTermine,
        )
        Spacer(Modifier.height(32.dp))
    }
}
