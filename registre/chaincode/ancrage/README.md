# Chaincode d'ancrage

| Fonction | Rôle |
|---|---|
| `AncrerSeance(seance, salle, debut, fin, racine, nbEvenements, observateursJSON)` | Enregistre la racine de Merkle d'une séance. Refuse un second ancrage du même identifiant. |
| `AncrerCorrection(seance, nouvelleRacine, racinePrecedente, motif)` | Ajoute une nouvelle racine liée à la précédente, sans l'effacer. |
| `LireAncrage(seance)` | Renvoie l'ancrage et l'historique de ses corrections. |

L'organisation qui soumet chaque transaction est enregistrée (`MSPID`). Aucune donnée personnelle n'est stockée.

## Compiler

```bash
go mod tidy      # télécharge les dépendances et crée go.sum
go build ./...
```

Le code n'a pas encore été compilé contre ses dépendances : lancer `go mod tidy` puis `go build` au premier usage et corriger si besoin.
