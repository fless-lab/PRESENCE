# Serveur

API PRESENCE en Python (FastAPI) avec PostgreSQL.

## Lancer en développement

```bash
cd serveur
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env
uvicorn app.main:app --reload     # http://localhost:8000/docs
pytest                            # tests
```

La base se lance avec `infra/docker-compose.yml`, qui applique automatiquement `migrations/`.

## Organisation

| Dossier | Rôle |
|---|---|
| `app/api/` | Routes HTTP (santé, enrôlement, séances, événements, reçus, audit, imports) |
| `app/domaine/` | Logique métier sans dépendance web ni base : Merkle, fenêtres, statuts, cardinalité |
| `app/registre/` | Seul module qui parle à Hyperledger Fabric |
| `app/stockage/` | Accès à PostgreSQL |
| `migrations/` | Schéma SQL, un fichier par évolution |
| `tests/` | Tests automatiques |

## Déjà en place

- `GET /sante`
- `app/domaine/merkle.py` : racine, preuves d'inclusion et vérification selon le RFC 9162, avec tests sur des arbres de 1 à 33 feuilles.
- Schéma initial, avec un déclencheur qui interdit toute modification ou suppression d'un événement.
