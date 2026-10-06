# Passerelle Fabric

Petit service HTTP (Node.js, TypeScript) entre le serveur Python et le chaincode d'ancrage. Il utilise le SDK officiel Fabric Gateway, qui n'existe pas en Python.

| Route | Rôle | Fonction du chaincode |
|---|---|---|
| `POST /ancrages` | Ancre la racine d'une séance | `AncrerSeance` |
| `POST /corrections` | Ancre une correction liée à la racine précédente | `AncrerCorrection` |
| `GET /ancrages/{seance}` | Lit l'ancrage et ses corrections | `LireAncrage` |
| `GET /sante` | État du service | |

## Lancer

Une fois le réseau de test démarré et le chaincode déployé (voir [`../reseau/`](../reseau/)) :

```bash
cd registre/passerelle
npm install && npm run build && npm start      # port 8800
```

Puis, côté serveur : `PRESENCE_REGISTRE=fabric PRESENCE_REGISTRE_URL=http://localhost:8800`.

| Variable | Défaut |
|---|---|
| `PASSERELLE_PORT` | `8800` |
| `FABRIC_CANAL`, `FABRIC_CHAINCODE` | `presence`, `ancrage` |
| `FABRIC_MSP`, `FABRIC_PAIR`, `FABRIC_HOTE_PAIR` | `Org1MSP`, `localhost:7051`, `peer0.org1.example.com` |
| `FABRIC_ORGANISATIONS` | dossier `org1.example.com` du réseau de test |
| `FABRIC_CERTIFICAT`, `FABRIC_CLE`, `FABRIC_TLS` | identité `User1` et certificat TLS du pair, dans ce dossier |

La passerelle soumet au nom de l'organisation Scolarité (Org1). Le SDK sollicite automatiquement les pairs nécessaires pour satisfaire la politique d'approbation 2 sur 3.

## Tests

`npm test` démarre le serveur HTTP avec un contrat en mémoire et vérifie l'ancrage, le refus d'un double ancrage, la correction et la lecture.
