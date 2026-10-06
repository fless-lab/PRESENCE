@file:OptIn(ExperimentalTextApi::class)

package ma.inpt.presence.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.inpt.presence.R

/** Palette claire : fond blanc, filets fins, encre presque noire, couleurs de statut sobres. */
object Palette {
    val Fond = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF6F7F9)
    val Filet = Color(0xFFE4E7EB)
    val Encre = Color(0xFF111418)
    val Secondaire = Color(0xFF5E6670)
    val Discret = Color(0xFF9AA1A9)

    val Present = Color(0xFF1A7F5A)
    val Retard = Color(0xFFB5621B)
    val AVerifier = Color(0xFF2F5FB3)
    val Absent = Color(0xFFB42318)
    val Partiel = Color(0xFF6B7280)

    val PresentFond = Color(0xFFE9F4EF)
    val RetardFond = Color(0xFFF9F0E8)
    val AVerifierFond = Color(0xFFEBF0F8)
    val AbsentFond = Color(0xFFFBECEB)
    val PartielFond = Color(0xFFF1F2F4)
}

val GoogleSansFlex = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/** Styles de texte de l'application. */
object Styles {
    val Titre = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
        color = Palette.Encre,
    )
    val SousTitre = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
        color = Palette.Encre,
    )
    val Section = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        color = Palette.Secondaire,
    )
    val Corps = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = Palette.Encre,
    )
    val CorpsFort = Corps.copy(fontWeight = FontWeight.Medium)
    val Petit = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = Palette.Secondaire,
    )
    val Chiffre = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontFeatureSettings = "tnum",
        color = Palette.Encre,
    )
    val Bouton = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    )
    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = Palette.Encre,
    )
}

private val typographie = Typography(
    headlineMedium = Styles.Titre,
    titleLarge = Styles.SousTitre,
    titleMedium = Styles.CorpsFort,
    titleSmall = Styles.Section,
    bodyLarge = Styles.Corps,
    bodyMedium = Styles.Corps,
    bodySmall = Styles.Petit,
    labelLarge = Styles.Bouton,
    labelMedium = Styles.Petit,
    labelSmall = Styles.Petit.copy(fontSize = 12.sp),
)

private val couleurs = lightColorScheme(
    primary = Palette.Encre,
    onPrimary = Palette.Fond,
    primaryContainer = Palette.Surface,
    onPrimaryContainer = Palette.Encre,
    secondary = Palette.Encre,
    onSecondary = Palette.Fond,
    secondaryContainer = Palette.Surface,
    onSecondaryContainer = Palette.Encre,
    tertiary = Palette.AVerifier,
    onTertiary = Palette.Fond,
    background = Palette.Fond,
    onBackground = Palette.Encre,
    surface = Palette.Fond,
    onSurface = Palette.Encre,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.Secondaire,
    surfaceTint = Palette.Fond,
    outline = Palette.Filet,
    outlineVariant = Palette.Filet,
    error = Palette.Absent,
    onError = Palette.Fond,
    surfaceBright = Palette.Fond,
    surfaceDim = Palette.Surface,
    surfaceContainer = Palette.Fond,
    surfaceContainerHigh = Palette.Fond,
    surfaceContainerHighest = Palette.Fond,
    surfaceContainerLow = Palette.Fond,
    surfaceContainerLowest = Palette.Fond,
)

private val formes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun PresenceTheme(contenu: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = couleurs,
        typography = typographie,
        shapes = formes,
        content = contenu,
    )
}
