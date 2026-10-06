# Feuille de route

Le projet avance en trois phases. Chaque phase livre un système qui fonctionne de bout en bout ; la suivante l'enrichit sans rien casser.

## Phase 1. Système complet, projet Cryptographie et Blockchain

**Objectif** : une séance réelle suivie de bout en bout, de l'attestation du téléphone jusqu'au reçu vérifiable, avec un historique scellé sur le registre.

| Brique | Livrable |
|---|---|
| Application Android | Enrôlement, réponse aux balises en arrière-plan, séance en cours, historique, reçus ; rôle enseignant : démarrer, pause, reprise, clôture, liste en direct, validation manuelle |
| Observateurs | 2 ESP32 dans la salle : balise, nonce, vérification des signatures, témoignages signés, stockage hors ligne |
| Serveur | Enrôlement, import CSV, séances, fenêtres et statuts, arbre de Merkle, reçus avec preuve d'inclusion |
| Registre | Réseau Fabric à 3 organisations, chaincode d'ancrage, politique 2 sur 3 |
| Tableau de bord web | Suivi des présences par séance, audit d'intégrité, vérification d'un reçu |
| Vérificateur indépendant | Outil en ligne de commande qui vérifie un reçu sans passer par le serveur |

**Matériel** : 2 ESP32 (un troisième optionnel pour simuler une deuxième salle), les téléphones, un PC.

**Démonstration** : séance complète avec pause ; rejeu refusé ; falsification de la base détectée à l'audit ; une organisation seule incapable de réécrire l'ancrage ; reçu vérifié par un tiers.

| Semaines | Travail |
|---|---|
| 1 et 2 | Expérience 01 (Bluetooth), base de données, serveur minimal, réseau Fabric de test |
| 3 à 5 | Protocole complet sur l'application et les observateurs, ingestion des événements |
| 6 et 7 | Merkle, chaincode, ancrage, reçus, vérificateur |
| 8 | Tableau de bord, scénarios de démonstration, mesures du registre |

## Phase 2. Preuve physique renforcée, projet d'innovation

**Objectif** : détecter une personne qui porte plusieurs identifiants, sans biométrie.

| Brique | Livrable |
|---|---|
| Compteur de porte | ESP32 et 2 VL53L1X, passages signés, occupation |
| Règle de cardinalité | Alerte en cours de séance, localisation des suspects par appariement |
| Expériences | Précision du compteur, courbe de détection de la fraude, red team, passage à l'échelle |
| Pilote | Quelques semaines sur un vrai cours, avec consentement |

Les événements `PASSAGE` rejoignent l'arbre de Merkle : la phase 1 n'est pas modifiée, elle reçoit un type d'événement de plus.

## Phase 3. Vers un produit

| Chantier | Contenu |
|---|---|
| Déploiement | Plusieurs salles et bâtiments, gestion de flotte des ESP32, mises à jour à distance |
| Clients | Application iOS, SDK à intégrer dans l'application d'une école ou d'une entreprise |
| Intégrations | Export vers les systèmes de scolarité, de ressources humaines ou de paie |
| Registre | Ancrage public léger pour une organisation seule, consortium pour plusieurs |
| Conformité | Analyse d'impact sur la vie privée, démarches auprès de l'autorité de protection des données |
| Cas d'usage | Formation financée, sous-traitance sur chantier, pointage en entreprise |
