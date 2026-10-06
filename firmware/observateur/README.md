# Observateur de salle

ESP32 fixé dans la salle (deux par salle, coins opposés). Phase 1. Le format des trames et des requêtes est fixé par [`protocole/API.md`](../../protocole/API.md).

| Fonction | Détail |
|---|---|
| Balise | Annonce le service PRESENCE avec la salle et l'état de la séance |
| Nonce | 16 octets aléatoires, renouvelés toutes les 45 s, le précédent reste accepté 10 s |
| Vérification | Signature ECDSA P-256 de chaque attestation, avec la clé publique de l'inscrit, puis compteur strictement croissant |
| Témoignage | Chaque attestation valide est horodatée et signée par l'observateur |
| Envoi | Lots de 20 au plus, toutes les 30 s ou dès 20 témoignages en attente |

## Modules

| Fichier | Rôle |
|---|---|
| `identite.c` | Clé ECDSA P-256 créée au premier démarrage, gardée en NVS (espace `presence`, clé `cle`) |
| `reseau.c` | Wi-Fi station, heure réglée sur `GET /heure` toutes les 10 min, requêtes signées |
| `config.c` | `GET /equipements/moi/config` toutes les 10 s : salle, séance, clés des appareils (64 au plus) |
| `nonce.c` | Tirage et renouvellement du nonce |
| `ble.c` | Annonce, caractéristiques INFO, NONCE et ATTEST (NimBLE) |
| `attestation.c` | Vérification des trames dans une tâche dédiée, production des témoignages |
| `journal.c` | File des témoignages en RAM et envoi au serveur |
| `led.c` | Voyant d'état |

## Compiler et flasher

ESP-IDF v5.3, cibles `esp32` et `esp32s3`.

```bash
cd firmware/observateur
idf.py set-target esp32
idf.py menuconfig          # menu PRESENCE, voir ci-dessous
idf.py build flash monitor
```

Le programme dépasse 1 Mo : `sdkconfig.defaults` choisit la table de partitions « Single factory app (large) ».

## Réglages (menu PRESENCE)

| Option | Rôle | Défaut |
|---|---|---|
| `PRESENCE_SALLE` | Identifiant numérique de la salle, remplacé par celui du serveur dès la première configuration | 204 |
| `PRESENCE_NOM_OBSERVATEUR` | Nom de l'équipement, envoyé dans `X-Presence-Id: equ:<nom>` et dans le nom Bluetooth `PRESENCE-<nom>` | 204-A |
| `PRESENCE_WIFI_SSID`, `PRESENCE_WIFI_MDP` | Réseau Wi-Fi | |
| `PRESENCE_URL_SERVEUR` | Adresse du serveur, sans barre oblique finale (`http://192.168.1.10:8000`) | |
| `PRESENCE_MODE_TEST` | Mode de l'expérience 01, voir plus bas | non |
| `PRESENCE_GPIO_LED` | Broche du voyant, `-1` pour aucun | 2 |

Ces valeurs restent dans `sdkconfig`, qui n'est pas versionné.

## Enregistrer l'observateur

Au premier démarrage, l'observateur crée sa clé et l'affiche à chaque démarrage sur le port série :

```
I (1234) identite: PRESENCE cle publique: MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...
I (1235) identite: PRESENCE id: 3f9a0c21d4e5b687
```

1. Ouvrir le moniteur série (`idf.py monitor`) et copier la valeur après `PRESENCE cle publique:` (SPKI DER en base64).
2. Dans le tableau de bord, menu Équipements, créer l'observateur avec le même nom que `PRESENCE_NOM_OBSERVATEUR`, la salle, le type `observateur` et cette clé publique (`POST /admin/equipements`).
3. Au prochain cycle (10 s au plus), `GET /equipements/moi/config` répond 200 et le journal affiche `liste des clés mise à jour`. Un statut 401 ou 403 indique en général un nom ou une clé qui ne correspond pas.

La clé survit aux reflashages tant que la NVS n'est pas effacée. Après `idf.py erase-flash`, une nouvelle clé est créée et l'observateur doit être enregistré à nouveau.

## Mode test (expérience 01)

Avec `PRESENCE_MODE_TEST` activé, l'observateur n'interroge pas le serveur. Il annonce la salle `PRESENCE_SALLE` avec une séance 1 à l'état ACTIVE, sert INFO et NONCE normalement, et accepte toute trame ATTEST de longueur valide sans vérifier la signature, puisqu'il ne connaît aucune clé. Chaque trame est décrite sur le port série :

```
I (52310) attestation: trame reçue id=a7f3c1d09e2b4f60 compteur=184 numero_nonce=51 (courant) longueur=87 rssi=-63
```

`(courant)`, `(precedent)` ou `(inconnu)` indique si le numéro de nonce aurait été accepté. Le RSSI est lu sur la connexion encore ouverte ; `rssi=inconnu` signifie que le téléphone s'était déjà déconnecté. Aucun témoignage n'est produit dans ce mode. Le Wi-Fi reste utile pour régler l'heure si un serveur répond sur `/heure`, mais n'est pas nécessaire.

En mode normal, chaque attestation donne une ligne `attestation acceptée ...` ou `attestation rejetée ... raison=<motif>`, avec les motifs `longueur`, `file_pleine`, `nonce_perime`, `id_inconnu`, `signature`, `compteur`, et `signature_temoin` si l'observateur n'a pas pu signer son témoignage.

## Voyant

| Voyant | Signification |
|---|---|
| Clignotement lent (1 Hz) | Pas de Wi-Fi |
| Allumé fixe | Wi-Fi connecté |
| Bref éclat (150 ms) | Attestation acceptée (ou trame reçue en mode test) |

## Limites connues

- Les témoignages sont gardés en RAM seulement (128 au plus). Une coupure de courant perd ceux qui n'ont pas été envoyés ; si la file déborde, les plus anciens sont écrasés et un avertissement est journalisé.
- La liste des clés et les derniers compteurs sont en RAM : après un redémarrage, l'observateur attend la configuration du serveur avant d'accepter une attestation.
- 64 appareils au plus par salle. Au-delà, les clés suivantes sont ignorées avec un avertissement.
- La clé privée est en NVS sans chiffrement de flash. Le chiffrement prévu par la spécification (flash et NVS chiffrées) reste à activer pour un déploiement réel.
- Le numéro de nonce repart de 1 à chaque démarrage. Le serveur reçoit le nonce lui-même dans chaque témoignage.
- Les requêtes signées attendent la première synchronisation de l'heure, car le serveur refuse un écart de plus de 120 s.
- L'heure n'est corrigée que de la moitié de l'aller-retour HTTP ; la précision attendue est de quelques dizaines de millisecondes sur un réseau local.
- Neuf connexions Bluetooth simultanées au plus. Au-delà, l'annonce reprend à la première déconnexion.
- Une séance close (CLOTUREE ou SCELLEE) est annoncée à l'état 3 pendant 60 s, puis l'observateur revient à l'état AUCUNE.
