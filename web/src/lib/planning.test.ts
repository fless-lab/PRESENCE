import { describe, expect, it } from 'vitest';
import type { ChangementPlanning, Creneau } from '../api/types';
import {
  ajouterJours,
  debutJour,
  debutSemaine,
  detailChangement,
  grouperParJour,
  versSaisie,
} from './planning';

const local = (a: number, m: number, j: number, h = 0, min = 0) => new Date(a, m - 1, j, h, min).getTime();

const creneau = (id: number, debut: number): Creneau => ({
  id,
  ref: null,
  cours: { code: 'SECRES', intitule: 'Sécurité', groupe: '', filiere: 'SESNUM', niveau: 'INE3' },
  salle: { id: 204, nom: 'Salle 204' },
  enseignant: null,
  debut_ms: debut,
  fin_ms: debut + 2 * 3_600_000,
  etat: 'PREVU',
  origine: 'IMPORT',
  maj_ms: 0,
});

describe('semaines et jours', () => {
  it('commence la semaine le lundi, à minuit local', () => {
    const mercredi = local(2026, 11, 4, 15, 30);
    expect(debutSemaine(mercredi)).toBe(local(2026, 11, 2));
    expect(debutSemaine(local(2026, 11, 2, 0, 0))).toBe(local(2026, 11, 2));
    // le dimanche appartient à la semaine qui finit
    expect(debutSemaine(local(2026, 11, 8, 23, 59))).toBe(local(2026, 11, 2));
  });

  it('ajoute des jours calendaires, y compris à travers un changement de mois', () => {
    expect(ajouterJours(local(2026, 10, 30), 3)).toBe(local(2026, 11, 2));
    expect(debutJour(local(2026, 11, 4, 18, 5))).toBe(local(2026, 11, 4));
  });

  it('regroupe les créneaux par jour, du plus ancien au plus récent', () => {
    const jours = grouperParJour([
      creneau(3, local(2026, 11, 5, 9)),
      creneau(1, local(2026, 11, 3, 14)),
      creneau(2, local(2026, 11, 3, 9)),
    ]);
    expect(jours.map((j) => j.debut)).toEqual([local(2026, 11, 3), local(2026, 11, 5)]);
    expect(jours[0].creneaux.map((c) => c.id)).toEqual([2, 1]);
  });
});

describe('saisie de date', () => {
  it('produit la valeur attendue par un champ datetime-local', () => {
    expect(versSaisie(local(2026, 11, 3, 9, 5))).toBe('2026-11-03T09:05');
  });
});

describe('détail d\'un changement', () => {
  const nom = (id: number) => `Salle ${id}`;
  const base = { id: 1, creneau: 1, cours: { code: 'SECRES', intitule: 'Sécurité' }, motif: '', auteur: 'A001', origine: 'LOCAL' as const, horodatage: 0 };
  const valeurs = (debut: number, salle: number) => ({
    debut_ms: debut,
    fin_ms: debut + 7_200_000,
    salle,
    enseignant: 'P001',
    etat: 'PREVU' as const,
  });

  it('indique l\'ancien et le nouvel horaire', () => {
    const ch: ChangementPlanning = {
      ...base,
      type: 'DEPLACE',
      avant: valeurs(local(2026, 11, 3, 9), 204),
      apres: valeurs(local(2026, 11, 3, 13), 204),
    };
    const t = detailChangement(ch, nom);
    expect(t).toContain('09:00');
    expect(t).toContain('13:00');
    expect(t).not.toContain('Salle');
  });

  it('indique la salle quand elle change', () => {
    const ch: ChangementPlanning = {
      ...base,
      type: 'DEPLACE',
      avant: valeurs(local(2026, 11, 3, 9), 204),
      apres: valeurs(local(2026, 11, 3, 9), 103),
    };
    expect(detailChangement(ch, nom)).toBe('Salle 204 → Salle 103');
  });
});
