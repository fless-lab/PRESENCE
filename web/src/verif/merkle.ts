// Arbre de Merkle PRESENCE (SPEC section 9, API section 11), compatible RFC 9162.
// Feuille : SHA-256(0x00 | données). Nœud : SHA-256(0x01 | gauche | droite).

import { concat, egaux, sha256, utf8 } from './octets';

const PREFIXE_FEUILLE = new Uint8Array([0x00]);
const PREFIXE_NOEUD = new Uint8Array([0x01]);

export function hacherFeuille(donnees: Uint8Array): Promise<Uint8Array> {
  return sha256(concat(PREFIXE_FEUILLE, donnees));
}

export function hacherNoeud(gauche: Uint8Array, droite: Uint8Array): Promise<Uint8Array> {
  return sha256(concat(PREFIXE_NOEUD, gauche, droite));
}

/** Feuille d'un événement à partir de sa forme canonique (chaîne JSON). */
export function feuilleCanonique(canonique: string): Promise<Uint8Array> {
  return hacherFeuille(utf8(canonique));
}

/**
 * Vérifie une preuve d'inclusion (RFC 9162, section 2.1.3.2).
 * `feuille` est le hachage de feuille déjà calculé.
 */
export async function verifierInclusion(
  index: number,
  taille: number,
  feuille: Uint8Array,
  preuve: Uint8Array[],
  racine: Uint8Array,
): Promise<boolean> {
  if (!Number.isSafeInteger(index) || !Number.isSafeInteger(taille)) return false;
  if (index < 0 || index >= taille) return false;
  // Les décalages se font en arithmétique entière classique (pas d'opérateurs 32 bits).
  let fn = index;
  let sn = taille - 1;
  let r = feuille;
  const pair = (x: number) => x % 2 === 0;
  for (const p of preuve) {
    if (sn === 0) return false;
    if (!pair(fn) || fn === sn) {
      r = await hacherNoeud(p, r);
      if (pair(fn)) {
        while (pair(fn) && fn !== 0) {
          fn = Math.floor(fn / 2);
          sn = Math.floor(sn / 2);
        }
      }
    } else {
      r = await hacherNoeud(r, p);
    }
    fn = Math.floor(fn / 2);
    sn = Math.floor(sn / 2);
  }
  return sn === 0 && egaux(r, racine);
}
