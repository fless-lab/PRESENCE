# Registre

| Dossier | Contenu |
|---|---|
| [`chaincode/ancrage/`](chaincode/ancrage/) | Chaincode Go : ancrage des racines de séance et des corrections |
| [`reseau/`](reseau/) | Lancement du réseau Fabric à trois organisations |
| [`passerelle/`](passerelle/) | Service HTTP qui soumet les transactions au chaincode pour le serveur |

Le serveur accède au registre uniquement par `serveur/app/registre/`, ce qui permet de changer de registre sans toucher au reste.

Sans réseau Fabric, le serveur utilise un registre local (`PRESENCE_REGISTRE=local`) : un journal en ajout seul dont chaque entrée est chaînée à la précédente. Il suffit pour développer et pour la démonstration, mais il n'apporte pas la garantie d'un registre partagé entre organisations.
