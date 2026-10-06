# Protocole PRESENCE, version 0.1 (brouillon)

Ce document décrit le protocole de façon assez précise pour qu'une autre équipe puisse écrire un client ou un observateur compatible. Les valeurs numériques sont centralisées dans [`constantes.yaml`](constantes.yaml). Les points marqués **[À valider]** dépendent des expériences des semaines 1 et 2.

## 1. Rôles

| Rôle | Porteur | Clé |
|---|---|---|
| Étudiant | Téléphone Android | Paire ECDSA P-256 générée dans le Keystore, non exportable |
| Enseignant | Téléphone Android | Idem, avec le droit de piloter une séance |
| Observateur | ESP32 fixé dans une salle | Paire générée au premier démarrage, stockée en flash chiffrée |
| Compteur de porte | ESP32 sur le cadre de la porte | Idem |
| Serveur | API PRESENCE | Paire de service, pour signer les reçus |
| Organisation du registre | Scolarité, Direction, Service informatique | Identités Fabric (MSP) |

Un **identifiant d'appareil** vaut les 8 premiers octets de `SHA-256(clé publique encodée en SPKI DER)`.

## 2. Enrôlement

1. L'utilisateur s'authentifie avec son compte de l'établissement.
2. L'application génère la paire de clés dans le Keystore (StrongBox si disponible), avec l'attestation de clé activée et l'option « déverrouillage récent » **[À valider]**.
3. Elle envoie au serveur la clé publique et la chaîne de certificats d'attestation.
4. Le serveur vérifie la chaîne, refuse les clés logicielles, puis enregistre `personne ↔ appareil ↔ clé publique`. Un seul appareil actif par compte.

## 3. Balise de salle (Bluetooth Low Energy)

Chaque observateur annonce le service `service_uuid`, et dans les données fabricant (identifiant `0xFFFF`, réservé aux essais) :

| Champ | Taille | Description |
|---|---|---|
| version | 1 octet | `0x01` pour la v0.1 |
| salle | 2 octets | Identifiant numérique de la salle |
| etat | 1 octet | 0 aucune séance, 1 active, 2 pause, 3 clôturée |

Le détail des encodages et des routes du serveur est dans [`API.md`](API.md).

Caractéristiques GATT :

| Caractéristique | Accès | Contenu |
|---|---|---|
| `info` | lecture | version (1), salle (2), seance (4), etat (1) |
| `nonce` | lecture | nonce (16), numero_nonce (4) |
| `attest` | écriture | attestation (section 4) |

Le nonce est tiré par un générateur aléatoire matériel et renouvelé toutes les `renouvellement_nonce_s` secondes. L'observateur accepte aussi le nonce précédent pendant 10 secondes, pour couvrir un renouvellement survenu pendant l'échange.

## 4. Attestation

### Message signé par le téléphone

```
m = "PRESENCE/v0.1/ATTEST" || salle (2) || seance (4) || nonce (16) || compteur (4)
signature = ECDSA_P256_SHA256(cle_privee_appareil, m)
```

Le `compteur` est un entier strictement croissant tenu par l'application. Il permet de rejeter un ordre incohérent.

### Trame écrite dans `attest`

| Champ | Taille |
|---|---|
| id_appareil | 8 |
| compteur | 4 |
| numero_nonce | 4 |
| signature DER | 70 à 72 |

Total : 86 à 88 octets. Le téléphone demande un MTU de `mtu_demande` avant d'écrire.

### Traitement par l'observateur

1. Retrouver la clé publique à partir de `id_appareil` dans la liste des inscrits reçue au démarrage de la séance. Inconnu : rejet.
2. Vérifier que `numero_nonce` correspond au nonce courant ou au précédent.
3. Reconstituer `m` et vérifier la signature.
4. Vérifier `compteur > dernier_compteur(id_appareil)`.
5. Enregistrer un **témoignage** : la trame, l'heure locale et l'identifiant de l'observateur, le tout signé avec la clé de l'observateur.

## 5. Compteur de porte

Deux capteurs de distance sont placés côte à côte sur le même montant, l'un côté couloir (C), l'autre côté salle (S). Un passage est validé quand les deux faisceaux sont coupés dans un ordre net et en moins de `x` ms **[À valider]**.

- C puis S : `PASSAGE{sens: ENTREE}`
- S puis C : `PASSAGE{sens: SORTIE}`

Chaque passage porte un numéro de séquence et est signé par le compteur. L'occupation vaut `entrées − sorties`, remise à zéro chaque nuit.

## 6. Événements côté serveur

Tous les événements sont stockés sous une forme JSON canonique (RFC 8785, JCS) puis hachés en SHA-256.

```json
{
  "v": "0.1",
  "type": "ATTESTATION",
  "seance": "S-2026-1006-204-01",
  "salle": 204,
  "auteur": "obs:204-A",
  "horodatage": 1791278372120,
  "contenu": { "appareil": "a7f3c1d09e2b4f60", "compteur": 184, "numero_nonce": 51, "nonce": "q3Zx…" },
  "preuves": { "trame": "<base64>", "sig_temoin": "MEQCIA…" }
}
```

| Type | Auteur | Rôle |
|---|---|---|
| DEBUT, PAUSE, REPRISE, CLOTURE | Enseignant | Pilotage de la séance |
| ATTESTATION | Observateur (avec la signature de l'appareil) | Présence d'un identifiant |
| PASSAGE | Compteur de porte | Entrée ou sortie physique |
| VALIDATION_MANUELLE, DECISION | Enseignant | Présence sans attestation, arbitrage d'un « À vérifier » |
| CORRECTION | Scolarité | Justificatif accepté, erreur corrigée ; référence l'événement visé |

Aucun événement n'est jamais modifié ni supprimé. Une correction est un nouvel événement.

## 7. Calcul de la présence

L'algorithme est déterministe : le serveur et tout vérificateur de reçu doivent obtenir le même résultat.

1. **Intervalles actifs** : de `DEBUT` à la première `PAUSE` ou `CLOTURE`, puis de chaque `REPRISE` à la `PAUSE` ou `CLOTURE` suivante. Une séance non clôturée s'arrête à l'instant présent.
2. **Axe actif** : les intervalles sont mis bout à bout. Un instant `t` situé dans l'intervalle `k` a pour position `somme des longueurs précédentes + (t − début_k)`. Un instant hors des intervalles est ignoré.
3. **Nombre de fenêtres** : `W = 300 000 ms`, `A` = durée active totale. `N = ⌊A / W⌋`, plus 1 si le reste vaut au moins 120 000 ms. Si `A > 0` et `N = 0`, alors `N = 1`.
4. **Fenêtres validées** `V` : indices `⌊position / W⌋ < N` contenant au moins une attestation de l'appareil.
5. **Statut**, dans cet ordre :
   - une `CORRECTION` existe pour la personne : son statut (la plus récente) ;
   - une `DECISION` existe : `PRESENT` ou `ABSENT` (la plus récente) ;
   - une `VALIDATION_MANUELLE` existe : `PRESENT`, marqué manuel ;
   - l'appareil a été attesté dans une autre salle à moins de 120 s d'écart : `A_VERIFIER` ;
   - `|V| = 0` : `ABSENT` ;
   - `|V| / N ≥ 0,8` : `PRESENT` ;
   - sinon, avec `f = min(V)` et `l = max(V)` :
     - `f ≥ 3` et `|V| / (N − f) ≥ 0,8` : `RETARD` ;
     - `l ≤ N − 3` et `|V| / (l + 1) ≥ 0,8` : `DEPART_ANTICIPE` ;
     - sinon : `PARTIEL`.

Trois fenêtres correspondent aux 15 minutes de `retard_apres_s`.

## 8. Règle de cardinalité

Pour chaque fenêtre `f` :

```
identites(f)  = nombre d'appareils d'étudiants attestés pendant f
occupation(f) = occupation maximale mesurée à la porte pendant f, moins l'enseignant
excedent(f)   = identites(f) − occupation(f)
alerte si excedent(f) > tolerance_cardinalite
```

**Localisation des suspects.** Chaque entrée à la porte est appariée à la première attestation d'un appareil qui suit, dans un délai de `fenetre_appariement_entree_s`. Un appareil qui ne peut être apparié à aucune entrée est marqué *à vérifier*. **[À valider]** : la formulation exacte de l'appariement (glouton ou par appariement biparti optimal) sera fixée après les premières mesures de délai de détection.

## 9. Scellement

### Arbre de Merkle

Construction inspirée du RFC 6962, pour éviter les ambiguïtés entre feuilles et nœuds :

```
feuille(e)  = SHA-256(0x00 || JCS(e))
noeud(g, d) = SHA-256(0x01 || g || d)
```

Les feuilles sont triées par `(horodatage, hachage)`. Pour un nombre de feuilles qui n'est pas une puissance de deux, on découpe à la plus grande puissance de deux strictement inférieure, comme dans le RFC 6962.

### Ancrage

À la clôture, le serveur appelle `AncrerSeance(seance, salle, debut, fin, racine, nb_evenements, observateurs)` sur le chaincode. La politique d'approbation exige 2 organisations sur 3. Le chaincode refuse un deuxième ancrage du même identifiant. Une correction ultérieure passe par `AncrerCorrection(seance, nouvelle_racine, racine_precedente, motif)`.

Aucune donnée personnelle n'est écrite sur le registre.

## 10. Reçu de présence

```json
{
  "v": "0.1",
  "seance": "S-2026-1006-204-01",
  "evenements": [ { "...": "événements de l'appareil" } ],
  "statut": "PRESENT",
  "preuves_inclusion": [ ["<hachage>", "G|D"], "..." ],
  "racine": "<hex>",
  "ancrage": { "canal": "presence", "transaction": "<id>", "bloc": 18402 },
  "sig_serveur": "MEUCIQ…"
}
```

Vérification indépendante : recalculer chaque feuille, remonter la preuve jusqu'à la racine, comparer avec la racine lue sur le registre.

## 11. Évolutions prévues

- Client iOS (même protocole).
- Mesure de distance UWB pour contrer le relais Bluetooth.
- Ancrage public alternatif pour une organisation unique.
