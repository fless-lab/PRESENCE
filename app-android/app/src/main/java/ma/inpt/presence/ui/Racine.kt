package ma.inpt.presence.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ma.inpt.presence.R
import ma.inpt.presence.cle.GestionCle
import ma.inpt.presence.donnees.Identite
import ma.inpt.presence.donnees.Stockage
import ma.inpt.presence.reseau.ApiClient
import ma.inpt.presence.service.PresenceService
import ma.inpt.presence.ui.commun.ContexteApp
import ma.inpt.presence.ui.commun.ProfilEcran
import ma.inpt.presence.ui.enseignant.ClotureEcran
import ma.inpt.presence.ui.enseignant.CoursEcran
import ma.inpt.presence.ui.enseignant.HistoriqueEnseignantEcran
import ma.inpt.presence.ui.enseignant.SeanceActiveEcran
import ma.inpt.presence.ui.etudiant.AccueilEcran
import ma.inpt.presence.ui.etudiant.DetailSeanceEcran
import ma.inpt.presence.ui.etudiant.HistoriqueEcran
import ma.inpt.presence.ui.onboarding.EnrolementEcran
import ma.inpt.presence.ui.onboarding.PermissionsEcran
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles

private class Onglet(val route: String, val libelle: String)

/** Point d'entrée de l'interface : enrôlement, autorisations, puis navigation selon le rôle. */
@Composable
fun Racine() {
    val context = LocalContext.current
    val stockage = remember { Stockage(context) }
    var identite by remember { mutableStateOf(stockage.identite()) }
    var permissionsVues by remember { mutableStateOf(stockage.permissionsVues) }

    val moi = identite
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Fond),
    ) {
        val marges = Modifier
            .fillMaxSize()
            .systemBarsPadding()
        when {
            moi == null -> EnrolementEcran(
                stockage = stockage,
                surEnrole = { identite = it },
                modifier = marges,
            )
            !permissionsVues -> PermissionsEcran(
                surTermine = {
                    stockage.permissionsVues = true
                    permissionsVues = true
                    PresenceService.demarrer(context)
                },
                modifier = marges,
            )
            else -> Principal(
                identite = moi,
                surDeconnexion = {
                    PresenceService.arreter(context)
                    GestionCle.supprimer()
                    stockage.effacer()
                    permissionsVues = false
                    identite = null
                },
            )
        }
    }
}

@Composable
private fun Principal(identite: Identite, surDeconnexion: () -> Unit) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val portee = rememberCoroutineScope()
    val messages = remember { SnackbarHostState() }
    val api = remember(identite) { ApiClient(identite.serveur, identite.idAppareil) }

    LaunchedEffect(identite.idAppareil) { PresenceService.demarrer(context) }

    val onglets = if (identite.estEnseignant) {
        listOf(
            Onglet("cours", stringResource(R.string.onglet_cours)),
            Onglet("historique", stringResource(R.string.onglet_historique)),
            Onglet("profil", stringResource(R.string.onglet_profil)),
        )
    } else {
        listOf(
            Onglet("accueil", stringResource(R.string.onglet_accueil)),
            Onglet("historique", stringResource(R.string.onglet_historique)),
            Onglet("profil", stringResource(R.string.onglet_profil)),
        )
    }

    val ctx = remember(identite, api) {
        ContexteApp(
            identite = identite,
            api = api,
            notifier = { texte -> portee.launch { messages.showSnackbar(texte) } },
            naviguer = { route -> naviguer(nav, route) },
            retour = { nav.popBackStack() },
        )
    }

    val entree by nav.currentBackStackEntryAsState()
    val routeCourante = entree?.destination?.route
    val surOnglet = onglets.any { it.route == routeCourante }

    Scaffold(
        containerColor = Palette.Fond,
        snackbarHost = {
            SnackbarHost(messages) { donnees ->
                Snackbar(
                    snackbarData = donnees,
                    shape = RoundedCornerShape(10.dp),
                    containerColor = Palette.Encre,
                    contentColor = Palette.Fond,
                )
            }
        },
        bottomBar = {
            if (surOnglet) {
                BarreNavigation(onglets, routeCourante) { route ->
                    nav.navigate(route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { marges ->
        NavHost(
            navController = nav,
            startDestination = onglets.first().route,
            modifier = Modifier
                .fillMaxSize()
                .padding(marges),
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(120)) },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = { fadeOut(animationSpec = tween(120)) },
        ) {
            composable("accueil") { AccueilEcran(ctx) }
            composable("cours") { CoursEcran(ctx) }
            composable("historique") {
                if (identite.estEnseignant) HistoriqueEnseignantEcran(ctx) else HistoriqueEcran(ctx)
            }
            composable("profil") { ProfilEcran(ctx, surDeconnexion) }
            composable(
                "seance/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e ->
                DetailSeanceEcran(ctx, e.arguments?.getString("id").orEmpty())
            }
            composable(
                "active/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e ->
                SeanceActiveEcran(ctx, e.arguments?.getString("id").orEmpty())
            }
            composable(
                "cloture/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e ->
                ClotureEcran(ctx, e.arguments?.getString("id").orEmpty())
            }
        }
    }
}

/** Navigation : la clôture remplace la séance active dans la pile. */
private fun naviguer(nav: NavHostController, route: String) {
    nav.navigate(route) {
        if (route.startsWith("cloture/")) {
            val courante = nav.currentBackStackEntry?.destination?.route
            if (courante == "active/{id}") {
                popUpTo("active/{id}") { inclusive = true }
            }
        }
        launchSingleTop = true
    }
}

@Composable
private fun BarreNavigation(onglets: List<Onglet>, courante: String?, surChoix: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Fond)
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.Filet),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            onglets.forEach { onglet ->
                val actif = onglet.route == courante
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { if (!actif) surChoix(onglet.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        onglet.libelle,
                        style = Styles.Bouton.copy(color = if (actif) Palette.Encre else Palette.Secondaire),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(width = 18.dp, height = 2.dp)
                            .background(if (actif) Palette.Encre else Color.Transparent, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
}
