package ma.inpt.presence.ui.enseignant

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ma.inpt.presence.R
import ma.inpt.presence.reseau.Seance
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.commun.ContexteApp
import ma.inpt.presence.ui.commun.Etat
import ma.inpt.presence.ui.composants.Chargement
import ma.inpt.presence.ui.composants.EnTete
import ma.inpt.presence.ui.composants.EtatVide
import ma.inpt.presence.ui.composants.LigneListe
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.MessageErreur
import ma.inpt.presence.ui.composants.StatusChip
import ma.inpt.presence.ui.etudiant.chargerHistorique
import ma.inpt.presence.ui.etudiant.detailSeance

/** Séances récentes de l'enseignant. */
@Composable
fun HistoriqueEnseignantEcran(ctx: ContexteApp) {
    var essai by remember { mutableIntStateOf(0) }
    var etat by remember { mutableStateOf<Etat<List<Seance>>>(Etat.Chargement) }
    LaunchedEffect(essai) {
        etat = Etat.Chargement
        etat = chargerHistorique(ctx, "/enseignant/seances")
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = MargeLaterale),
    ) {
        item { EnTete(stringResource(R.string.titre_historique), stringResource(R.string.historique_enseignant_sous_titre)) }
        when (val e = etat) {
            is Etat.Chargement -> item { Chargement() }
            is Etat.Erreur -> item { MessageErreur(e.message) { essai++ } }
            is Etat.Pret -> {
                if (e.valeur.isEmpty()) {
                    item {
                        EtatVide(
                            stringResource(R.string.historique_vide),
                            stringResource(R.string.historique_enseignant_vide_texte),
                        )
                    }
                } else {
                    items(e.valeur) { s ->
                        val ouverte = s.etat.uppercase() == "ACTIVE" || s.etat.uppercase() == "PAUSE"
                        LigneListe(
                            titre = s.libelleCours,
                            detail = detailSeance(s),
                            surClic = if (s.id.isBlank()) null else ({
                                ctx.naviguer(if (ouverte) "active/${s.id}" else "cloture/${s.id}")
                            }),
                            fin = {
                                StatusChip(Format.statutEtat(s.etat), libelle = Format.libelleEtat(s.etat).ifBlank { "Séance" })
                            },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
