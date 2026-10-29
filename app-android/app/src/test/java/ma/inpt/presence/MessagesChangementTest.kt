package ma.inpt.presence

import ma.inpt.presence.reseau.ChangementCours
import ma.inpt.presence.reseau.MessagesChangement
import ma.inpt.presence.reseau.ValeursCreneau
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesChangementTest {

    private val jour: (Long) -> String = { "j$it" }
    private val heure: (Long) -> String = { "h$it" }

    private fun changement(type: String, avant: ValeursCreneau?, apres: ValeursCreneau?, motif: String = "") =
        ChangementCours(1, type, "SECRES", "Sécurité des réseaux", avant, apres, motif, 0)

    @Test
    fun annulationReprendLeMotif() {
        val m = MessagesChangement.composer(
            changement("ANNULE", ValeursCreneau(10, 20, "204"), ValeursCreneau(10, 20, "204"), "Examen"),
            jour,
            heure,
        )
        assertEquals("Cours annulé : Sécurité des réseaux", m.titre)
        assertTrue(m.corps.contains("Prévu j10 à h10"))
        assertTrue(m.corps.endsWith("Motif : Examen"))
    }

    @Test
    fun deplacementMontreAncienEtNouvelHoraire() {
        val m = MessagesChangement.composer(
            changement("DEPLACE", ValeursCreneau(10, 20, "204"), ValeursCreneau(30, 40, "204"), "Mission"),
            jour,
            heure,
        )
        assertEquals("Cours modifié : Sécurité des réseaux", m.titre)
        assertEquals("j10 h10 devient j30 h30. Motif : Mission", m.corps)
    }

    @Test
    fun changementDeSalleSeulEstSignale() {
        val m = MessagesChangement.composer(
            changement("DEPLACE", ValeursCreneau(10, 20, "204"), ValeursCreneau(10, 20, "205")),
            jour,
            heure,
        )
        assertEquals("Salle 205", m.corps)
    }

    @Test
    fun ajoutEtRetablissement() {
        val apres = ValeursCreneau(10, 20, "204")
        assertEquals("Nouveau cours : Sécurité des réseaux", MessagesChangement.composer(changement("CREE", null, apres), jour, heure).titre)
        val r = MessagesChangement.composer(changement("RETABLI", apres, apres), jour, heure)
        assertEquals("Cours rétabli : Sécurité des réseaux", r.titre)
        assertEquals("j10 à h10. Salle 204", r.corps)
    }
}
