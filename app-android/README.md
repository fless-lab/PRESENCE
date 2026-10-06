# Application Android PRESENCE

Une seule application, deux rôles selon le compte connecté : **étudiant** et **enseignant**.

## Créer le projet

Dans Android Studio : *New Project*, *Empty Activity* (Jetpack Compose), à la racine de ce dossier.

| Paramètre | Valeur |
|---|---|
| Nom | PRESENCE |
| Package | `ma.inpt.presence` |
| Langage | Kotlin |
| SDK minimum | API 26 (Android 8.0) |
| SDK cible | La dernière version stable |

## Organisation prévue du code

```
app/src/main/java/ma/inpt/presence/
├── protocole/      constantes (reprises de protocole/constantes.yaml), formats de trames
├── cle/            génération de la clé Keystore, attestation, signature
├── ble/            recherche filtrée, connexion GATT, lecture du nonce, écriture de l'attestation
├── service/        service de premier plan (type connectedDevice), boucle d'attestation
├── reseau/         appels à l'API du serveur
├── donnees/        stockage local (historique, reçus, journal de test)
└── ui/
    ├── etudiant/   accueil, séance en cours, historique, reçus
    └── enseignant/ mes cours, séance active, pause, clôture, validation manuelle
```

## Permissions

`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`, `INTERNET`. L'application guide aussi l'utilisateur pour l'exclure de l'optimisation de batterie.

## Points d'attention

- La recherche Bluetooth doit filtrer sur l'UUID du service, sinon Android la bloque en arrière-plan.
- Demander un MTU de 185 avant d'écrire l'attestation (environ 88 octets).
- Chaque échange reste court : connexion, lecture, signature, écriture, déconnexion.

## Étapes

1. **Mode test** pour l'expérience 01 : création de la clé, service de premier plan, journal local.
2. Enrôlement auprès du serveur.
3. Écrans étudiant.
4. Écrans enseignant.
5. Reçus et leur vérification.
