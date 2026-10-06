package ma.inpt.presence.ui.etudiant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ma.inpt.presence.R
import ma.inpt.presence.reseau.Lecture
import ma.inpt.presence.reseau.Seance
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.commun.BlocPresence
import ma.inpt.presence.ui.commun.ContexteApp
import ma.inpt.presence.ui.commun.Etat
import ma.inpt.presence.ui.commun.Sondage
import ma.inpt.presence.ui.commun.essayer
import ma.inpt.presence.ui.commun.messageDe
import ma.inpt.presence.ui.composants.Bloc
import ma.inpt.presence.ui.composants.Chargement
import ma.inpt.presence.ui.composants.EnTete
import ma.inpt.presence.ui.composants.EtatVide
import ma.inpt.presence.ui.composants.Filet
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.SectionLabel
import ma.inpt.presence.ui.composants.StatusChip
import ma.inpt.presence.ui.composants.WindowsGrid
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles
import org.json.JSONObject

/** Accueil de l'étudiant : état de la présence et séance en cours. */
@Composable
fun AccueilEcran(ctx: ContexteApp) {
    var seance by remember { mutableStateOf<Etat<Seance?>>(Etat.Chargement) }

    Sondage(30_000L) {
        essayer { ctx.api.get("/moi/seance-courante") }
            .onSuccess { reponse ->
                val objet = reponse as? JSONObject
                seance = Etat.Pret(objet?.let { Lecture.seanceEnveloppee(it) })
            }
            .onFailure { e ->
                // On garde la dernière séance affichée si un sondage échoue.
                if (seance !is Etat.Pret) seance = Etat.Erreur(messageDe(e))
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeLaterale),
    ) {
        EnTete(
            titre = stringResource(R.string.bonjour, ctx.identite.prenom.ifBlank { ctx.identite.nomComplet }),
            sousTitre = Format.dateLongue(System.currentTimeMillis()),
        )
        Spacer(Modifier.height(12.dp))
        BlocPresence()

        SectionLabel(stringResource(R.string.section_seance_courante))
        when (val e = seance) {
            is Etat.Chargement -> Chargement()
            is Etat.Erreur -> EtatVide(stringResource(R.string.erreur_chargement), e.message)
            is Etat.Pret -> {
                val s = e.valeur
                if (s == null) {
                    EtatVide(stringResource(R.string.aucune_seance), stringResource(R.string.aucune_seance_texte))
                } else {
                    BlocSeanceCourante(s)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BlocSeanceCourante(s: Seance) {
    Bloc {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(s.libelleCours, style = Styles.SousTitre)
                Spacer(Modifier.height(2.dp))
                val details = listOfNotNull(
                    s.libelleSalle.takeIf { it.isNotBlank() }?.let { "Salle $it" },
                    Format.plage(s.debutMs, s.finMs).takeIf { it.isNotBlank() },
                    Format.libelleEtat(s.etat).takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(details, style = Styles.Petit.copy(fontFeatureSettings = "tnum"))
            }
            if (s.statut.isNotBlank()) StatusChip(s.statut)
        }
        Spacer(Modifier.height(16.dp))
        Filet()
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.fenetres), style = Styles.Section)
        Spacer(Modifier.height(10.dp))
        val f = s.fenetres
        if (f == null) {
            Text(stringResource(R.string.fenetres_indisponibles), style = Styles.Petit)
        } else {
            WindowsGrid(total = f.total, validees = f.indices, nbValidees = f.nbValidees)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.statut_provisoire_note), style = Styles.Petit.copy(color = Palette.Discret))
        }
    }
}
