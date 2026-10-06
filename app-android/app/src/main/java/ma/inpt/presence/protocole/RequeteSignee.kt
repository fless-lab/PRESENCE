package ma.inpt.presence.protocole

/** Construction de la chaîne signée d'une requête (API.md, section 2). */
object RequeteSignee {

    const val ENTETE_ID = "X-Presence-Id"
    const val ENTETE_HORODATAGE = "X-Presence-Horodatage"
    const val ENTETE_SIGNATURE = "X-Presence-Signature"

    fun identifiant(idAppareil: String): String = "app:$idAppareil"

    /**
     * PRESENCE/v0.1/REQ \n MÉTHODE \n chemin \n horodatage \n SHA-256(corps) en hexadécimal.
     * Un corps vide donne le SHA-256 de la chaîne vide.
     */
    fun chaineASigner(methode: String, chemin: String, horodatage: Long, corps: ByteArray): String =
        listOf(
            Constantes.ETIQUETTE_REQUETE,
            methode.uppercase(),
            chemin,
            horodatage.toString(),
            Octets.sha256Hex(corps),
        ).joinToString("\n")
}
