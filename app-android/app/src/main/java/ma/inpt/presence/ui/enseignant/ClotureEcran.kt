package ma.inpt.presence.ui.enseignant

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ma.inpt.presence.R
import ma.inpt.presence.reseau.Direct
import ma.inpt.presence.reseau.Inscrit
import ma.inpt.presence.reseau.Lecture
import ma.inpt.presence.reseau.Scellement
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.commun.ContexteApp
import ma.inpt.presence.ui.commun.Etat
import ma.inpt.presence.ui.commun.Sondage
import ma.inpt.presence.ui.commun.essayer
import ma.inpt.presence.ui.commun.messageDe
import ma.inpt.presence.ui.composants.Bloc
import ma.inpt.presence.ui.composants.Chargement
import ma.inpt.presence.ui.composants.Confirmation
import ma.inpt.presence.ui.composants.DialogueMotif
import ma.inpt.presence.ui.composants.EnTete
import ma.inpt.presence.ui.composants.Filet
import ma.inpt.presence.ui.composants.InfoRow
import ma.inpt.presence.ui.composants.Lien
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.MessageErreur
import ma.inpt.presence.ui.composants.Point
import ma.inpt.presence.ui.composants.PrimaryButton
import ma.inpt.presence.ui.composants.SectionLabel
import ma.inpt.presence.ui.composants.StatusChip
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles
import org.json.JSONObject

/** Clôture : arbitrage des « À vérifier », validations manuelles, puis scellement. */
@Composable
fun ClotureEcran(ctx: ContexteApp, idSeance: String) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var etat by remember { mutableStateOf<Etat<Direct>>(Etat.Chargement) }
    var rafraichir by remember { mutableIntStateOf(0) }
    var enCours by remember { mutableStateOf<String?>(null) }
    var aValider by remember { mutableStateOf<Inscrit?>(null) }
    var motif by remember { mutableStateOf("") }
    var confirmerScellement by remember { mutableStateOf(false) }
    var scellement by remember { mutableStateOf<Scellement?>(null) }

    // Le suivi reste à jour tant que l'écran est visible : une décision prise ailleurs apparaît aussi.
    Sondage(15_000L, cle = rafraichir) {
        val resultat = chargerDirect(ctx, idSeance)
        if (resultat is Etat.Pret || etat !is Etat.Pret) etat = resultat
    }

    fun envoyer(cle: String, chemin: String, corps: JSONObject?, succes: String) {
        enCours = cle
        portee.launch {
            essayer { ctx.api.post(chemin, corps) }
                .onSuccess {
                    ctx.notifier(succes)
                    rafraichir++
                }
                .onFailure { ctx.notifier(messageDe(it)) }
            enCours = null
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
            is Etat.Erreur -> MessageErreur(e.message) { rafraichir++ }
            is Etat.Pret -> {
                val s = e.valeur.seance
                val scellee = s.etat.uppercase() == "SCELLEE" || scellement != null
                val aVerifier = e.valeur.inscrits.filter { it.statut.uppercase() == "A_VERIFIER" }
                val absents = e.valeur.inscrits.filter {
                    val st = it.statut.uppercase()
                    st == "ABSENT" || st == "NON_DETECTE" || st == "PARTIEL"
                }
                val presents = e.valeur.inscrits.size - aVerifier.size - absents.size

                EnTete(
                    stringResource(R.string.titre_cloture),
                    listOf(s.libelleCours, Format.date(s.debutMs), Format.plage(s.debutMs, s.finMs))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                )
                Text(
                    stringResource(R.string.cloture_resume, presents, aVerifier.size, absents.size),
                    style = Styles.Corps.copy(color = Palette.Secondaire, fontFeatureSettings = "tnum"),
                )

                if (!scellee) {
                    SectionLabel(stringResource(R.string.section_a_verifier))
                    if (aVerifier.isEmpty()) {
                        Text(stringResource(R.string.a_verifier_vide), style = Styles.Corps.copy(color = Palette.Secondaire))
                    } else {
                        Bloc {
                            aVerifier.forEachIndexed { index, inscrit ->
                                if (index > 0) Filet()
                                LigneDecision(
                                    inscrit = inscrit,
                                    actif = enCours == null,
                                    surPresent = {
                                        envoyer(
                                            "decision-${inscrit.matricule}",
                                            "/seances/$idSeance/decisions",
                                            JSONObject()
                                                .put("matricule", inscrit.matricule)
                                                .put("decision", "PRESENT")
                                                .put("motif", context.getString(R.string.motif_confirme_present)),
                                            context.getString(R.string.decision_enregistree),
                                        )
                                    },
                                    surAbsent = {
                                        envoyer(
                                            "decision-${inscrit.matricule}",
                                            "/seances/$idSeance/decisions",
                                            JSONObject()
                                                .put("matricule", inscrit.matricule)
                                                .put("decision", "ABSENT")
                                                .put("motif", context.getString(R.string.motif_absent_constate)),
                                            context.getString(R.string.decision_enregistree),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    SectionLabel(stringResource(R.string.section_absents))
                    if (absents.isEmpty()) {
                        Text(stringResource(R.string.absents_vide), style = Styles.Corps.copy(color = Palette.Secondaire))
                    } else {
                        Bloc {
                            absents.forEachIndexed { index, inscrit ->
                                if (index > 0) Filet()
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(inscrit.nomComplet, style = Styles.CorpsFort, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(4.dp))
                                        StatusChip(inscrit.statut)
                                    }
                                    Lien(
                                        stringResource(R.string.action_valider_main),
                                        {
                                            motif = ""
                                            aValider = inscrit
                                        },
                                        enabled = enCours == null,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(R.string.scellement_explication), style = Styles.Petit)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        texte = stringResource(R.string.action_sceller),
                        onClick = { confirmerScellement = true },
                        enabled = enCours == null && aVerifier.isEmpty(),
                        chargement = enCours == "scellement",
                    )
                    if (aVerifier.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.sceller_apres_verification), style = Styles.Petit)
                    }
                } else {
                    SectionLabel(stringResource(R.string.section_scellement))
                    val racine = scellement?.racine?.ifBlank { null } ?: s.racine
                    val transaction = scellement?.transaction?.ifBlank { null } ?: s.transaction
                    Bloc {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Point(Palette.Present, 9.dp)
                            Spacer(Modifier.padding(start = 10.dp))
                            Text(stringResource(R.string.seance_scellee), style = Styles.SousTitre)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.racine), style = Styles.Section)
                        Spacer(Modifier.height(4.dp))
                        Text(racine.ifBlank { "Non fournie" }, style = Styles.Mono)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.transaction), style = Styles.Section)
                        Spacer(Modifier.height(4.dp))
                        Text(transaction.ifBlank { "Non fournie" }, style = Styles.Mono)
                        val registre = scellement?.registre.orEmpty()
                        if (registre.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            InfoRow(stringResource(R.string.registre), registre)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    val cible = aValider
    if (cible != null) {
        DialogueMotif(
            titre = stringResource(R.string.validation_titre, cible.nomComplet),
            texte = stringResource(R.string.validation_texte),
            motif = motif,
            surMotif = { motif = it },
            surValider = {
                val m = motif.trim()
                aValider = null
                envoyer(
                    "validation-${cible.matricule}",
                    "/seances/$idSeance/validations",
                    JSONObject().put("matricule", cible.matricule).put("motif", m),
                    context.getString(R.string.validation_enregistree),
                )
            },
            surAnnuler = { aValider = null },
        )
    }

    if (confirmerScellement) {
        Confirmation(
            titre = stringResource(R.string.sceller_titre),
            texte = stringResource(R.string.sceller_texte),
            confirmer = stringResource(R.string.action_sceller),
            surConfirmer = {
                confirmerScellement = false
                enCours = "scellement"
                portee.launch {
                    essayer { ctx.api.post("/seances/$idSeance/scellement") }
                        .onSuccess {
                            scellement = Lecture.scellement(it as? JSONObject)
                            rafraichir++
                        }
                        .onFailure { ctx.notifier(messageDe(it)) }
                    enCours = null
                }
            },
            surAnnuler = { confirmerScellement = false },
        )
    }
}

@Composable
private fun LigneDecision(inscrit: Inscrit, actif: Boolean, surPresent: () -> Unit, surAbsent: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(inscrit.nomComplet, style = Styles.CorpsFort, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(inscrit.matricule, "${inscrit.fenetresValidees} fenêtres").filter { it.isNotBlank() }.joinToString(" · "),
            style = Styles.Petit.copy(fontFeatureSettings = "tnum"),
        )
        Row {
            Lien(stringResource(R.string.action_confirmer_present), surPresent, couleur = Palette.Present, enabled = actif)
            Lien(stringResource(R.string.action_marquer_absent), surAbsent, couleur = Palette.Absent, enabled = actif)
        }
    }
}
