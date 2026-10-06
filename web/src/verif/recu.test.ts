import { describe, expect, it } from 'vitest';
import { canonique } from './canonique';
import { hacherFeuille, hacherNoeud } from './merkle';
import { utf8, versHex } from './octets';
import { verifierRecu, type Ancrage, type Recu } from './recu';

function decoupe(n: number): number {
  let k = 1;
  while (k * 2 < n) k *= 2;
  return k;
}
async function racine(f: Uint8Array[]): Promise<Uint8Array> {
  if (f.length === 1) return hacherFeuille(f[0]!);
  const k = decoupe(f.length);
  return hacherNoeud(await racine(f.slice(0, k)), await racine(f.slice(k)));
}
async function preuve(i: number, f: Uint8Array[]): Promise<Uint8Array[]> {
  if (f.length === 1) return [];
  const k = decoupe(f.length);
  return i < k
    ? [...(await preuve(i, f.slice(0, k))), await racine(f.slice(k))]
    : [...(await preuve(i - k, f.slice(k))), await racine(f.slice(0, k))];
}

const b64 = (o: Uint8Array) => btoa(String.fromCharCode(...o));
function brutVersDer(brut: Uint8Array): Uint8Array {
  const entier = (o: Uint8Array) => {
    let v = o;
    while (v.length > 1 && v[0] === 0) v = v.subarray(1);
    if ((v[0] ?? 0) & 0x80) v = new Uint8Array([0, ...v]);
    return [0x02, v.length, ...v];
  };
  const r = entier(brut.subarray(0, 32));
  const s = entier(brut.subarray(32));
  return new Uint8Array([0x30, r.length + s.length, ...r, ...s]);
}

const T0 = 1_791_278_400_000;
const MIN = 60_000;
const SEANCE = 'S-20261006-204-01';
const APPAREIL = 'a7f3c1d09e2b4f60';

async function fabriquer() {
  const evt = (type: string, t: number, auteur: string, contenu: Record<string, unknown>) => ({
    v: '0.1',
    type,
    seance: SEANCE,
    salle: 204,
    auteur,
    horodatage: t,
    contenu,
    preuves: {},
  });
  // Séance de 20 min (4 fenêtres), titulaire attesté dans chacune
  const evenements = [
    evt('DEBUT', T0, 'ens:E1', { cours: 'SECRES', enseignant: 'E1', numero: 1 }),
    ...[1, 6, 11, 16].map((m) =>
      evt('ATTESTATION', T0 + m * MIN, 'obs:204-A', { appareil: APPAREIL, compteur: m, numero_nonce: m, nonce: 'q3Zx' }),
    ),
    evt('ATTESTATION', T0 + 2 * MIN, 'obs:204-A', { appareil: 'ffffffffffffffff', compteur: 1, numero_nonce: 2, nonce: 'aa' }),
    evt('CLOTURE', T0 + 20 * MIN, 'ens:E1', { enseignant: 'E1' }),
  ].sort((a, b) => a.horodatage - b.horodatage);
  const canoniques = evenements.map((e) => canonique(e));
  const feuilles = canoniques.map(utf8);
  const r = versHex(await racine(feuilles));
  const retenus = canoniques
    .map((c, index) => ({ c, index }))
    .filter(({ c }) => !c.includes('ffffffffffffffff'));
  const sansSig = {
    v: '0.1',
    type: 'RECU',
    seance: { id: SEANCE, cours: 'SECRES', salle: 204 },
    titulaire: { matricule: '2025001', appareil: APPAREIL },
    statut: 'PRESENT' as const,
    fenetres: { validees: 4, total: 4 },
    arbre: { taille: feuilles.length, racine: r },
    evenements: await Promise.all(
      retenus.map(async ({ c, index }) => ({
        index,
        canonique: c,
        preuve: (await preuve(index, feuilles)).map(versHex),
      })),
    ),
    ancrage: { registre: 'local', transaction: 'tx-1' },
    emis_ms: T0 + 30 * MIN,
  };
  const paire = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  const sig = new Uint8Array(
    await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, paire.privateKey, utf8(canonique(sansSig))),
  );
  const recu: Recu = { ...sansSig, sig_serveur: b64(brutVersDer(sig)) };
  const cle = b64(new Uint8Array(await crypto.subtle.exportKey('spki', paire.publicKey)));
  return { recu, cle, racine: r };
}

const sources = (cle: string, ancrage: Partial<Ancrage> & { racine: string }) => ({
  lireCleServeur: async () => cle,
  lireAncrage: async (seance: string): Promise<Ancrage> => ({
    seance,
    corrections: [],
    transaction: 'tx-1',
    registre: 'local',
    ...ancrage,
  }),
});

const etats = (r: Awaited<ReturnType<typeof verifierRecu>>) =>
  Object.fromEntries(r.controles.map((c) => [c.id, c.etat]));

describe('vérification de bout en bout', () => {
  it('accepte un reçu authentique', async () => {
    const { recu, cle, racine } = await fabriquer();
    const r = await verifierRecu(recu, sources(cle, { racine }));
    expect(etats(r)).toEqual({ inclusion: 'ok', ancrage: 'ok', signature: 'ok', statut: 'ok' });
  });

  it('détecte un statut falsifié (signature et recalcul)', async () => {
    const { recu, cle, racine } = await fabriquer();
    const r = await verifierRecu({ ...recu, statut: 'ABSENT' }, sources(cle, { racine }));
    expect(etats(r)).toMatchObject({ inclusion: 'ok', signature: 'echec', statut: 'echec' });
  });

  it('détecte un événement modifié', async () => {
    const { recu, cle, racine } = await fabriquer();
    const evenements = recu.evenements.map((e, i) =>
      i === 1 ? { ...e, canonique: e.canonique.replace('"compteur":1', '"compteur":2') } : e,
    );
    const r = await verifierRecu({ ...recu, evenements }, sources(cle, { racine }));
    expect(etats(r).inclusion).toBe('echec');
  });

  it('compare à la racine de la dernière correction', async () => {
    const { recu, cle, racine } = await fabriquer();
    const corrige = await verifierRecu(
      recu,
      sources(cle, { racine: 'aa'.repeat(32), corrections: [{ racine: 'bb'.repeat(32) }, { racine }] }),
    );
    expect(etats(corrige).ancrage).toBe('ok');
    const ancien = await verifierRecu(
      recu,
      sources(cle, { racine, corrections: [{ racine: 'bb'.repeat(32) }] }),
    );
    expect(etats(ancien).ancrage).toBe('echec');
  });

  it('signale un statut « À vérifier » comme non recalculable', async () => {
    const { recu, cle, racine } = await fabriquer();
    const r = await verifierRecu({ ...recu, statut: 'A_VERIFIER' }, sources(cle, { racine }));
    expect(etats(r).statut).toBe('indetermine');
  });

  it('reste indéterminé si le registre est injoignable', async () => {
    const { recu, cle } = await fabriquer();
    const r = await verifierRecu(recu, {
      lireCleServeur: async () => cle,
      lireAncrage: async () => {
        throw new Error('réseau');
      },
    });
    expect(etats(r).ancrage).toBe('indetermine');
  });
});
