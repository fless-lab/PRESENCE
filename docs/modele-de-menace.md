# Modèle de menace

## Hypothèses

- La clé privée d'un téléphone ne peut pas être extraite du Keystore matériel.
- Les observateurs et le compteur de porte sont fixés dans des lieux contrôlés ; leur compromission physique est hors périmètre de la V1.
- Aucune organisation du registre ne contrôle seule deux organisations sur trois.

## Classement

| Catégorie | Attaque | Réponse |
|---|---|---|
| **Couvert** | Rejeu d'une ancienne attestation | Nonce renouvelé, compteur croissant |
| | Copie de la clé vers un autre appareil | Clé non exportable, attestation matérielle à l'enrôlement |
| | Téléphone émulé | Attestation de clé refusée |
| | Modification ou suppression d'un événement après la séance | Racine de Merkle ancrée |
| | Réécriture de l'ancrage par une organisation | Politique d'approbation 2 sur 3 |
| **Détecté** | Une personne porte plusieurs téléphones | Règle de cardinalité |
| | Même appareil dans deux salles | Incohérence spatiale |
| | Départ en cours de séance | Fenêtres non validées |
| **Étudié** | Relais Bluetooth vers un téléphone resté ailleurs | Mesure du temps de réponse et du RSSI ; UWB en perspective |
| | Passages côte à côte faussant le comptage | Mesure de l'erreur ; capteur VL53L5CX en option |
| **Hors périmètre** | Téléphone prêté volontairement, déverrouillé, à une personne qui le garde | Limite assumée de toute approche sans biométrie |
| | Identification de la personne physique | Non visée par conception |

## Données personnelles

Le registre ne contient que des identifiants de séance et des empreintes. Les noms, numéros d'étudiants et identifiants d'appareils restent dans la base de l'établissement. Aucune donnée biométrique n'est collectée.
