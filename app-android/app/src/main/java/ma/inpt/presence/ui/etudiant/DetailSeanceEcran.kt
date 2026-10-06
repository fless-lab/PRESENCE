package ma.inpt.presence.ui.etudiant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ma.inpt.presence.R
import ma.inpt.presence.donnees.Partage
import ma.inpt.presence.recu.Verification
import ma.inpt.presence.recu.VerificationRecu
import ma.inpt.presence.reseau.Seance
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.commun.ContexteApp
import ma.inpt.presence.ui.commun.Etat
import ma.inpt.presence.ui.commun.essayer
import ma.inpt.presence.ui.commun.messageDe
import ma.inpt.presence.ui.composants.Bloc
import ma.inpt.presence.ui.composants.Chargement
import ma.inpt.presence.ui.composants.EnTete
import ma.inpt.presence.ui.composants.Filet
import ma.inpt.presence.ui.composants.InfoRow
import ma.inpt.presence.ui.composants.Lien
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.MessageErreur
import ma.inpt.presence.ui.composants.Point
import ma.inpt.presence.ui.composants.PrimaryButton
import ma.inpt.presence.ui.composants.SecondaryButton
import ma.inpt.presence.ui.composants.SectionLabel
import ma.inpt.presence.ui.composants.StatusChip
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles

/** Détail d'une séance passée et vérification locale du reçu. */
@Composable
fun DetailSeanceEcran(ctx: ContexteApp, idSeance: String) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var essai by remember { mutableIntStateOf(0) }
    var etat by remember { mutableStateOf<Etat<Seance?>>(Etat.Chargement) }
    var verification by remember { mutableStateOf<Verification?>(null) }
    var verificationEnCours by remember { mutableStateOf(false) }
    var erreurRecu by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(essai) {
        etat = Etat.Chargement
        etat = when (val h = chargerHistorique(ctx, "/moi/historique")) {
            is Etat.Pret -> Etat.Pret(h.valeur.firstOrNull { it.id == idSeance })
            is Etat.Erreur -> h
            is Etat.Chargement -> h
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeLaterale),
    ) {
        Lien(stringResource(R.string.action_retour), ctx.retour, couleur = Palette.Secondaire)
        when (val e = etat) {
            is Etat.Chargement -> Chargement()
            is Etat.Erreur -> MessageErreur(e.message) { essai++ }
            is Etat.Pret -> {
                val s = e.valeur
                EnTete(s?.libelleCours ?: idSeance, s?.let { Format.dateLongue(it.debutMs) })
                if (s != null) {
                    Spacer(Modifier.height(8.dp))
                    Bloc {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.statut), style = Styles.Corps.copy(color = Palette.Secondaire), modifier = Modifier.weight(1f))
                            StatusChip(s.statut)
                        }
                        Spacer(Modifier.height(6.dp))
                        InfoRow(stringResource(R.string.salle), s.libelleSalle)
                        InfoRow(stringResource(R.string.horaire), Format.plage(s.debutMs, s.finMs))
                        val f = s.fenetres
                        if (f != null && f.total > 0) {
                            InfoRow(stringResource(R.string.fenetres), "${f.nbValidees} sur ${f.total}")
                        }
                        InfoRow(stringResource(R.string.identifiant_seance), s.id, styleValeur = Styles.Mono)
                    }
                }

                SectionLabel(stringResource(R.string.section_recu))
                Text(stringResource(R.string.recu_explication), style = Styles.Corps.copy(color = Palette.Secondaire))
                Spacer(Modifier.height(16.dp))

                val v = verification
                if (v == null) {
                    PrimaryButton(
                        texte = stringResource(R.string.action_recu),
                        chargement = verificationEnCours,
                        onClick = {
                            verificationEnCours = true
                            erreurRecu = null
                            portee.launch {
                                essayer { VerificationRecu.verifier(ctx.api, idSeance) }
                                    .onSuccess { verification = it }
                                    .onFailure { erreurRecu = messageDe(it) }
                                verificationEnCours = false
                            }
                        },
                    )
                    erreurRecu?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = Styles.Corps.copy(color = Palette.Absent))
                    }
                } else {
                    BlocVerification(v)
                    Spacer(Modifier.height(16.dp))
                    SecondaryButton(
                        texte = stringResource(R.string.action_partager_recu),
                        onClick = {
                            val ok = Partage.partagerTexte(
                                context,
                                "recu-${idSeance}.json",
                                v.texteRecu,
                                "application/json",
                                context.getString(R.string.partager_recu_titre),
                            )
                            if (!ok) ctx.notifier(context.getString(R.string.partage_impossible))
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BlocVerification(v: Verification) {
    Bloc {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Point(if (v.verifie) Palette.Present else Palette.Absent, 9.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (v.verifie) R.string.recu_verifie else R.string.recu_non_verifie),
                style = Styles.SousTitre,
            )
        }
        Spacer(Modifier.height(12.dp))
        InfoRow(stringResource(R.string.statut_recu), Format.libelleStatut(v.statut))
        if (v.fenetresTotal > 0) {
            InfoRow(stringResource(R.string.fenetres), "${v.fenetresValidees} sur ${v.fenetresTotal}")
        }
        InfoRow(
            stringResource(R.string.signature_serveur),
            stringResource(if (v.signatureValide) R.string.valide else R.string.invalide),
        )
        InfoRow(stringResource(R.string.preuves_inclusion), "${v.preuvesValides} sur ${v.preuvesTotal}")
        InfoRow(
            stringResource(R.string.ancrage_registre),
            stringResource(if (v.ancrageConforme) R.string.racine_identique else R.string.racine_differente),
        )
        Spacer(Modifier.height(8.dp))
        Filet()
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.racine), style = Styles.Section)
        Spacer(Modifier.height(4.dp))
        Text(v.racine.ifBlank { "Non fournie" }, style = Styles.Mono)
        if (v.transaction.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.transaction), style = Styles.Section)
            Spacer(Modifier.height(4.dp))
            Text(v.transaction, style = Styles.Mono)
        }
        if (v.remarques.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            v.remarques.forEach { Text(it, style = Styles.Petit.copy(color = Palette.Absent)) }
        }
    }
}
