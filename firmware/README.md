# Firmware ESP32

Deux programmes ESP-IDF qui partagent un composant commun.

| Dossier | Rôle | Phase |
|---|---|---|
| [`observateur/`](observateur/) | Balise de salle, nonce, vérification des attestations, témoignages signés, envoi au serveur | 1 |
| [`porte/`](porte/) | Compteur de passages avec deux VL53L1X | 2 |
| [`composants/presence_protocole/`](composants/presence_protocole/) | Constantes et formats du protocole, partagés | 1 |

## Prérequis

ESP-IDF 5.x installé (`idf.py --version`). Pile Bluetooth : NimBLE, activée par `sdkconfig.defaults`.

## Compiler et flasher

```bash
cd firmware/observateur
idf.py set-target esp32      # ou esp32s3
idf.py build flash monitor
```

## Configuration locale

Le nom du réseau Wi-Fi, le mot de passe, l'adresse du serveur et l'identifiant de salle se règlent avec `idf.py menuconfig`, menu *PRESENCE*. Ces valeurs ne sont jamais committées (`sdkconfig` est ignoré par Git).
