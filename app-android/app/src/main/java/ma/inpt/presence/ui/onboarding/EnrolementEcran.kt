package ma.inpt.presence.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ma.inpt.presence.R
import ma.inpt.presence.donnees.Identite
import ma.inpt.presence.donnees.Stockage
import ma.inpt.presence.reseau.Enrolement
import ma.inpt.presence.ui.commun.Marque
import ma.inpt.presence.ui.commun.essayer
import ma.inpt.presence.ui.commun.messageDe
import ma.inpt.presence.ui.composants.Champ
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.PrimaryButton
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles

/** Premier lancement : adresse du serveur, matricule et code d'enrôlement. */
@Composable
fun EnrolementEcran(stockage: Stockage, surEnrole: (Identite) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var serveur by rememberSaveable { mutableStateOf(stockage.serveur) }
    var matricule by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    val complet = serveur.isNotBlank() && matricule.isNotBlank() && code.isNotBlank()

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeLaterale),
    ) {
        Spacer(Modifier.height(56.dp))
        Marque(36.dp)
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.enrolement_titre), style = Styles.Titre)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.enrolement_texte), style = Styles.Corps.copy(color = Palette.Secondaire))

        Spacer(Modifier.height(32.dp))
        Champ(
            libelle = stringResource(R.string.champ_serveur),
            valeur = serveur,
            surChangement = { serveur = it.trim(); erreur = null },
            indication = Stockage.SERVEUR_PAR_DEFAUT,
            clavier = KeyboardType.Uri,
            enabled = !enCours,
        )
        Spacer(Modifier.height(18.dp))
        Champ(
            libelle = stringResource(R.string.champ_matricule),
            valeur = matricule,
            surChangement = { matricule = it.trim(); erreur = null },
            indication = "2025001",
            clavier = KeyboardType.Ascii,
            enabled = !enCours,
        )
        Spacer(Modifier.height(18.dp))
        Champ(
            libelle = stringResource(R.string.champ_code),
            valeur = code,
            surChangement = { code = it.trim().uppercase(); erreur = null },
            indication = "K7P-4QX",
            clavier = KeyboardType.Ascii,
            majuscules = true,
            imeAction = ImeAction.Done,
            enabled = !enCours,
        )

        val message = erreur
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(message, style = Styles.Corps.copy(color = Palette.Absent))
        }

        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            texte = stringResource(R.string.action_enroler),
            enabled = complet,
            chargement = enCours,
            onClick = {
                enCours = true
                erreur = null
                stockage.serveur = serveur.trimEnd('/')
                portee.launch {
                    essayer { Enrolement.enroler(context, serveur, matricule, code) }
                        .onSuccess { surEnrole(it) }
                        .onFailure { erreur = messageDe(it) }
                    enCours = false
                }
            },
        )
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.enrolement_note), style = Styles.Petit)
        Spacer(Modifier.height(32.dp))
    }
}
