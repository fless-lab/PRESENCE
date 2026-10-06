# Protocole

Le protocole est la pièce centrale de PRESENCE : il est indépendant de la plateforme, ce qui permet d'ajouter un client iOS, un autre observateur ou un autre registre sans toucher au reste.

| Fichier | Rôle |
|---|---|
| [`SPEC-v0.1.md`](SPEC-v0.1.md) | Spécification : rôles, balise, attestation, événements, calcul, cardinalité, scellement, reçus |
| [`API.md`](API.md) | Encodages, signature des requêtes, routes du serveur, format du reçu |
| [`constantes.yaml`](constantes.yaml) | Valeurs partagées : UUID Bluetooth, tailles, durées, seuils |

## Règles

- Toute modification d'un format ou d'une constante incrémente la version et s'accompagne d'une note dans [`docs/decisions/`](../docs/decisions/).
- L'application Android, les firmwares et le serveur reprennent les constantes de `constantes.yaml`. Un script de génération pourra produire les fichiers Kotlin, C et Python à partir de ce fichier.
