// Signature ECDSA P-256 / SHA-256 du serveur sur les reçus.
// Le serveur produit une signature DER ; WebCrypto attend r || s sur 64 octets.

import { depuisBase64 } from './octets';

function lireLongueur(der: Uint8Array, pos: number): [number, number] {
  const premier = der[pos];
  if (premier === undefined) throw new Error('DER tronqué');
  if (premier < 0x80) return [premier, pos + 1];
  const nb = premier & 0x7f;
  if (nb === 0 || nb > 2) throw new Error('Longueur DER non admise');
  let longueur = 0;
  for (let i = 0; i < nb; i++) {
    const o = der[pos + 1 + i];
    if (o === undefined) throw new Error('DER tronqué');
    longueur = (longueur << 8) | o;
  }
  return [longueur, pos + 1 + nb];
}

function lireEntier(der: Uint8Array, pos: number, taille: number): [Uint8Array, number] {
  if (der[pos] !== 0x02) throw new Error('Entier DER attendu');
  const [longueur, debut] = lireLongueur(der, pos + 1);
  const fin = debut + longueur;
  if (longueur === 0 || fin > der.length) throw new Error('Entier DER invalide');
  let octets = der.subarray(debut, fin);
  while (octets.length > 1 && octets[0] === 0x00) octets = octets.subarray(1);
  if (octets.length > taille) throw new Error('Entier DER trop long');
  const sortie = new Uint8Array(taille);
  sortie.set(octets, taille - octets.length);
  return [sortie, fin];
}

/** Convertit une signature ECDSA DER (SEQUENCE { r, s }) en r || s brut. */
export function derVersBrut(der: Uint8Array, taille = 32): Uint8Array {
  if (der[0] !== 0x30) throw new Error('Séquence DER attendue');
  const [longueur, debut] = lireLongueur(der, 1);
  if (debut + longueur !== der.length) throw new Error('Longueur de séquence DER incohérente');
  const [r, apresR] = lireEntier(der, debut, taille);
  const [s, apresS] = lireEntier(der, apresR, taille);
  if (apresS !== der.length) throw new Error('Octets en trop après la signature');
  const brut = new Uint8Array(taille * 2);
  brut.set(r, 0);
  brut.set(s, taille);
  return brut;
}

export async function importerClePublique(spkiBase64: string): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    'spki',
    depuisBase64(spkiBase64) as BufferSource,
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['verify'],
  );
}

export async function verifierSignature(
  cle: CryptoKey,
  signatureDerBase64: string,
  donnees: Uint8Array,
): Promise<boolean> {
  const brut = derVersBrut(depuisBase64(signatureDerBase64));
  return crypto.subtle.verify(
    { name: 'ECDSA', hash: 'SHA-256' },
    cle,
    brut as BufferSource,
    donnees as BufferSource,
  );
}
