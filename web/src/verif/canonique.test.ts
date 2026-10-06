import { describe, expect, it } from 'vitest';
import { canonique } from './canonique';

describe('JSON canonique', () => {
  it('trie les clés récursivement, sans espace', () => {
    const v = {
      z: 1,
      a: { y: [3, { d: true, c: null }], b: 'x' },
      m: [],
      k: {},
    };
    expect(canonique(v)).toBe('{"a":{"b":"x","y":[3,{"c":null,"d":true}]},"k":{},"m":[],"z":1}');
  });

  it('garde les caractères non ASCII tels quels (ensure_ascii=False)', () => {
    expect(canonique({ motif: 'Justificatif médical, salle 204 – B', nom: 'Zoé' })).toBe(
      '{"motif":"Justificatif médical, salle 204 – B","nom":"Zoé"}',
    );
  });

  it('échappe comme Python : guillemets, barre inverse, contrôles en minuscules', () => {
    expect(canonique('a"b\\c\n\t\u0001\u001f')).toBe('"a\\"b\\\\c\\n\\t\\u0001\\u001f"');
    // Python n'échappe pas la barre oblique ni DEL
    expect(canonique('/\u007f')).toBe('"/\u007f"');
  });

  it('trie par point de code, comme Python', () => {
    // U+1F600 (hors BMP) doit venir après U+FF61, contrairement au tri UTF-16 de JavaScript
    const v = { '\u{1F600}': 1, '｡': 2, B: 3, a: 4, é: 5 };
    expect(canonique(v)).toBe('{"B":3,"a":4,"é":5,"｡":2,"\u{1F600}":1}');
  });

  it('refuse les nombres non entiers', () => {
    expect(() => canonique({ x: 1.5 })).toThrow();
    expect(() => canonique(Number.NaN)).toThrow();
  });

  it('accepte les grands entiers sûrs (horodatages en ms)', () => {
    expect(canonique({ horodatage: 1791278372120, n: -3 })).toBe(
      '{"horodatage":1791278372120,"n":-3}',
    );
  });
});
