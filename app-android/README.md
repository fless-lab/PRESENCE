# Application Android PRESENCE

Une seule application, deux rôles selon le compte connecté : **étudiant** et **enseignant**. Le rôle est fixé par le serveur au moment de l'enrôlement.

| Paramètre | Valeur |
|---|---|
| Package | `ma.inpt.presence` |
| Langage | Kotlin, Jetpack Compose (Material 3 restylé) |
| SDK minimum | API 26 (Android 8.0) |
| SDK cible et de compilation | API 35 |
| Outils | Gradle 8.9, Android Gradle Plugin 8.5.2, Kotlin 2.0.21, Java 17 |

Le réseau passe par `HttpURLConnection` et `org.json`, sans bibliothèque tierce. Les contrats suivis sont [`protocole/API.md`](../protocole/API.md) et [`protocole/constantes.yaml`](../protocole/constantes.yaml).

## Construire et lancer

Depuis ce dossier, avec Java 17 et le SDK Android installés (variable `ANDROID_HOME` ou fichier `local.properties`) :

```bash
./gradlew assembleDebug          # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests JVM du protocole
./gradlew installDebug           # installation sur un téléphone branché
```

Dans Android Studio : *Open*, puis ce dossier. Les essais Bluetooth demandent un vrai téléphone ; l'émulateur ne sait pas joindre un observateur.

## Configurer le serveur

L'adresse du serveur se saisit au premier lancement. Par défaut : `http://192.168.1.10:8000`, soit le serveur de développement sur le réseau local. Le trafic HTTP en clair est autorisé pour ces essais ; un déploiement réel passera en HTTPS. Pour changer de serveur, se déconnecter depuis le profil puis enrôler à nouveau.

## Organisation du code

```
app/src/main/java/ma/inpt/presence/
├── protocole/      constantes, trames Bluetooth, chaîne signée, Merkle, JSON canonique (Kotlin pur, testé)
├── cle/            clé Keystore : création avec attestation, StrongBox si possible, signature
├── ble/            échange GATT d'attestation avec un observateur
├── service/        service de premier plan, recherche Bluetooth, démarrage au boot, notifications
├── reseau/         client HTTP signé, enrôlement, lecture tolérante des réponses
├── recu/           vérification locale des reçus
├── donnees/        préférences, compteur, journal de l'expérience 01, partage de fichiers
└── ui/
    ├── theme/      couleurs, typographie Google Sans Flex, formes
    ├── composants/ StatusChip, SectionLabel, InfoRow, PrimaryButton, SecondaryButton, WindowsGrid…
    ├── onboarding/ enrôlement et autorisations
    ├── etudiant/   accueil, historique, détail de séance et reçu
    ├── enseignant/ mes cours, séance active, clôture, historique
    └── commun/     profil, bloc de diagnostic, outils partagés
```

## Écrans

**Premier lancement.** Adresse du serveur, matricule et code d'enrôlement. L'application demande un défi au serveur, crée sa clé avec ce défi comme challenge d'attestation, puis envoie la clé publique et la chaîne de certificats. Vient ensuite l'écran des autorisations : appareils à proximité (ou position avant Android 12), notifications (Android 13 et plus), exclusion de l'optimisation de batterie, Bluetooth activé.

**Étudiant**

| Écran | Contenu |
|---|---|
| Accueil | Bloc « Présence active » : dernière attestation, Bluetooth, batterie, service. Séance en cours de ses cours (sondage toutes les 30 s, écran visible) : cours, salle, heure de début, grille des fenêtres de 5 minutes, statut provisoire. |
| Historique | Séances passées et statut. Un appui ouvre le détail. |
| Détail de séance | Statut, salle, horaire, fenêtres. Le bouton « Reçu » télécharge le reçu, le vérifie sur le téléphone, affiche « Reçu vérifié » avec la racine, et permet de partager le fichier JSON. |
| Profil | Identité, identifiant d'appareil, modèle, stockage de la clé, diagnostic, journal des 50 dernières tentatives, export du journal en CSV, déconnexion (supprime la clé après confirmation). |

**Enseignant**

| Écran | Contenu |
|---|---|
| Cours | Bandeau de présence (sondage toutes les 10 s) : « En salle 204 depuis 9h02 » ou « Pas encore détecté dans une salle ». Liste des cours ; « Démarrer » n'est actif que si l'enseignant est attesté dans une salle. Une séance déjà ouverte se reprend en un appui. |
| Séance active | Compteurs présents, en retard, à vérifier, non détectés ; liste des inscrits avec leur statut (sondage toutes les 10 s) ; Pause ou Reprendre ; Terminer, avec confirmation. |
| Clôture | Cas « À vérifier » : Confirmer présent ou Marquer absent. Absents et non détectés : Valider à la main, avec un motif. Puis « Sceller la séance », qui affiche la racine et la transaction d'ancrage. |
| Historique | Séances récentes ; une séance ouverte mène au suivi, une séance close à la clôture. |
| Profil | Identique à celui de l'étudiant. |

## Clé de l'appareil

- Alias `presence`, ECDSA P-256 (`secp256r1`), usage signature, SHA-256, non exportable.
- Le défi du serveur est passé à `setAttestationChallenge` ; la chaîne de certificats part à l'enrôlement.
- StrongBox est essayé en premier, puis l'environnement sécurisé classique.
- À partir d'Android 11, si l'écran est verrouillé par un code, la clé exige un déverrouillage dans les 4 dernières heures (`deverrouillage_recent: true`). Sinon la clé est créée sans cette exigence. Si une signature échoue pour cette raison, une notification demande de déverrouiller le téléphone.
- L'identifiant d'appareil est celui renvoyé par le serveur : 8 premiers octets de `SHA-256(SPKI)`.

## Requêtes signées

Chaque route du téléphone porte `X-Presence-Id: app:<id_appareil>`, `X-Presence-Horodatage` (ms) et `X-Presence-Signature`, signature DER en base64 de :

```
PRESENCE/v0.1/REQ
<MÉTHODE>
<chemin>
<horodatage>
<SHA-256 hexadécimal du corps>
```

Le chemin signé est la route telle qu'écrite dans `API.md` (par exemple `/seances/S-1/pause`), sans le préfixe de l'adresse du serveur. Les routes d'enrôlement et les routes publiques ne sont pas signées.

## Bluetooth et attestation

1. Le service de premier plan (`connectedDevice`) tourne en permanence après l'enrôlement et redémarre avec le téléphone. Sa notification « Présence active » est discrète.
2. Une seule recherche Bluetooth longue, filtrée sur l'UUID du service PRESENCE, en mode faible latence. Elle est relancée toutes les 25 minutes, car Android dégrade les recherches de plus de 30 minutes.
3. Pour chaque annonce, les données fabricant `0xFFFF` donnent `version | salle | etat`. État 1 (séance active) : on atteste. État 0 (aucune séance) : seul l'enseignant atteste. Pause et clôture : rien.
4. Au plus une tentative par observateur toutes les 40 s. Les échanges passent par une file : une seule connexion à la fois, 8 s au maximum.
5. Échange : connexion GATT (LE), MTU 185, découverte des services, lecture de `info` (8 octets) puis de `nonce` (20 octets), incrément du compteur local (strictement croissant, écrit avant usage), signature de

   ```
   "PRESENCE/v0.1/ATTEST" | salle (u16) | seance (u32) | nonce (16) | compteur (u32)
   ```

   puis écriture dans `attest` de `id_appareil (8) | compteur (u32) | numero_nonce (u32) | signature DER`, avec réponse. Déconnexion et fermeture dès la confirmation, ou en cas d'erreur ou de délai dépassé.

## Journal de l'expérience 01

Chaque tentative est ajoutée à `files/journal.csv` (archivé dans `journal.1.csv` au-delà de 2 Mo) et à un anneau en mémoire :

```
horodatage_ms,observateur,salle,seance,numero_nonce,resultat,erreur
```

`resultat` vaut `OK`, `ECHEC` ou `IGNORE` (l'observateur annonçait une séance active mais `info` indique un autre état). Le profil affiche les 50 dernières lignes et exporte le fichier par le menu de partage.

## Vérification d'un reçu

1. `GET /moi/recus/{seance}`, conservé tel quel pour le partage.
2. Signature du serveur (`GET /cle-serveur`) sur le JSON canonique du reçu sans `sig_serveur` : clés triées récursivement, aucun espace, UTF-8, comme `json.dumps(sort_keys=True, separators=(",", ":"), ensure_ascii=False)`.
3. Preuve d'inclusion de chaque événement contre `arbre.racine` (RFC 9162, `API.md` section 11).
4. Racine comparée à celle lue sur le registre (`GET /ancrages/{seance}`), corrections comprises.

Le reçu n'est déclaré vérifié que si les trois contrôles réussissent.

## Formats supposés

`API.md` ne détaille pas toutes les réponses. L'application lit les champs suivants et tolère leur absence :

| Route | Champs lus |
|---|---|
| `/moi/seance-courante` | `id`, `cours` (code ou `{code, intitule}`), `intitule`, `salle`, `nom_salle`, `etat`, `debut_ms`, `statut`, `fenetres: {total, validees}` (`validees` : liste d'indices ou nombre). Une enveloppe `{seance: {...}, statut, fenetres}` est aussi acceptée. |
| `/moi/historique`, `/enseignant/seances` | Tableau, ou objet avec `seances` : mêmes champs, plus `fin_ms`, `racine`, `ancrage.transaction`. |
| `/enseignant/cours` | Tableau, ou objet avec `cours` : `code`, `intitule`, `groupe`, `inscrits`. |
| `POST /seances` | `id` de la séance créée. |
| `/seances/{id}/direct` | Champs de séance (à plat ou dans `seance`) et `inscrits: [{matricule, nom, prenom, statut, fenetres_validees, manuel}]`. |
| `/seances/{id}/scellement` | `{racine, ancrage: {transaction, registre}}`. |

## Apparence

Thème clair uniquement : fond blanc, filets de 1 dp, encre presque noire, rayons de 10 dp au plus, aucune ombre ni dégradé. Les statuts sont un point et un libellé sur fond teinté (présent vert, retard orange, à vérifier bleu, absent rouge, partiel gris). Les chiffres utilisent des chasses fixes (`tnum`).

La police Google Sans Flex est incluse dans `app/src/main/res/font/`, sous licence SIL Open Font License 1.1 (texte dans `app/src/main/assets/licences/OFL.txt`). Le fichier est réduit à l'alphabet latin et ses axes secondaires sont figés ; seul l'axe de graisse reste variable (400, 500 et 600 utilisés).

## Tests

`app/src/test` contient des tests JUnit 4 sans dépendance Android : chaîne signée des requêtes, disposition des trames et du message d'attestation, preuves de Merkle (feuilles `evenement-0` à `evenement-4`, racine identique à celle du serveur Python, feuille modifiée rejetée) et JSON canonique d'un objet imbriqué.
