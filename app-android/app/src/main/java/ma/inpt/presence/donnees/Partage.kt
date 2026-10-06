package ma.inpt.presence.donnees

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Partage de fichiers (reçu, journal) par l'intention système. */
object Partage {

    private fun dossier(context: Context): File = File(context.cacheDir, "partage").apply { mkdirs() }

    fun partagerTexte(context: Context, nomFichier: String, contenu: String, type: String, titre: String): Boolean {
        val fichier = File(dossier(context), nomFichier)
        fichier.writeText(contenu, Charsets.UTF_8)
        return partagerFichier(context, fichier, type, titre)
    }

    fun partagerCopie(context: Context, source: File, nomFichier: String, type: String, titre: String): Boolean {
        val fichier = File(dossier(context), nomFichier)
        if (source.exists()) source.copyTo(fichier, overwrite = true) else fichier.writeText("")
        return partagerFichier(context, fichier, type, titre)
    }

    private fun partagerFichier(context: Context, fichier: File, type: String, titre: String): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fichiers", fichier)
        val envoi = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fichier.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val choix = Intent.createChooser(envoi, titre).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(choix)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
