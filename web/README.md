# Interface web

Tableau de bord du personnel (scolarité, administration, audit) et vérificateur public de reçus. React 18, TypeScript strict, Vite, sans bibliothèque de composants.

## Lancer

```bash
cd web
npm install
npm run dev        # http://localhost:5173
```

En développement, les appels à `/api/...` sont relayés par Vite vers le serveur FastAPI (`http://localhost:8000`), préfixe retiré : `/api/admin/apercu` devient `/admin/apercu`.

| Commande | Rôle |
|---|---|
| `npm run dev` | Serveur de développement avec relais `/api` |
| `npm run build` | Vérification des types puis construction dans `dist/` |
| `npm run preview` | Sert le contenu de `dist/` |
| `npm run lint` | ESLint (typescript-eslint, règles des hooks React) |
| `npm test` | Tests unitaires (Vitest) |

## Configuration

| Variable | Défaut | Rôle |
|---|---|---|
| `VITE_API_URL` | `/api` | Adresse de base de l'API vue par le navigateur. En production, mettre l'adresse publique du serveur, ou garder `/api` derrière un relais équivalent. |
| `VITE_API_PROXY` | `http://localhost:8000` | Cible du relais `/api`, en développement seulement. |

Voir `.env.example`. Le jeton de session est gardé dans `sessionStorage` et envoyé en `Authorization: Bearer`.

## Pages

| Route | Contenu | Routes du serveur |
|---|---|---|
| `/connexion` | Matricule et mot de passe | `POST /auth/connexion` |
| `/` | Aperçu : chiffres du jour, séances en cours, équipements | `GET /admin/apercu` |
| `/seances` | Liste filtrable par état | `GET /admin/seances` |
| `/seances/:id` | Déroulement, statuts et fenêtres par étudiant, intégrité, journal, export CSV, correction. Rafraîchi toutes les 10 s si la séance est active ou en pause | `GET /admin/seances/{id}`, `.../evenements`, `.../export.csv`, `POST .../corrections` |
| `/personnes` | Import CSV, appareils, codes d'enrôlement | `GET /admin/personnes`, `POST /admin/import`, `POST /admin/personnes/{matricule}/code` |
| `/salles` | Salles et équipements, dernier contact | `GET/POST /admin/salles`, `GET/POST /admin/equipements` |
| `/audit` | Racine recalculée et racine ancrée pour chaque séance scellée | `GET /audit` |
| `/verifier` | Vérification d'un reçu, accessible sans compte | `GET /ancrages/{seance}`, `GET /cle-serveur` |

Les formes de réponse attendues sont décrites dans `src/api/types.ts`, avec les hypothèses en tête de fichier.

## Vérification d'un reçu

Tout se passe dans le navigateur, avec WebCrypto. Le reçu n'est pas envoyé au serveur ; seules la racine ancrée et la clé publique du serveur sont lues. Le code est dans `src/verif/`.

1. **Preuves d'inclusion** (`merkle.ts`). Pour chaque événement, la feuille vaut `SHA-256(0x00 | canonique en UTF-8)`. La preuve est remontée selon le RFC 9162 (API.md, section 11) jusqu'à `arbre.racine`, avec `arbre.taille`. Chaque événement doit aussi appartenir à la séance du reçu.
2. **Ancrage** (`recu.ts`). La racine lue par `GET /ancrages/{seance}` doit être celle du reçu. S'il y a eu des corrections, c'est la racine de la dernière correction qui fait foi.
3. **Signature du serveur** (`signature.ts`, `canonique.ts`). Le reçu sans `sig_serveur` est mis sous forme canonique (clés triées par point de code, sans espace, identique à `json.dumps(sort_keys=True, separators=(',', ':'), ensure_ascii=False)`), puis la signature ECDSA P-256 / SHA-256 est vérifiée avec la clé de `GET /cle-serveur`. La signature DER est convertie en `r || s` sur 64 octets, format attendu par WebCrypto.
4. **Statut recalculé** (`statut.ts`). L'algorithme de la SPEC, section 7, est rejoué sur les événements du reçu : intervalles actifs (DEBUT, PAUSE, REPRISE, CLOTURE), fenêtres de 5 min avec la règle du reste d'au moins 120 s, attestations de l'appareil du titulaire, puis CORRECTION, DECISION et VALIDATION_MANUELLE du titulaire. Le statut et le nombre de fenêtres doivent correspondre au reçu.

La règle « attesté dans une autre salle à moins de 120 s » (`A_VERIFIER`) dépend d'événements qui ne figurent pas dans le reçu. Si le reçu indique `A_VERIFIER`, ce contrôle est marqué non concluant plutôt qu'en échec.

Convention retenue pour l'axe actif : un intervalle est pris comme `[début, fin[`. Une attestation horodatée exactement à une pause ou à la clôture est ignorée. Le serveur doit appliquer la même règle. Si une séance n'est pas clôturée, `emis_ms` du reçu sert d'instant présent.

## Tests

`npm test` couvre l'arbre de Merkle (1 à 20 feuilles, toutes les preuves, feuille altérée), le JSON canonique, la conversion DER vers `r || s` et une vraie signature P-256, l'algorithme de statut (présent, retard, départ anticipé, partiel, absent, pauses, fenêtre finale partielle, priorité des décisions) et une vérification de reçu complète avec sources simulées.
