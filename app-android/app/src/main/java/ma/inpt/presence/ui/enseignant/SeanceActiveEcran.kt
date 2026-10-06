package ma.inpt.presence.ui.enseignant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ma.inpt.presence.R
import ma.inpt.presence.reseau.Direct
import ma.inpt.presence.reseau.Inscrit
import ma.inpt.presence.reseau.Lecture
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.commun.ContexteApp
import ma.inpt.presence.ui.commun.Etat
import ma.inpt.presence.ui.commun.Sondage
import ma.inpt.presence.ui.commun.essayer
import ma.inpt.presence.ui.commun.messageDe
import ma.inpt.presence.ui.composants.Bloc
import ma.inpt.presence.ui.composants.Chargement
import ma.inpt.presence.ui.composants.Compteur
import ma.inpt.presence.ui.composants.Confirmation
import ma.inpt.presence.ui.composants.EnTete
import ma.inpt.presence.ui.composants.EtatVide
import ma.inpt.presence.ui.composants.Filet
import ma.inpt.presence.ui.composants.Lien
import ma.inpt.presence.ui.composants.LigneListe
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.MessageErreur
import ma.inpt.presence.ui.composants.PrimaryButton
import ma.inpt.presence.ui.composants.SecondaryButton
import ma.inpt.presence.ui.composants.SectionLabel
import ma.inpt.presence.ui.composants.StatusChip
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles
import org.json.JSONObject

/** Charge le suivi en direct d'une séance. */
suspend fun chargerDirect(ctx: ContexteApp, id: String): Etat<Direct> =
    essayer { ctx.api.get("/seances/$id/direct") }.fold(
        onSuccess = { reponse ->
            val o = reponse as? JSONObject
            if (o == null) Etat.Erreur("Réponse vide du serveur") else Etat.Pret(Lecture.direct(o))
        },
        onFailure = { Etat.Erreur(messageDe(it)) },
    )

private val ORDRE_STATUTS = listOf("A_VERIFIER", "ABSENT", "NON_DETECTE", "PARTIEL", "DEPART_ANTICIPE", "RETARD", "PRESENT")

fun trierInscrits(inscrits: List<Inscrit>): List<Inscrit> =
    inscrits.sortedWith(
        compareBy<Inscrit>({ ORDRE_STATUTS.indexOf(it.statut.uppercase()).let { i -> if (i < 0) ORDRE_STATUTS.size else i } })
            .thenBy { it.nom.lowercase() }
            .thenBy { it.prenom.lowercase() },
    )

/** Séance active : compteurs, liste des inscrits, pause, reprise et fin. */
@Composable
fun SeanceActiveEcran(ctx: ContexteApp, idSeance: String) {
    val portee = rememberCoroutineScope()
    var etat by remember { mutableStateOf<Etat<Direct>>(Etat.Chargement) }
    var rafraichir by remember { mutableIntStateOf(0) }
    var action by remember { mutableStateOf<String?>(null) }
    var confirmerFin by remember { mutableStateOf(false) }

    Sondage(10_000L, cle = rafraichir) {
        val resultat = chargerDirect(ctx, idSeance)
        if (resultat is Etat.Pret || etat !is Etat.Pret) etat = resultat
    }

    fun agir(nom: String, chemin: String, apres: (() -> Unit)? = null) {
        action = nom
        portee.launch {
            essayer { ctx.api.post(chemin) }
                .onSuccess {
                    if (apres != null) apres() else rafraichir++
                }
                .onFailure { ctx.notifier(messageDe(it)) }
            action = null
        }
    }

    when (val e = etat) {
        is Etat.Chargement -> Column(Modifier.padding(horizontal = MargeLaterale)) {
            Lien(stringResource(R.string.action_retour), ctx.retour, couleur = Palette.Secondaire)
            Chargement()
        }
        is Etat.Erreur -> Column(Modifier.padding(horizontal = MargeLaterale)) {
            Lien(stringResource(R.string.action_retour), ctx.retour, couleur = Palette.Secondaire)
            MessageErreur(e.message) { rafraichir++ }
        }
        is Etat.Pret -> {
            val d = e.valeur
            val s = d.seance
            val etatSeance = s.etat.uppercase()
            val inscrits = trierInscrits(d.inscrits)
            val presents = d.inscrits.count { it.statut.uppercase() == "PRESENT" }
            val retards = d.inscrits.count { it.statut.uppercase() == "RETARD" }
            val aVerifier = d.inscrits.count { it.statut.uppercase() == "A_VERIFIER" }
            val nonDetectes = d.inscrits.count { it.statut.uppercase() == "ABSENT" || it.statut.uppercase() == "NON_DETECTE" }

            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = MargeLaterale),
                ) {
                    item {
                        Lien(stringResource(R.string.action_retour), ctx.retour, couleur = Palette.Secondaire)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                EnTete(
                                    s.libelleCours.ifBlank { idSeance },
                                    listOf("Salle ${s.libelleSalle}", Format.plage(s.debutMs, 0))
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                )
                            }
                            StatusChip(
                                Format.statutEtat(etatSeance),
                                libelle = Format.libelleEtat(etatSeance),
                                modifier = Modifier.padding(bottom = 14.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Bloc {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Compteur(presents, stringResource(R.string.compte_presents), Palette.Present)
                                Compteur(retards, stringResource(R.string.compte_retards), Palette.Retard)
                                Compteur(aVerifier, stringResource(R.string.compte_a_verifier), Palette.AVerifier)
                                Compteur(nonDetectes, stringResource(R.string.compte_non_detectes), Palette.Absent)
                            }
                            val f = s.fenetres
                            if (f != null && f.total > 0) {
                                Spacer(Modifier.height(12.dp))
                                Filet()
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(R.string.fenetres_ecoulees, f.total, d.inscrits.size),
                                    style = Styles.Petit.copy(fontFeatureSettings = "tnum"),
                                )
                            }
                        }
                        SectionLabel(stringResource(R.string.section_inscrits))
                        Filet()
                    }
                    if (inscrits.isEmpty()) {
                        item { EtatVide(stringResource(R.string.inscrits_vide), stringResource(R.string.inscrits_vide_texte)) }
                    }
                    items(inscrits) { i ->
                        LigneListe(
                            titre = i.nomComplet,
                            detail = listOfNotNull(
                                i.matricule.takeIf { it.isNotBlank() },
                                stringResource(R.string.n_fenetres, i.fenetresValidees),
                                if (i.manuel) stringResource(R.string.validation_manuelle) else null,
                            ).joinToString(" · "),
                            fin = { StatusChip(i.statut) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MargeLaterale),
                ) {
                    Filet()
                    Spacer(Modifier.height(12.dp))
                    when (etatSeance) {
                        "ACTIVE", "PAUSE" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (etatSeance == "ACTIVE") {
                                SecondaryButton(
                                    texte = stringResource(R.string.action_pause),
                                    onClick = { agir("pause", "/seances/$idSeance/pause") },
                                    enabled = action == null,
                                    chargement = action == "pause",
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                SecondaryButton(
                                    texte = stringResource(R.string.action_reprendre),
                                    onClick = { agir("reprise", "/seances/$idSeance/reprise") },
                                    enabled = action == null,
                                    chargement = action == "reprise",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            PrimaryButton(
                                texte = stringResource(R.string.action_terminer),
                                onClick = { confirmerFin = true },
                                enabled = action == null,
                                chargement = action == "cloture",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        else -> PrimaryButton(
                            texte = stringResource(R.string.action_aller_cloture),
                            onClick = { ctx.naviguer("cloture/$idSeance") },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (confirmerFin) {
        Confirmation(
            titre = stringResource(R.string.terminer_titre),
            texte = stringResource(R.string.terminer_texte),
            confirmer = stringResource(R.string.action_terminer),
            surConfirmer = {
                confirmerFin = false
                agir("cloture", "/seances/$idSeance/cloture") { ctx.naviguer("cloture/$idSeance") }
            },
            surAnnuler = { confirmerFin = false },
        )
    }
}
