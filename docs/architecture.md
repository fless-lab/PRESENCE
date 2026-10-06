# Architecture

## Principe

PRESENCE combine quatre garanties indépendantes :

| Niveau | Question | Mécanisme |
|---|---|---|
| Identité | L'identifiant est-il authentique ? | Clé non exportable dans le téléphone, signature ECDSA |
| Proximité | L'identifiant est-il dans la salle, maintenant ? | Nonce tournant émis par les observateurs, réponse en Bluetooth |
| Cardinalité | Y a-t-il autant de personnes que d'identifiants ? | Comptage anonyme à la porte |
| Intégrité | L'historique a-t-il été modifié ? | Arbre de Merkle, racine ancrée sur un registre à trois organisations |

La blockchain ne prouve pas la présence. Elle prouve que les événements enregistrés n'ont pas été modifiés depuis leur scellement.

## Composants

| Composant | Emplacement | Responsabilités |
|---|---|---|
| Application PRESENCE | Téléphones Android | Enrôlement ; réponse aux balises en arrière-plan ; pilotage de séance (rôle enseignant) ; historique et reçus |
| Observateur | 2 ESP32 par salle, coins opposés, environ 2 m de haut | Balise et nonce ; vérification des signatures ; témoignages signés ; stockage si le réseau coupe |
| Compteur de porte | 1 ESP32 sur le cadre de la porte, 2 capteurs VL53L1X à 1 m de haut | Sens et nombre de passages ; occupation ; événements signés |
| Serveur | PC ou serveur de l'établissement | Enrôlement ; séances ; fenêtres ; cardinalité ; Merkle ; reçus ; API pour le web |
| Registre | Réseau Hyperledger Fabric, 3 organisations | Ancrage des racines ; historique des corrections |
| Interface web | Navigateur | Suivi des présences ; import CSV ; audit d'intégrité ; vérification des reçus |

## Installation physique (prototype)

```
                         SALLE 204
   ┌───────────────────────────────────────────────┐
   │  [OBS A]                         TABLEAU       │
   │                                                │
   │                 tables, étudiants              │
   │                                                │
   │                                       [OBS B]  │
   └───────────────────────[PORTE]──────────────────┘
                           [PORTE P]  2× VL53L1X sur le même montant

   Autre salle : [OBS C], pour la démonstration de double présence
   Réseau : un routeur Wi-Fi dédié relie les ESP32 et le serveur
```

## Déroulé d'une séance

1. **En continu** : le compteur de porte suit l'occupation ; les observateurs annoncent « aucune séance ».
2. **Démarrage** : l'enseignant, attesté dans la salle, choisit son cours. Événement `DEBUT` signé.
3. **Préparation** : le serveur envoie aux observateurs les clés publiques des inscrits.
4. **Attestations** : nonce renouvelé toutes les 45 s ; chaque téléphone se connecte, signe, écrit, se déconnecte ; l'observateur vérifie et témoigne.
5. **Calcul** : toutes les 5 min, fenêtres validées par appareil et contrôle de cardinalité.
6. **Pause et reprise** : événements signés, temps exclu du calcul.
7. **Clôture** : statuts, arbitrage des cas « à vérifier », validations manuelles.
8. **Scellement** : arbre de Merkle de tous les événements, ancrage de la racine.
9. **Reçus** : chaque personne récupère son reçu avec la preuve d'inclusion.

## Cas particuliers

| Situation | Comportement |
|---|---|
| Téléphone déchargé | Validation manuelle par l'enseignant, marquée comme telle |
| Visiteur sans application | Compté à la porte, sans identifiant : aucune alerte |
| Coupure Wi-Fi | Les ESP32 conservent leurs événements et les renvoient |
| Rejeu d'une ancienne réponse | Refusé : nonce et compteur ne correspondent plus |
| Même appareil dans deux salles | Statut « à vérifier » |
| Modification de la base | Détectée à l'audit : la racine recalculée diffère de la racine ancrée |
| Dérive du compteur | Remise à zéro nocturne et tolérance dans la règle |

## Hors du périmètre de la V1

Gestion d'emploi du temps, client iOS, UWB, justificatifs en ligne, changement de téléphone en libre-service, suivi des heures des enseignants. Ces éléments restent dans la vision cible.
