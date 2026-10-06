# Serveur

API PRESENCE en Python (FastAPI, SQLAlchemy). PostgreSQL en production, SQLite accepté pour les essais. Le contrat suivi est [`protocole/API.md`](../protocole/API.md).

## Lancer en développement

```bash
cd serveur
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"

# Base SQLite locale, registre local : aucun service à installer
export PRESENCE_BASE_URL=sqlite:///./donnees/presence.db PRESENCE_DONNEES=./donnees
presence creer-personnel A001 --nom Admin --role ADMIN      # compte du tableau de bord
uvicorn app.main:app --reload --host 0.0.0.0                 # http://localhost:8000/docs
pytest                                                       # 57 tests
```

`--host 0.0.0.0` rend le serveur joignable par les téléphones et les ESP32 du réseau local.

## Configuration

| Variable | Rôle | Défaut |
|---|---|---|
| `PRESENCE_BASE_URL` | Base de données (SQLAlchemy) | PostgreSQL local |
| `PRESENCE_DONNEES` | Dossier de la clé du serveur, du secret des jetons et du registre local | `./donnees` |
| `PRESENCE_REGISTRE` | `local` (journal chaîné en fichier) ou `fabric` (passerelle) | `local` |
| `PRESENCE_REGISTRE_URL` | Adresse de la passerelle Fabric | `http://localhost:8800` |
| `PRESENCE_ATTESTATION` | `souple` (accepte un téléphone sans chaîne d'attestation) ou `stricte` | `souple` |
| `PRESENCE_CORS` | Origines du tableau de bord | `http://localhost:5173` |

Pour comparer la racine d'attestation Android aux racines Google, déposer leurs certificats dans `donnees/racines_attestation.pem`.

## Organisation

| Dossier | Rôle |
|---|---|
| `app/api/` | Routes : publiques, enrôlement, téléphone, enseignant, équipements, personnel et audit |
| `app/domaine/` | Logique pure : JSON canonique, arbre de Merkle (RFC 9162), calcul de la présence (SPEC §7) |
| `app/services/` | Événements, séances, témoignages, reçus, import et enrôlement |
| `app/securite/` | Clés et signatures, requêtes signées, jetons du personnel, attestation Android |
| `app/registre/` | Registre local et client de la passerelle Fabric : seul code lié au registre |
| `tests/` | Tests unitaires et scénario complet avec téléphones et observateurs simulés |

## Garanties mises en œuvre

- **Requêtes signées** : chaque téléphone et chaque équipement signe ses requêtes ; horloge contrôlée à 120 s près, signatures rejouées refusées.
- **Double vérification des attestations** : le serveur revérifie la signature de l'observateur et celle du téléphone. Un observateur compromis ne peut pas fabriquer une présence.
- **Anti-rejeu** : un même compteur d'appareil n'est accepté qu'une fois.
- **Événements immuables** : sur PostgreSQL, un déclencheur interdit toute modification ou suppression. Une correction est un nouvel événement.
- **Scellement** : arbre de Merkle de tous les événements de la séance, racine ancrée sur le registre ; les corrections sont ancrées et chaînées à la racine précédente.
- **Reçus** : signés par le serveur, avec les preuves d'inclusion et assez d'événements pour recalculer le statut.
- **Audit** : recalcul des racines depuis la base et comparaison avec le registre.
