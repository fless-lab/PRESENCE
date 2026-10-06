import { describe, expect, it } from 'vitest';
import {
  calculerStatut,
  intervallesActifs,
  nombreFenetres,
  type EvenementSeance,
} from './statut';

const T0 = 1_791_278_400_000;
const MIN = 60_000;
const W = 5 * MIN;
const APPAREIL = 'a7f3c1d09e2b4f60';
const titulaire = { matricule: '2025001', appareil: APPAREIL };

const ev = (type: string, t: number, contenu: Record<string, unknown> = {}): EvenementSeance => ({
  type,
  horodatage: t,
  contenu,
});
const debut = (t = T0) => ev('DEBUT', t, { cours: 'SECRES', enseignant: 'E1', numero: 1 });
const cloture = (t: number) => ev('CLOTURE', t, { enseignant: 'E1' });
const attest = (t: number, appareil = APPAREIL) =>
  ev('ATTESTATION', t, { appareil, compteur: 1, numero_nonce: 1, nonce: 'x' });

/** Séance d'une heure (12 fenêtres) avec une attestation au milieu des fenêtres données. */
function seanceUneHeure(fenetres: number[]): EvenementSeance[] {
  return [debut(), ...fenetres.map((k) => attest(T0 + k * W + W / 2)), cloture(T0 + 60 * MIN)];
}

const statut = (evs: EvenementSeance[], maintenant = T0 + 10 * 60 * MIN) =>
  calculerStatut(evs, titulaire, maintenant);

describe('nombre de fenêtres', () => {
  it('applique la règle de la fenêtre finale partielle (reste >= 120 s)', () => {
    expect(nombreFenetres(0)).toBe(0);
    expect(nombreFenetres(60_000)).toBe(1); // A > 0 et N = 0 : N = 1
    expect(nombreFenetres(2 * W)).toBe(2);
    expect(nombreFenetres(2 * W + 119_999)).toBe(2);
    expect(nombreFenetres(2 * W + 120_000)).toBe(3);
    expect(nombreFenetres(60 * MIN)).toBe(12);
  });

  it("n'utilise pas une attestation tombée dans un reste trop court", () => {
    const evs = [debut(), attest(T0 + 2 * W + 30_000), cloture(T0 + 2 * W + 60_000)];
    const r = statut(evs);
    expect(r.total).toBe(2);
    expect(r.validees).toEqual([]);
    expect(r.statut).toBe('ABSENT');
  });

  it('compte une attestation dans une fenêtre finale partielle retenue', () => {
    const evs = [
      debut(),
      attest(T0 + W / 2),
      attest(T0 + W + W / 2),
      attest(T0 + 2 * W + 60_000),
      cloture(T0 + 2 * W + 150_000),
    ];
    const r = statut(evs);
    expect(r.total).toBe(3);
    expect(r.validees).toEqual([0, 1, 2]);
    expect(r.statut).toBe('PRESENT');
  });
});

describe('statut', () => {
  it('présent : au moins 80 % des fenêtres', () => {
    const r = statut(seanceUneHeure([0, 1, 2, 3, 4, 5, 6, 7, 9, 11]));
    expect(r.total).toBe(12);
    expect(r.validees.length).toBe(10);
    expect(r.statut).toBe('PRESENT');
    expect(r.regle).toBe('FENETRES');
  });

  it('retard : première fenêtre >= 3 puis assiduité', () => {
    expect(statut(seanceUneHeure([3, 4, 5, 6, 7, 8, 9, 10, 11])).statut).toBe('RETARD');
  });

  it('départ anticipé : dernière fenêtre <= N - 3 et assiduité avant', () => {
    expect(statut(seanceUneHeure([0, 1, 2, 3, 4, 5, 6, 7, 8])).statut).toBe('DEPART_ANTICIPE');
  });

  it('partiel : présence dispersée', () => {
    expect(statut(seanceUneHeure([0, 2, 4, 6, 8, 10])).statut).toBe('PARTIEL');
  });

  it('absent : aucune fenêtre validée', () => {
    expect(statut(seanceUneHeure([])).statut).toBe('ABSENT');
  });

  it("ignore les attestations d'un autre appareil", () => {
    const evs = [debut(), attest(T0 + W / 2, 'ffffffffffffffff'), cloture(T0 + 10 * MIN)];
    expect(statut(evs).statut).toBe('ABSENT');
  });

  it('plusieurs attestations dans la même fenêtre ne comptent qu’une fois', () => {
    const evs = [debut(), attest(T0 + 10_000), attest(T0 + 20_000), cloture(T0 + 2 * W)];
    const r = statut(evs);
    expect(r.validees).toEqual([0]);
    expect(r.statut).toBe('PARTIEL');
  });
});

describe('pauses', () => {
  // Actif de 0 à 10 min, pause de 10 à 40 min, actif de 40 à 60 min : 30 min, 6 fenêtres
  const pilotage = [
    debut(),
    ev('PAUSE', T0 + 10 * MIN, { enseignant: 'E1' }),
    ev('REPRISE', T0 + 40 * MIN, { enseignant: 'E1' }),
    cloture(T0 + 60 * MIN),
  ];

  it('exclut les pauses de la durée active', () => {
    const iv = intervallesActifs(pilotage, T0 + 120 * MIN);
    expect(iv).toEqual([
      { debut: T0, fin: T0 + 10 * MIN },
      { debut: T0 + 40 * MIN, fin: T0 + 60 * MIN },
    ]);
    expect(statut(pilotage).total).toBe(6);
  });

  it('ignore les attestations pendant la pause', () => {
    const r = statut([...pilotage, attest(T0 + 15 * MIN), attest(T0 + 30 * MIN)]);
    expect(r.validees).toEqual([]);
    expect(r.statut).toBe('ABSENT');
  });

  it("place les attestations sur l'axe actif mis bout à bout", () => {
    // 41 min réelles = 11 min actives = fenêtre 2 ; 59 min réelles = 29 min actives = fenêtre 5
    const r = statut([...pilotage, attest(T0 + 41 * MIN), attest(T0 + 59 * MIN)]);
    expect(r.validees).toEqual([2, 5]);
  });

  it("présent sur l'axe actif malgré la pause", () => {
    // Fenêtres actives : 0 et 1 avant la pause, 2 à 5 après la reprise
    const evs = [1, 6, 41, 46, 51].map((m) => attest(T0 + m * MIN));
    const r = statut([...pilotage, ...evs]);
    expect(r.validees).toEqual([0, 1, 2, 3, 4]);
    expect(r.statut).toBe('PRESENT');
  });

  it("arrête une séance non clôturée à l'instant présent", () => {
    const evs = [debut(), attest(T0 + 1000)];
    const r = calculerStatut(evs, titulaire, T0 + 3 * MIN);
    expect(r.dureeActive).toBe(3 * MIN);
    expect(r.total).toBe(1);
    expect(r.statut).toBe('PRESENT');
  });
});

describe('priorité des décisions humaines', () => {
  const base = seanceUneHeure([]);
  const pour = (type: string, t: number, contenu: Record<string, unknown>) =>
    ev(type, t, { matricule: '2025001', motif: 'm', ...contenu });

  it('validation manuelle : présent, marqué manuel', () => {
    const r = statut([...base, pour('VALIDATION_MANUELLE', T0 + 5 * MIN, {})]);
    expect(r.statut).toBe('PRESENT');
    expect(r.manuel).toBe(true);
    expect(r.regle).toBe('VALIDATION_MANUELLE');
  });

  it('la décision la plus récente l’emporte sur la validation', () => {
    const r = statut([
      ...base,
      pour('VALIDATION_MANUELLE', T0 + 5 * MIN, {}),
      pour('DECISION', T0 + 6 * MIN, { decision: 'PRESENT' }),
      pour('DECISION', T0 + 7 * MIN, { decision: 'ABSENT' }),
    ]);
    expect(r.statut).toBe('ABSENT');
    expect(r.regle).toBe('DECISION');
  });

  it('la correction la plus récente l’emporte sur tout', () => {
    const r = statut([
      ...base,
      pour('DECISION', T0 + 7 * MIN, { decision: 'ABSENT' }),
      pour('CORRECTION', T0 + 90 * MIN, { statut: 'PARTIEL' }),
      pour('CORRECTION', T0 + 80 * MIN, { statut: 'RETARD' }),
    ]);
    expect(r.statut).toBe('PARTIEL');
    expect(r.regle).toBe('CORRECTION');
  });

  it("ignore les décisions concernant quelqu'un d'autre", () => {
    const r = statut([...base, ev('DECISION', T0, { matricule: '2025999', decision: 'PRESENT' })]);
    expect(r.statut).toBe('ABSENT');
  });
});
