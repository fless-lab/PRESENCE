import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { creerServeur, type Contrat } from '../src/http.js';

function contratMemoire(): Contrat {
  const etat = new Map<string, Record<string, unknown>>();
  let n = 0;
  return {
    async soumettre(fonction, args) {
      n += 1;
      const tx = `tx${n}`;
      if (fonction === 'AncrerSeance') {
        if (etat.has(args[0])) throw new Error(`la séance ${args[0]} est déjà ancrée`);
        etat.set(args[0], { seance: args[0], racine: args[4], corrections: [], transaction: tx });
      } else {
        const a = etat.get(args[0]);
        if (!a) throw new Error(`aucun ancrage pour la séance ${args[0]}`);
        (a.corrections as unknown[]).push({ racine: args[1], racine_precedente: args[2], motif: args[3], transaction: tx });
      }
      return tx;
    },
    async evaluer(_f, args) {
      const a = etat.get(args[0]);
      if (!a) throw new Error(`aucun ancrage pour la séance ${args[0]}`);
      return Buffer.from(JSON.stringify(a));
    },
  };
}

test('ancrage, double ancrage, correction, lecture', async () => {
  const serveur = creerServeur(contratMemoire()).listen(0);
  const { port } = serveur.address() as AddressInfo;
  const url = `http://127.0.0.1:${port}`;
  const poster = (chemin: string, corps: unknown) =>
    fetch(url + chemin, { method: 'POST', body: JSON.stringify(corps) });
  const racine = 'a'.repeat(64);
  try {
    let r = await poster('/ancrages', { seance: 'S-1', salle: 204, debut: '1', fin: '2', racine, nb_evenements: 3, observateurs: ['204-A'] });
    assert.equal(r.status, 200);
    assert.equal((await r.json()).transaction, 'tx1');
    r = await poster('/ancrages', { seance: 'S-1', racine });
    assert.equal(r.status, 409);
    r = await poster('/ancrages', { seance: 'S-2', racine: 'zz' });
    assert.equal(r.status, 422);
    r = await poster('/corrections', { seance: 'S-1', nouvelle_racine: 'b'.repeat(64), racine_precedente: racine, motif: 'justificatif' });
    assert.equal(r.status, 200);
    r = await fetch(`${url}/ancrages/S-1`);
    const a = await r.json();
    assert.equal(a.racine, racine);
    assert.equal(a.corrections.length, 1);
    r = await fetch(`${url}/ancrages/S-9`);
    assert.equal(r.status, 404);
  } finally {
    serveur.close();
  }
});
