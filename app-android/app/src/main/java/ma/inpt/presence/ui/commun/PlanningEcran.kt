package ma.inpt.presence.ui.commun

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import ma.inpt.presence.R
import ma.inpt.presence.reseau.Creneau
import ma.inpt.presence.reseau.Json
import ma.inpt.presence.reseau.LecturePlanning
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.composants.Chargement
import ma.inpt.presence.ui.composants.EnTete
import ma.inpt.presence.ui.composants.EtatVide
import ma.inpt.presence.ui.composants.LigneListe
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.MessageErreur
import ma.inpt.presence.ui.composants.SectionLabel
import ma.inpt.presence.ui.composants.StatusChip

private suspend fun chargerPlanning(ctx: ContexteApp): Etat<List<Creneau>> =
    essayer { ctx.api.get("/moi/planning") }.fold(
        onSuccess = { reponse ->
            Etat.Pret(
                Json.listeObjets(reponse, "creneaux", "planning", "elements")
                    .map { LecturePlanning.creneau(it) }
                    .sortedBy { it.debutMs },
            )
        },
        onFailure = { Etat.Erreur(messageDe(it)) },
    )

private fun jourDe(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** Emploi du temps des cours de la personne, mis à jour toutes les minutes. */
@Composable
fun PlanningEcran(ctx: ContexteApp) {
    var essai by remember { mutableIntStateOf(0) }
    var etat by remember { mutableStateOf<Etat<List<Creneau>>>(Etat.Chargement) }
    Sondage(intervalleMs = 60_000L, cle = essai) {
        val suivant = chargerPlanning(ctx)
        // Une erreur de rafraîchissement ne remplace pas un planning déjà affiché.
        if (suivant !is Etat.Erreur || etat !is Etat.Pret) etat = suivant
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = MargeLaterale),
    ) {
        item { EnTete(stringResource(R.string.titre_planning), stringResource(R.string.planning_sous_titre)) }
        when (val e = etat) {
            is Etat.Chargement -> item { Chargement() }
            is Etat.Erreur -> item { MessageErreur(e.message) { essai++ } }
            is Etat.Pret -> {
                if (e.valeur.isEmpty()) {
                    item { EtatVide(stringResource(R.string.planning_vide), stringResource(R.string.planning_vide_texte)) }
                } else {
                    e.valeur.groupBy { jourDe(it.debutMs) }.forEach { (_, creneaux) ->
                        item { SectionLabel(Format.dateLongue(creneaux.first().debutMs)) }
                        items(creneaux.size) { i ->
                            val c = creneaux[i]
                            LigneListe(
                                titre = c.libelleCours,
                                detail = listOfNotNull(
                                    Format.plage(c.debutMs, c.finMs),
                                    c.nomSalle.takeIf { it.isNotBlank() }?.let { "Salle $it" },
                                    c.enseignant.takeIf { it.isNotBlank() && ctx.identite.estEnseignant.not() },
                                ).joinToString(" · "),
                                fin = if (c.annule) ({ StatusChip("ABSENT", libelle = stringResource(R.string.planning_annule)) }) else null,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
