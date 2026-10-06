# Outils

| Outil | Rôle |
|---|---|
| `presence demo` (serveur) | Remplit la base avec plusieurs jours de séances scellées, à travers les vraies routes et avec de vraies signatures |
| `presence simuler` (serveur) | Joue une séance en direct contre un serveur en marche : observateurs et téléphones simulés, au rythme réel |
| [`verificateur-recu/`](verificateur-recu/) | Vérifie un reçu sans faire confiance au serveur |
| [`demo/falsifier.py`](demo/falsifier.py) | Falsifie la base pour montrer que l'audit le détecte |
| [`simulateur-telephone/`](simulateur-telephone/) | Pistes pour simuler un téléphone en Bluetooth réel |

## Démonstration complète sans matériel

```bash
cd serveur && source .venv/bin/activate
export PRESENCE_BASE_URL=sqlite:///./donnees/presence.db PRESENCE_DONNEES=./donnees
presence demo                          # 2 cours, 18 étudiants, séances des jours précédents
uvicorn app.main:app --host 0.0.0.0 &  # API
cd ../web && npm run dev &             # tableau de bord : http://localhost:5173, A001 / presence
cd ../serveur && presence simuler --minutes 5 --periode 15   # séance en direct, visible dans « Aperçu »

python ../outils/verificateur-recu/verifier.py recu_E001.json --serveur http://localhost:8000
python ../outils/demo/falsifier.py --base $PRESENCE_BASE_URL --seance <id> --matricule E005
# puis « Audit » dans le tableau de bord : la séance falsifiée apparaît en écart
```
