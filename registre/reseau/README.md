# Réseau Hyperledger Fabric

Prototype : le réseau de test de `fabric-samples`, étendu à trois organisations.

| Organisation | Rôle |
|---|---|
| Org1 | Scolarité |
| Org2 | Direction |
| Org3 | Service informatique |

## Lancer

```bash
cd registre/reseau
curl -sSLO https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh
chmod +x install-fabric.sh && ./install-fabric.sh docker samples binary

cd fabric-samples/test-network
./network.sh up createChannel -c presence -ca
cd addOrg3 && ./addOrg3.sh up -c presence && cd ..

./network.sh deployCC -c presence -ccn ancrage \
  -ccp ../../../chaincode/ancrage -ccl go \
  -ccep "OutOf(2,'Org1MSP.peer','Org2MSP.peer','Org3MSP.peer')"
```

La politique d'approbation `OutOf(2, ...)` exige l'accord de deux organisations sur trois pour toute écriture.

Attention : le script `deployCC` du réseau de test installe et approuve le chaincode pour Org1 et Org2 seulement. Pour Org3, il faut compléter avec les commandes `peer lifecycle chaincode install` et `approveformyorg` ; la procédure exacte sera notée ici lors de la mise en place.

Le dossier `fabric-samples/` est ignoré par Git. Prévoir au moins 8 Go de RAM libres pour Docker.

## Brancher le serveur

```bash
cd registre/passerelle && npm install && npm run build && npm start
cd serveur && PRESENCE_REGISTRE=fabric PRESENCE_REGISTRE_URL=http://localhost:8800 uvicorn app.main:app
```
