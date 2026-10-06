# PRESENCE

**Preuve de présence physique vérifiable et sans biométrie.**

PRESENCE établit qu'un identifiant personnel, lié au matériel du téléphone, est resté physiquement dans une salle pendant une séance. Il confronte le nombre d'identifiants attestés au nombre de personnes réellement comptées à la porte, puis scelle l'ensemble des événements dans un registre distribué que personne, pas même l'institution, ne peut réécrire sans que cela se voie.

Projet de 5e année, filière SESNUM, INPT Rabat (2026-2027). Il sert de base commune à deux projets :

| Projet | Contribution propre |
|---|---|
| Cryptographie et Blockchain | Événements signés, arbres de Merkle, chaincode d'ancrage à trois organisations, reçus vérifiables, vérificateur indépendant |
| Innovation | Présence continue, règle de cardinalité (identités face au comptage physique), détection et localisation de la fraude, expérimentations et pilote |

## Vue d'ensemble

```
 Téléphone (identifiant)          Salle                           Serveur               Registre
 ┌────────────────────┐   BLE    ┌──────────────────────┐  Wi-Fi  ┌─────────────────┐    ┌──────────────┐
 │ App PRESENCE       │ ───────▶ │ Observateurs ESP32   │ ──────▶ │ API + PostgreSQL│ ──▶│ Hyperledger  │
 │ clé non exportable │          │ (nonce, vérification)│         │ fenêtres,       │    │ Fabric       │
 └────────────────────┘          ├──────────────────────┤         │ cardinalité,    │    │ 3 organis.   │
                                 │ Compteur de porte    │ ──────▶ │ Merkle, reçus   │    │ chaincode    │
                                 │ ESP32 + 2× VL53L1X   │         └─────────────────┘    └──────────────┘
                                 └──────────────────────┘
```

Le fonctionnement détaillé est décrit dans [`docs/architecture.md`](docs/architecture.md) et le protocole dans [`protocole/SPEC-v0.1.md`](protocole/SPEC-v0.1.md).

## Organisation du dépôt

| Dossier | Contenu | Technologie |
|---|---|---|
| [`protocole/`](protocole/) | Spécification du protocole et constantes partagées (UUID, formats) | Markdown, YAML |
| [`docs/`](docs/) | Architecture, modèle de menace, décisions, matériel, expériences | Markdown |
| [`app-android/`](app-android/) | Application mobile, rôles étudiant et enseignant | Kotlin |
| [`firmware/`](firmware/) | Observateur de salle et compteur de porte | C, ESP-IDF |
| [`serveur/`](serveur/) | API, calcul des fenêtres et de la cardinalité, Merkle, reçus | Python, FastAPI, PostgreSQL |
| [`registre/`](registre/) | Réseau Fabric et chaincode d'ancrage | Go, Hyperledger Fabric |
| [`web/`](web/) | Interface de suivi, d'audit et de vérification des reçus | React |
| [`outils/`](outils/) | Vérificateur de reçus en ligne de commande, simulateur de téléphone | Python |
| [`infra/`](infra/) | Lancement local de la base et du serveur | Docker Compose |

Chaque dossier contient un `README.md` qui décrit son rôle, comment le lancer et ce qu'il reste à faire.

## Démarrer

```bash
# Base de données et serveur
cd infra && docker compose up -d

# Serveur en développement
cd serveur && python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]" && uvicorn app.main:app --reload

# Firmware (ESP-IDF 5.x installé)
cd firmware/observateur && idf.py set-target esp32 && idf.py build flash monitor
```

## Feuille de route

| Phase | Contenu | Projet |
|---|---|---|
| **1** | Système complet sans capteur : application, 2 observateurs ESP32, serveur, Merkle, registre Fabric, tableau de bord, reçus vérifiables | Cryptographie et Blockchain |
| **2** | Compteur de porte, règle de cardinalité, expériences, pilote | Innovation |
| **3** | Multi-salles, iOS, intégrations, conformité : passage au produit | Suite |

Le détail est dans [`docs/feuille-de-route.md`](docs/feuille-de-route.md). Le projet en est au début de la phase 1, avec l'expérience de faisabilité Bluetooth décrite dans [`docs/experiences/01-faisabilite-ble.md`](docs/experiences/01-faisabilite-ble.md).

## Auteur

Abdou-Raouf ATARMLA, INPT Rabat.
