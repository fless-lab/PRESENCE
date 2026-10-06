# API du serveur PRESENCE, version 0.1

Ce document complète [`SPEC-v0.1.md`](SPEC-v0.1.md). Il fixe les encodages et chaque route utilisée par l'application, les observateurs et le tableau de bord.

## 1. Encodages communs

| Élément | Encodage |
|---|---|
| Clé publique | P-256, SPKI DER, base64 standard |
| Signature | ECDSA P-256 avec SHA-256, DER, base64 standard |
| Identifiant d'appareil | 16 caractères hexadécimaux minuscules : 8 premiers octets de `SHA-256(SPKI DER)` |
| Hachage, racine | 64 caractères hexadécimaux minuscules |
| Instant | Entier, millisecondes depuis l'époque Unix (UTC) |
| JSON canonique | Clés triées, aucun espace, UTF-8, aucun nombre flottant (sous-ensemble du RFC 8785) |

Entiers binaires : gros-boutistes (`u16`, `u32`, `u64`).

## 2. Requêtes signées

Les téléphones et les équipements (observateurs, porte) signent chaque requête avec leur clé. Aucun mot de passe n'est stocké pour eux.

| En-tête | Valeur |
|---|---|
| `X-Presence-Id` | `app:<id_appareil>` ou `equ:<nom_equipement>` |
| `X-Presence-Horodatage` | Instant de la requête (ms) |
| `X-Presence-Signature` | Signature de la chaîne ci-dessous |

Chaîne signée (UTF-8, séparateur `\n`) :

```
PRESENCE/v0.1/REQ
<MÉTHODE>
<chemin, avec la requête éventuelle : /seances/S-1/pause>
<horodatage>
<SHA-256 du corps en hexadécimal ; corps vide : SHA-256 de la chaîne vide>
```

Le serveur refuse un écart d'horloge supérieur à 120 s et une signature déjà vue dans les 5 dernières minutes.

Le personnel (scolarité, administration, audit) se connecte au tableau de bord avec un matricule et un mot de passe, puis utilise un jeton : `Authorization: Bearer <jeton>`.

## 3. Bluetooth

### Annonce de l'observateur

- Liste des services : l'UUID 128 bits du service PRESENCE.
- Données fabricant, identifiant `0xFFFF` (réservé aux essais), 4 octets : `version (1) | salle (u16) | etat (1)`.

### Caractéristiques

| Caractéristique | Accès | Contenu |
|---|---|---|
| `info` | lecture | `version (1) | salle (u16) | seance (u32) | etat (1)` : 8 octets |
| `nonce` | lecture | `nonce (16) | numero_nonce (u32)` : 20 octets |
| `attest` | écriture | `id_appareil (8) | compteur (u32) | numero_nonce (u32) | signature DER` |

`seance` vaut 0 hors séance. Hors séance, seuls les enseignants répondent : leur attestation prouve qu'ils sont dans la salle avant de démarrer.

### Message signé par le téléphone

```
"PRESENCE/v0.1/ATTEST" | salle (u16) | seance (u32) | nonce (16) | compteur (u32)
```

### Témoignage signé par l'observateur

```
"PRESENCE/v0.1/TEMOIN" | salle (u16) | seance (u32) | nonce (16) | numero_nonce (u32) | recu_ms (u64) | trame attest complète
```

## 4. Routes publiques

| Route | Réponse |
|---|---|
| `GET /sante` | `{statut, version, protocole}` |
| `GET /heure` | `{ms}` : sert aux observateurs à régler leur horloge |
| `GET /cle-serveur` | `{cle_publique}` : vérifie la signature des reçus |
| `GET /ancrages/{seance}` | Ancrage lu sur le registre : `{seance, racine, corrections[], transaction, registre}` |

## 5. Enrôlement d'un téléphone

1. `POST /enrolement/defi` `{matricule}` → `{defi, expire_ms}` (defi : 32 octets en base64)
2. Le téléphone crée sa clé avec ce défi comme challenge d'attestation.
3. `POST /enrolement` :

```json
{
  "matricule": "2025001",
  "code": "K7P-4QX",
  "cle_publique": "<base64 SPKI>",
  "defi": "<base64>",
  "chaine_attestation": ["<base64 DER>", "..."],
  "modele": "Samsung SM-A546B",
  "deverrouillage_recent": true
}
```

Réponse : `{id_appareil, personne: {matricule, nom, prenom, role}, attestation}`. Le code d'enrôlement est à usage unique ; un nouvel enrôlement révoque l'appareil précédent.

## 6. Routes du téléphone (signées `app:`)

| Route | Rôle | Réponse |
|---|---|---|
| `GET /moi` | tous | `{matricule, nom, prenom, role, appareil}` |
| `GET /moi/seance-courante` | étudiant | Séance active d'un de ses cours, fenêtres, statut provisoire, ou `null` |
| `GET /moi/historique` | tous | Séances passées et statut |
| `GET /moi/recus/{seance}` | tous | Reçu de présence (section 9) |
| `GET /enseignant/cours` | enseignant | Cours qu'il enseigne |
| `GET /enseignant/presence` | enseignant | `{salle, nom_salle, depuis_ms}` si attesté dans une salle depuis moins de 3 min, sinon `null` |
| `GET /enseignant/seances` | enseignant | Ses séances récentes |
| `POST /seances` `{cours}` | enseignant | Démarre une séance dans la salle où il est attesté |
| `POST /seances/{id}/pause` | enseignant | |
| `POST /seances/{id}/reprise` | enseignant | |
| `POST /seances/{id}/cloture` | enseignant | |
| `GET /seances/{id}/direct` | enseignant | Fenêtres, liste des inscrits avec statut provisoire |
| `POST /seances/{id}/validations` `{matricule, motif}` | enseignant | Validation manuelle |
| `POST /seances/{id}/decisions` `{matricule, decision, motif}` | enseignant | `decision` : `PRESENT` ou `ABSENT` |
| `POST /seances/{id}/scellement` | enseignant | Merkle et ancrage ; renvoie `{racine, ancrage}` |

## 7. Routes des équipements (signées `equ:`)

`GET /equipements/moi/config` :

```json
{
  "heure_ms": 1791278400000,
  "nom": "204-A",
  "salle": 204,
  "periode_nonce_s": 45,
  "seance": { "numero": 17, "etat": "ACTIVE" },
  "cles": [ { "id": "a7f3c1d09e2b4f60", "cle": "<base64 SPKI>" } ]
}
```

Hors séance, `seance` vaut `null` et `cles` contient les enseignants. En séance, `cles` contient les inscrits et l'enseignant.

`POST /equipements/moi/temoignages` :

```json
{
  "temoignages": [
    {
      "seance": 17,
      "nonce": "<base64 16 octets>",
      "numero_nonce": 51,
      "recu_ms": 1791278412345,
      "trame": "<base64 trame attest>",
      "sig": "<base64 signature de l'observateur>"
    }
  ]
}
```

Réponse : `{acceptes, rejetes: [{index, raison}]}`.

## 8. Routes du personnel (jeton)

| Route | Rôle |
|---|---|
| `POST /auth/connexion` `{matricule, mot_de_passe}` | Renvoie `{jeton, personne}` |
| `GET /admin/apercu` | Séances actives, équipements, chiffres du jour |
| `POST /admin/import` (CSV) | Colonnes : `matricule,nom,prenom,role,cours_code,cours_intitule,groupe` |
| `GET /admin/personnes` | Personnes, appareil, code d'enrôlement en attente |
| `POST /admin/personnes/{matricule}/code` | Nouveau code, révoque l'appareil actuel |
| `GET /admin/salles`, `POST /admin/salles` | Salles |
| `GET /admin/equipements`, `POST /admin/equipements` | Observateurs : `{nom, salle, type, cle_publique}` |
| `GET /admin/seances` | Liste des séances |
| `GET /admin/seances/{id}` | Détail : statuts, fenêtres, racine, ancrage |
| `GET /admin/seances/{id}/evenements` | Journal complet |
| `GET /admin/seances/{id}/export.csv` | Feuille de présence |
| `POST /admin/seances/{id}/corrections` `{matricule, statut, motif}` | Correction scellée et ré-ancrée |
| `GET /audit` | Pour chaque séance scellée : racine recalculée, racine ancrée, conformité |

## 9. Reçu de présence

```json
{
  "v": "0.1",
  "type": "RECU",
  "seance": { "id": "S-20261006-204-01", "cours": "SECRES", "salle": 204 },
  "titulaire": { "matricule": "2025001", "appareil": "a7f3c1d09e2b4f60" },
  "statut": "PRESENT",
  "fenetres": { "validees": 28, "total": 29 },
  "arbre": { "taille": 1630, "racine": "<hex>" },
  "evenements": [ { "index": 0, "canonique": "<JSON canonique>", "preuve": ["<hex>", "..."] } ],
  "ancrage": { "registre": "fabric", "transaction": "<id>" },
  "emis_ms": 1791289000000,
  "sig_serveur": "<base64>"
}
```

Le reçu contient les événements de séance (début, pauses, clôture) et ceux qui concernent le titulaire. Le vérificateur peut donc **recalculer le statut lui-même**, puis vérifier chaque preuve d'inclusion contre la racine lue sur le registre. `sig_serveur` signe le JSON canonique du reçu sans ce champ.

## 10. Contenu des événements

Chaque événement a la forme `{v, type, seance, salle, auteur, horodatage, contenu, preuves}`. Le champ `contenu` dépend du type :

| Type | `auteur` | `contenu` |
|---|---|---|
| `DEBUT` | `ens:<matricule>` | `{cours, enseignant, numero}` |
| `PAUSE`, `REPRISE`, `CLOTURE` | `ens:<matricule>` | `{enseignant}` |
| `ATTESTATION` | `obs:<nom>` | `{appareil, compteur, numero_nonce, nonce}` |
| `VALIDATION_MANUELLE` | `ens:<matricule>` | `{matricule, motif}` |
| `DECISION` | `ens:<matricule>` | `{matricule, decision, motif}` |
| `CORRECTION` | `sco:<matricule>` | `{matricule, statut, motif}` |

`preuves` contient de quoi revérifier l'événement : la trame et la signature du témoin pour une attestation, la requête signée (`{methode, chemin, horodatage, corps, sig}`) pour une action de l'enseignant ou de la scolarité.

Statuts possibles : `PRESENT`, `RETARD`, `DEPART_ANTICIPE`, `PARTIEL`, `A_VERIFIER`, `ABSENT`.

## 11. Preuve d'inclusion

`preuve` est la liste des hachages frères, de la feuille vers la racine. Feuille : `SHA-256(0x00 | canonique en UTF-8)`. Nœud : `SHA-256(0x01 | gauche | droite)`. Vérification (RFC 9162, §2.1.3.2) :

```
fn = index ; sn = taille - 1 ; r = feuille
pour chaque p de preuve :
    si sn == 0 : échec
    si fn est impair ou fn == sn :
        r = noeud(p, r)
        si fn est pair : tant que fn est pair et fn != 0 : fn >>= 1 ; sn >>= 1
    sinon :
        r = noeud(r, p)
    fn >>= 1 ; sn >>= 1
succès si sn == 0 et r == racine
```
