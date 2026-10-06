import { describe, expect, it } from 'vitest';
import { hacherFeuille, hacherNoeud, verifierInclusion } from './merkle';
import { depuisHex, utf8, versHex } from './octets';

// Construction de référence, écrite indépendamment du code testé (RFC 9162, section 2.1.1).
function decoupe(n: number): number {
  let k = 1;
  while (k * 2 < n) k *= 2;
  return k;
}

async function racine(feuilles: Uint8Array[]): Promise<Uint8Array> {
  if (feuilles.length === 1) return hacherFeuille(feuilles[0]!);
  const k = decoupe(feuilles.length);
  return hacherNoeud(await racine(feuilles.slice(0, k)), await racine(feuilles.slice(k)));
}

async function preuve(index: number, feuilles: Uint8Array[]): Promise<Uint8Array[]> {
  if (feuilles.length === 1) return [];
  const k = decoupe(feuilles.length);
  if (index < k) {
    return [...(await preuve(index, feuilles.slice(0, k))), await racine(feuilles.slice(k))];
  }
  return [...(await preuve(index - k, feuilles.slice(k))), await racine(feuilles.slice(0, k))];
}

const donnees = (n: number) =>
  Array.from({ length: n }, (_, i) => utf8(`{"i":${i},"type":"ATTESTATION"}`));

describe('Merkle', () => {
  it('hache une feuille avec le préfixe 0x00 (vecteur connu)', async () => {
    // SHA-256(0x00) : feuille de la chaîne vide, valeur du RFC 6962
    expect(versHex(await hacherFeuille(new Uint8Array()))).toBe(
      '6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d',
    );
  });

  it('vérifie toutes les preuves pour 1 à 20 feuilles', async () => {
    for (let n = 1; n <= 20; n++) {
      const feuilles = donnees(n);
      const r = await racine(feuilles);
      for (let i = 0; i < n; i++) {
        const p = await preuve(i, feuilles);
        const ok = await verifierInclusion(i, n, await hacherFeuille(feuilles[i]!), p, r);
        expect(ok, `n=${n} i=${i}`).toBe(true);
      }
    }
  });

  it('rejette une feuille modifiée', async () => {
    for (let n = 1; n <= 20; n++) {
      const feuilles = donnees(n);
      const r = await racine(feuilles);
      for (let i = 0; i < n; i++) {
        const p = await preuve(i, feuilles);
        const alteree = await hacherFeuille(utf8(`{"i":${i},"type":"ABSENT"}`));
        expect(await verifierInclusion(i, n, alteree, p, r)).toBe(false);
      }
    }
  });

  it('rejette un mauvais index, une mauvaise taille ou une preuve tronquée', async () => {
    const feuilles = donnees(13);
    const r = await racine(feuilles);
    const f5 = await hacherFeuille(feuilles[5]!);
    const p5 = await preuve(5, feuilles);
    expect(await verifierInclusion(5, 13, f5, p5, r)).toBe(true);
    expect(await verifierInclusion(4, 13, f5, p5, r)).toBe(false);
    expect(await verifierInclusion(5, 32, f5, p5, r)).toBe(false);
    expect(await verifierInclusion(5, 6, f5, p5, r)).toBe(false);
    expect(await verifierInclusion(5, 13, f5, p5.slice(1), r)).toBe(false);
    expect(await verifierInclusion(13, 13, f5, p5, r)).toBe(false);
    const autreRacine = depuisHex('00'.repeat(32));
    expect(await verifierInclusion(5, 13, f5, p5, autreRacine)).toBe(false);
  });
});
