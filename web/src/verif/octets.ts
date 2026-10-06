// Conversions entre hexadécimal, base64 et octets, et SHA-256 via WebCrypto.

export function versHex(octets: Uint8Array): string {
  let s = '';
  for (const o of octets) s += o.toString(16).padStart(2, '0');
  return s;
}

export function depuisHex(hex: string): Uint8Array {
  if (hex.length % 2 !== 0 || !/^[0-9a-fA-F]*$/.test(hex)) {
    throw new Error('Hexadécimal invalide');
  }
  const sortie = new Uint8Array(hex.length / 2);
  for (let i = 0; i < sortie.length; i++) {
    sortie[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return sortie;
}

export function depuisBase64(b64: string): Uint8Array {
  const binaire = atob(b64.replace(/\s+/g, ''));
  const sortie = new Uint8Array(binaire.length);
  for (let i = 0; i < binaire.length; i++) sortie[i] = binaire.charCodeAt(i);
  return sortie;
}

export function concat(...parties: Uint8Array[]): Uint8Array {
  const total = parties.reduce((n, p) => n + p.length, 0);
  const sortie = new Uint8Array(total);
  let pos = 0;
  for (const p of parties) {
    sortie.set(p, pos);
    pos += p.length;
  }
  return sortie;
}

export function egaux(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= (a[i] ?? 0) ^ (b[i] ?? 0);
  return diff === 0;
}

export async function sha256(donnees: Uint8Array): Promise<Uint8Array> {
  const empreinte = await crypto.subtle.digest('SHA-256', donnees as BufferSource);
  return new Uint8Array(empreinte);
}

export const utf8 = (texte: string): Uint8Array => new TextEncoder().encode(texte);
