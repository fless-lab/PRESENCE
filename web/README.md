# Interface web

Tableau de bord de la phase 1, en React (Vite).

| Page | Contenu |
|---|---|
| Séances | Liste des séances, statuts par étudiant, export |
| Séance en direct | Fenêtres validées, alertes, état des observateurs |
| Audit | Recalcul des racines et comparaison avec le registre |
| Vérifier un reçu | Dépôt d'un reçu, vérification de la preuve d'inclusion contre la racine ancrée |
| Import | Fichier CSV des cours et des inscrits |

## Créer le projet

```bash
cd web
npm create vite@latest . -- --template react-ts
npm install && npm run dev
```
