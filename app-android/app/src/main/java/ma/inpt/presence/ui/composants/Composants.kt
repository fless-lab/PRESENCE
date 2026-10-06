package ma.inpt.presence.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ma.inpt.presence.ui.Format
import ma.inpt.presence.ui.theme.Palette
import ma.inpt.presence.ui.theme.Styles

val MargeLaterale = 16.dp
private val Rayon = RoundedCornerShape(10.dp)

/** Titre d'écran, avec une ligne secondaire facultative. */
@Composable
fun EnTete(titre: String, sousTitre: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 20.dp, bottom = 8.dp)) {
        Text(titre, style = Styles.Titre)
        if (!sousTitre.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(sousTitre, style = Styles.Petit)
        }
    }
}

/** Libellé de section : petit, moyen, gris. */
@Composable
fun SectionLabel(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte,
        style = Styles.Section,
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

/** Bloc à filet fin, sans ombre. */
@Composable
fun Bloc(
    modifier: Modifier = Modifier,
    fond: Color = Palette.Fond,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Rayon)
            .background(fond)
            .border(1.dp, Palette.Filet, Rayon)
            .padding(16.dp),
        content = contenu,
    )
}

/** Ligne libellé / valeur. */
@Composable
fun InfoRow(
    libelle: String,
    valeur: String,
    modifier: Modifier = Modifier,
    styleValeur: TextStyle = Styles.Corps,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(libelle, style = Styles.Corps.copy(color = Palette.Secondaire), modifier = Modifier.width(132.dp))
        Text(
            valeur.ifBlank { "Non renseigné" },
            style = styleValeur.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun Filet(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Palette.Filet)
}

/** Pastille de statut : point et libellé sur fond teinté. */
@Composable
fun StatusChip(statut: String, modifier: Modifier = Modifier, libelle: String = Format.libelleStatut(statut)) {
    val couleur = Format.couleurStatut(statut)
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Format.fondStatut(statut))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Point(couleur, 6.dp)
        Spacer(Modifier.width(6.dp))
        Text(libelle, style = Styles.Petit.copy(color = couleur, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
fun Point(couleur: Color, taille: Dp = 8.dp) {
    Box(
        Modifier
            .size(taille)
            .clip(CircleShape)
            .background(couleur),
    )
}

/** Ligne de diagnostic : point vert ou rouge, libellé, détail et action facultative. */
@Composable
fun LigneDiagnostic(
    libelle: String,
    ok: Boolean,
    detail: String,
    action: String? = null,
    surAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Point(if (ok) Palette.Present else Palette.Absent, 7.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(libelle, style = Styles.Corps)
            Text(detail, style = Styles.Petit)
        }
        if (action != null && surAction != null) {
            Lien(action, surAction)
        }
    }
}

@Composable
fun PrimaryButton(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    chargement: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !chargement,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = Rayon,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Encre,
            contentColor = Palette.Fond,
            disabledContainerColor = Palette.Filet,
            disabledContentColor = Palette.Secondaire,
        ),
        elevation = null,
    ) {
        if (chargement) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Palette.Secondaire, strokeWidth = 1.5.dp)
        } else {
            Text(texte, style = Styles.Bouton)
        }
    }
}

@Composable
fun SecondaryButton(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    couleur: Color = Palette.Encre,
    chargement: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !chargement,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = Rayon,
        border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Filet),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Palette.Fond,
            contentColor = couleur,
            disabledContainerColor = Palette.Fond,
            disabledContentColor = Palette.Discret,
        ),
    ) {
        if (chargement) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Palette.Secondaire, strokeWidth = 1.5.dp)
        } else {
            Text(texte, style = Styles.Bouton)
        }
    }
}

/** Bouton texte discret. */
@Composable
fun Lien(texte: String, onClick: () -> Unit, couleur: Color = Palette.Encre, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp)) {
        Text(texte, style = Styles.Bouton.copy(color = if (enabled) couleur else Palette.Discret))
    }
}

/** Champ de saisie avec libellé au-dessus et filet fin. */
@Composable
fun Champ(
    libelle: String,
    valeur: String,
    surChangement: (String) -> Unit,
    modifier: Modifier = Modifier,
    indication: String = "",
    clavier: KeyboardType = KeyboardType.Text,
    majuscules: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Text(libelle, style = Styles.Section, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = valeur,
            onValueChange = surChangement,
            enabled = enabled,
            singleLine = true,
            textStyle = Styles.Corps,
            cursorBrush = SolidColor(Palette.Encre),
            keyboardOptions = KeyboardOptions(
                capitalization = if (majuscules) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                keyboardType = clavier,
                imeAction = imeAction,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { champ ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(Rayon)
                        .background(Palette.Fond)
                        .border(1.dp, Palette.Filet, Rayon)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (valeur.isEmpty() && indication.isNotEmpty()) {
                        Text(indication, style = Styles.Corps.copy(color = Palette.Discret))
                    }
                    champ()
                }
            },
        )
    }
}

/** Grille des fenêtres de 5 minutes : carré plein si validée, filet sinon. */
@Composable
fun WindowsGrid(total: Int, validees: Set<Int>?, nbValidees: Int, modifier: Modifier = Modifier) {
    if (total <= 0) {
        Text("Aucune fenêtre pour le moment", style = Styles.Petit, modifier = modifier)
        return
    }
    val pleines: Set<Int> = validees ?: (0 until nbValidees.coerceAtMost(total)).toSet()
    val parLigne = 12
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (debut in 0 until total step parLigne) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in debut until minOf(debut + parLigne, total)) {
                    val pleine = i in pleines
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (pleine) Palette.Encre else Palette.Fond)
                            .border(1.dp, if (pleine) Palette.Encre else Palette.Filet, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${pleines.size} sur $total fenêtres validées" + if (validees == null) " (détail non fourni)" else "",
            style = Styles.Petit.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** Chiffre clé avec son libellé. */
@Composable
fun RowScope.Compteur(valeur: Int, libelle: String, couleur: Color = Palette.Encre) {
    Column(Modifier.weight(1f)) {
        Text(valeur.toString(), style = Styles.Chiffre.copy(color = couleur))
        Text(libelle, style = Styles.Petit, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chargement(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Encre, strokeWidth = 1.5.dp)
    }
}

/** État vide ou erreur : texte posé, action facultative. */
@Composable
fun EtatVide(titre: String, texte: String, action: String? = null, surAction: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(titre, style = Styles.CorpsFort)
        Spacer(Modifier.height(4.dp))
        Text(texte, style = Styles.Corps.copy(color = Palette.Secondaire))
        if (action != null && surAction != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton(action, surAction)
        }
    }
}

@Composable
fun MessageErreur(texte: String, surReessayer: (() -> Unit)? = null) {
    EtatVide(
        titre = "Impossible de charger",
        texte = texte,
        action = if (surReessayer != null) "Réessayer" else null,
        surAction = surReessayer,
    )
}

/** Bandeau d'information discret sur fond gris clair. */
@Composable
fun Bandeau(texte: String, modifier: Modifier = Modifier, couleurPoint: Color? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Rayon)
            .background(Palette.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (couleurPoint != null) {
            Point(couleurPoint, 7.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(texte, style = Styles.Corps)
    }
}

/** Ligne cliquable d'une liste, séparée par un filet. */
@Composable
fun LigneListe(
    titre: String,
    detail: String,
    modifier: Modifier = Modifier,
    surClic: (() -> Unit)? = null,
    fin: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (surClic != null) Modifier.clickable(onClick = surClic) else Modifier)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(titre, style = Styles.CorpsFort, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(detail, style = Styles.Petit.copy(fontFeatureSettings = "tnum"), maxLines = 2)
                }
            }
            if (fin != null) {
                Spacer(Modifier.width(12.dp))
                fin()
            }
        }
        Filet()
    }
}

/** Confirmation simple. */
@Composable
fun Confirmation(
    titre: String,
    texte: String,
    confirmer: String,
    surConfirmer: () -> Unit,
    surAnnuler: () -> Unit,
    destructif: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = surAnnuler,
        shape = Rayon,
        containerColor = Palette.Fond,
        title = { Text(titre, style = Styles.SousTitre) },
        text = { Text(texte, style = Styles.Corps.copy(color = Palette.Secondaire)) },
        confirmButton = {
            Lien(confirmer, surConfirmer, couleur = if (destructif) Palette.Absent else Palette.Encre)
        },
        dismissButton = { Lien("Annuler", surAnnuler, couleur = Palette.Secondaire) },
    )
}

/** Saisie d'un motif dans une boîte de dialogue. */
@Composable
fun DialogueMotif(
    titre: String,
    texte: String,
    motif: String,
    surMotif: (String) -> Unit,
    surValider: () -> Unit,
    surAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = surAnnuler,
        shape = Rayon,
        containerColor = Palette.Fond,
        title = { Text(titre, style = Styles.SousTitre) },
        text = {
            Column {
                Text(texte, style = Styles.Corps.copy(color = Palette.Secondaire))
                Spacer(Modifier.height(16.dp))
                Champ(
                    libelle = "Motif",
                    valeur = motif,
                    surChangement = surMotif,
                    indication = "Par exemple : téléphone déchargé",
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = { Lien("Valider", surValider, enabled = motif.isNotBlank()) },
        dismissButton = { Lien("Annuler", surAnnuler, couleur = Palette.Secondaire) },
    )
}
