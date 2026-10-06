package ma.inpt.presence.ui.commun

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ma.inpt.presence.BuildConfig
import ma.inpt.presence.R
import ma.inpt.presence.donnees.EntreeJournal
import ma.inpt.presence.donnees.Journal
import ma.inpt.presence.donnees.Partage
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.composants.Bloc
import ma.inpt.presence.ui.composants.Confirmation
import ma.inpt.presence.ui.composants.EnTete
import ma.inpt.presence.ui.composants.Filet
import ma.inpt.presence.ui.composants.InfoRow
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.Point
import ma.inpt.presence.ui.composants.SecondaryButton
import ma.inpt.presence.ui.composants.SectionLabel
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles

/** Profil : identité, appareil, diagnostics, journal des attestations et déconnexion. */
@Composable
fun ProfilEcran(ctx: ContexteApp, surDeconnexion: () -> Unit) {
    val context = LocalContext.current
    val identite = ctx.identite
    val journal by Journal.entrees.collectAsStateWithLifecycle()
    var confirmer by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeLaterale),
    ) {
        EnTete(identite.nomComplet.ifBlank { identite.matricule }, Format.role(identite.role))

        SectionLabel(stringResource(R.string.section_identite))
        Bloc {
            InfoRow(stringResource(R.string.matricule), identite.matricule)
            InfoRow(stringResource(R.string.role), Format.role(identite.role))
            InfoRow(stringResource(R.string.serveur), identite.serveur)
        }

        SectionLabel(stringResource(R.string.section_appareil))
        Bloc {
            InfoRow(stringResource(R.string.identifiant_appareil), identite.idAppareil, styleValeur = Styles.Mono)
            InfoRow(stringResource(R.string.modele), identite.modele)
            InfoRow(
                stringResource(R.string.stockage_cle),
                stringResource(if (identite.strongBox) R.string.cle_strongbox else R.string.cle_tee),
            )
            InfoRow(
                stringResource(R.string.deverrouillage_recent),
                stringResource(if (identite.deverrouillageRecent) R.string.exige else R.string.non_exige),
            )
        }

        SectionLabel(stringResource(R.string.section_diagnostic))
        BlocPresence(afficherCompteurs = true)

        SectionLabel(stringResource(R.string.section_journal))
        val dernieres = journal.take(50)
        if (dernieres.isEmpty()) {
            Text(stringResource(R.string.journal_vide), style = Styles.Corps.copy(color = Palette.Secondaire))
        } else {
            Bloc {
                dernieres.forEachIndexed { i, entree ->
                    if (i > 0) Filet()
                    LigneJournal(entree)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton(
            texte = stringResource(R.string.action_exporter_journal),
            onClick = {
                val ok = Partage.partagerCopie(
                    context,
                    Journal.fichier(context),
                    "journal-presence-${identite.idAppareil}.csv",
                    "text/csv",
                    context.getString(R.string.exporter_journal_titre),
                )
                if (!ok) ctx.notifier(context.getString(R.string.partage_impossible))
            },
        )

        SectionLabel(stringResource(R.string.section_compte))
        SecondaryButton(
            texte = stringResource(R.string.action_deconnexion),
            couleur = Palette.Absent,
            onClick = { confirmer = true },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.mentions, BuildConfig.VERSION_NAME),
            style = Styles.Petit.copy(color = Palette.Discret),
        )
        Spacer(Modifier.height(32.dp))
    }

    if (confirmer) {
        Confirmation(
            titre = stringResource(R.string.deconnexion_titre),
            texte = stringResource(R.string.deconnexion_texte),
            confirmer = stringResource(R.string.action_deconnexion_confirmer),
            surConfirmer = {
                confirmer = false
                surDeconnexion()
            },
            surAnnuler = { confirmer = false },
            destructif = true,
        )
    }
}

@Composable
private fun LigneJournal(entree: EntreeJournal) {
    val couleur = when (entree.resultat) {
        EntreeJournal.RESULTAT_OK -> Palette.Present
        EntreeJournal.RESULTAT_IGNORE -> Palette.Partiel
        else -> Palette.Absent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Point(couleur, 6.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val titre = buildString {
                append(Format.horodatage(entree.horodatage))
                if (entree.salle > 0) append("  ·  salle ").append(entree.salle)
                if (entree.seance > 0) append("  ·  séance ").append(entree.seance)
            }
            Text(titre, style = Styles.Corps.copy(fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = if (entree.reussie) {
                "Attestation acceptée · ${entree.observateur}"
            } else {
                "${entree.erreur.ifBlank { entree.resultat }} · ${entree.observateur}"
            }
            Text(detail, style = Styles.Petit, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
