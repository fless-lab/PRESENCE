import { describe, expect, it } from 'vitest';
import { canonique } from './canonique';
import { utf8, versHex } from './octets';
import { derVersBrut, importerClePublique, verifierSignature } from './signature';

function encoderEntier(octets: Uint8Array): Uint8Array {
  let v = octets;
  while (v.length > 1 && v[0] === 0) v = v.subarray(1);
  const avecZero = (v[0] ?? 0) & 0x80 ? new Uint8Array([0, ...v]) : v;
  return new Uint8Array([0x02, avecZero.length, ...avecZero]);
}

function brutVersDer(brut: Uint8Array): Uint8Array {
  const r = encoderEntier(brut.subarray(0, 32));
  const s = encoderEntier(brut.subarray(32));
  return new Uint8Array([0x30, r.length + s.length, ...r, ...s]);
}

const base64 = (o: Uint8Array) => btoa(String.fromCharCode(...o));

describe('DER vers r || s', () => {
  it('retire le zéro de tête et complète à 32 octets', () => {
    const r = new Uint8Array(32).fill(0xaa); // bit de poids fort : 0x00 ajouté en DER
    const s = new Uint8Array(32);
    s[31] = 0x05; // entier court : complété à gauche
    const der = new Uint8Array([
      0x30, 0x26,
      0x02, 0x21, 0x00, ...r,
      0x02, 0x01, 0x05,
    ]);
    const brut = derVersBrut(der);
    expect(brut.length).toBe(64);
    expect(versHex(brut.subarray(0, 32))).toBe('aa'.repeat(32));
    expect(versHex(brut.subarray(32))).toBe('00'.repeat(31) + '05');
  });

  it('refuse un DER mal formé', () => {
    expect(() => derVersBrut(new Uint8Array([0x31, 0x00]))).toThrow();
    expect(() => derVersBrut(new Uint8Array([0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01]))).toThrow();
    expect(() =>
      derVersBrut(new Uint8Array([0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01, 0xff])),
    ).toThrow();
  });

  it('vérifie une vraie signature ECDSA P-256 encodée en DER', async () => {
    const paire = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, [
      'sign',
      'verify',
    ]);
    const spki = new Uint8Array(await crypto.subtle.exportKey('spki', paire.publicKey));
    const message = utf8(canonique({ type: 'RECU', statut: 'PRESENT', nom: 'Aïcha' }));
    for (let essai = 0; essai < 10; essai++) {
      const brut = new Uint8Array(
        await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, paire.privateKey, message),
      );
      const der = brutVersDer(brut);
      expect(versHex(derVersBrut(der))).toBe(versHex(brut));
      const cle = await importerClePublique(base64(spki));
      expect(await verifierSignature(cle, base64(der), message)).toBe(true);
      expect(await verifierSignature(cle, base64(der), utf8('autre'))).toBe(false);
    }
  });
});
