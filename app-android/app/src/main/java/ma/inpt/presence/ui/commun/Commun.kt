package ma.inpt.presence.ui.commun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import ma.inpt.presence.donnees.Identite
import ma.inpt.presence.reseau.ApiClient
import ma.inpt.presence.reseau.ErreurApi
import ma.inpt.presence.ui.theme.Palette

/** Ce dont un écran a besoin : identité, client, retour à l'utilisateur et navigation. */
class ContexteApp(
    val identite: Identite,
    val api: ApiClient,
    val notifier: (String) -> Unit,
    val naviguer: (String) -> Unit,
    val retour: () -> Unit,
)

/** État d'un chargement. */
sealed interface Etat<out T> {
    data object Chargement : Etat<Nothing>
    data class Erreur(val message: String) : Etat<Nothing>
    data class Pret<T>(val valeur: T) : Etat<T>
}

fun messageDe(e: Throwable): String = when (e) {
    is ErreurApi -> e.message ?: "Erreur du serveur"
    else -> "Erreur inattendue : ${e.message ?: e.javaClass.simpleName}"
}

/** Exécute [bloc] et transforme toute erreur en message, sans avaler l'annulation. */
suspend fun <T> essayer(bloc: suspend () -> T): Result<T> = try {
    Result.success(bloc())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Répète [action] toutes les [intervalleMs] tant que l'écran est visible.
 * [cle] relance la boucle quand elle change.
 */
@Composable
fun Sondage(intervalleMs: Long, cle: Any? = Unit, action: suspend () -> Unit) {
    val proprietaire = LocalLifecycleOwner.current
    val actionCourante by rememberUpdatedState(action)
    LaunchedEffect(proprietaire, cle, intervalleMs) {
        proprietaire.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                actionCourante()
                delay(intervalleMs)
            }
        }
    }
}

/** Marque de l'application : un disque plein dans un anneau. */
@Composable
fun Marque(taille: Dp = 40.dp) {
    Canvas(Modifier.size(taille)) {
        val epaisseur = size.minDimension * 0.08f
        val centre = Offset(size.width / 2, size.height / 2)
        drawCircle(
            color = Palette.Encre,
            radius = size.minDimension / 2 - epaisseur / 2,
            center = centre,
            style = Stroke(width = epaisseur),
        )
        drawCircle(color = Palette.Encre, radius = size.minDimension * 0.22f, center = centre)
    }
}
