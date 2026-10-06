package ma.inpt.presence.ui.enseignant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ma.inpt.presence.R
import ma.inpt.presence.reseau.Cours
import ma.inpt.presence.reseau.Json
import ma.inpt.presence.reseau.Lecture
import ma.inpt.presence.reseau.PresenceEnseignant
import ma.inpt.presence.reseau.Seance
import ma.inpt.presence.reseau.texte
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.commun.ContexteApp
import ma.inpt.presence.ui.commun.Etat
import ma.inpt.presence.ui.commun.Sondage
import ma.inpt.presence.ui.commun.essayer
import ma.inpt.presence.ui.commun.messageDe
import ma.inpt.presence.ui.composants.Bandeau
import ma.inpt.presence.ui.composants.Bloc
import ma.inpt.presence.ui.composants.Chargement
import ma.inpt.presence.ui.composants.EnTete
import ma.inpt.presence.ui.composants.EtatVide
import ma.inpt.presence.ui.composants.MargeLaterale
import ma.inpt.presence.ui.composants.MessageErreur
import ma.inpt.presence.ui.composants.PrimaryButton
import ma.inpt.presence.ui.composants.SecondaryButton
import ma.inpt.presence.ui.composants.SectionLabel
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles
import org.json.JSONObject

/** Mes cours : présence détectée dans une salle, séance en cours, démarrage. */
@Composable
fun CoursEcran(ctx: ContexteApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var essai by remember { mutableIntStateOf(0) }
    var cours by remember { mutableStateOf<Etat<List<Cours>>>(Etat.Chargement) }
    var presence by remember { mutableStateOf<PresenceEnseignant?>(null) }
    var presenceConnue by remember { mutableStateOf(false) }
    var seanceOuverte by remember { mutableStateOf<Seance?>(null) }
    var demarrage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(essai) {
        cours = Etat.Chargement
        cours = essayer { ctx.api.get("/enseignant/cours") }.fold(
            onSuccess = { Etat.Pret(Json.listeObjets(it, "cours").map { o -> Lecture.cours(o) }) },
            onFailure = { Etat.Erreur(messageDe(it)) },
        )
    }

    Sondage(10_000L) {
        essayer { ctx.api.get("/enseignant/presence") }
            .onSuccess {
                presence = Lecture.presence(it as? JSONObject)
                presenceConnue = true
            }
        essayer { ctx.api.get("/enseignant/seances") }
            .onSuccess { reponse ->
                seanceOuverte = Json.listeObjets(reponse, "seances")
                    .map { Lecture.seanceEnveloppee(it) }
                    .firstOrNull { it.etat.uppercase() == "ACTIVE" || it.etat.uppercase() == "PAUSE" }
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeLaterale),
    ) {
        EnTete(stringResource(R.string.titre_cours), Format.dateLongue(System.currentTimeMillis()))
        Spacer(Modifier.height(12.dp))

        val p = presence
        when {
            p != null -> Bandeau(
                stringResource(R.string.en_salle_depuis, p.libelleSalle, Format.heure(p.depuisMs)),
                couleurPoint = Palette.Present,
            )
            presenceConnue -> Bandeau(stringResource(R.string.pas_detecte), couleurPoint = Palette.Partiel)
            else -> Bandeau(stringResource(R.string.recherche_presence), couleurPoint = Palette.Partiel)
        }
        if (p == null && presenceConnue) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pas_detecte_aide), style = Styles.Petit)
        }

        val ouverte = seanceOuverte
        if (ouverte != null && ouverte.id.isNotBlank()) {
            SectionLabel(stringResource(R.string.section_seance_ouverte))
            Bloc {
                Text(ouverte.libelleCours, style = Styles.SousTitre)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf("Salle ${ouverte.libelleSalle}", Format.plage(ouverte.debutMs, 0), Format.libelleEtat(ouverte.etat))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = Styles.Petit.copy(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.action_reprendre_suivi), { ctx.naviguer("active/${ouverte.id}") })
            }
        }

        SectionLabel(stringResource(R.string.section_mes_cours))
        when (val e = cours) {
            is Etat.Chargement -> Chargement()
            is Etat.Erreur -> MessageErreur(e.message) { essai++ }
            is Etat.Pret -> {
                if (e.valeur.isEmpty()) {
                    EtatVide(stringResource(R.string.cours_vide), stringResource(R.string.cours_vide_texte))
                }
                e.valeur.forEach { c ->
                    Bloc(Modifier.padding(bottom = 10.dp)) {
                        Text(c.intitule.ifBlank { c.code }, style = Styles.CorpsFort)
                        Spacer(Modifier.height(2.dp))
                        val details = listOfNotNull(
                            c.code.takeIf { it.isNotBlank() && c.intitule.isNotBlank() },
                            c.groupe.takeIf { it.isNotBlank() }?.let { "Groupe $it" },
                            c.inscrits.takeIf { it > 0 }?.let { "$it inscrits" },
                        ).joinToString(" · ")
                        if (details.isNotBlank()) Text(details, style = Styles.Petit.copy(fontFeatureSettings = "tnum"))
                        Spacer(Modifier.height(14.dp))
                        val actif = p != null && ouverte == null
                        SecondaryButton(
                            texte = if (p != null) {
                                stringResource(R.string.action_demarrer_salle, p.libelleSalle)
                            } else {
                                stringResource(R.string.action_demarrer_seance)
                            },
                            enabled = actif && demarrage == null,
                            chargement = demarrage == c.code,
                            onClick = {
                                demarrage = c.code
                                portee.launch {
                                    essayer { ctx.api.post("/seances", JSONObject().put("cours", c.code)) }
                                        .onSuccess { reponse ->
                                            val o = reponse as? JSONObject
                                            val id = o?.texte("id", "seance")
                                                ?: o?.optJSONObject("seance")?.texte("id")
                                            if (id != null) {
                                                ctx.naviguer("active/$id")
                                            } else {
                                                ctx.notifier(context.getString(R.string.seance_demarree))
                                            }
                                        }
                                        .onFailure { ctx.notifier(messageDe(it)) }
                                    demarrage = null
                                }
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
