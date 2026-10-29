// Calculs d'affichage du planning : semaines, jours, saisie de dates, libellés des changements.

import type { ChangementPlanning, Creneau, TypeChangement } from '../api/types';
import { heure } from './format';

const jourFmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' });
const jourLongFmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
const moisFmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });

/** Minuit local du jour de `ms`. */
export function debutJour(ms: number): number {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** Ajoute des jours calendaires (le passage à l'heure d'été ne décale pas l'heure affichée). */
export function ajouterJours(ms: number, n: number): number {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n).getTime();
}

/** Lundi, minuit local, de la semaine qui contient `ms`. */
export function debutSemaine(ms: number): number {
  const jour = new Date(debutJour(ms));
  const depuisLundi = (jour.getDay() + 6) % 7;
  return ajouterJours(jour.getTime(), -depuisLundi);
}

/** « 3 nov. – 9 nov. » */
export function libelleSemaine(debut: number): string {
  return `${moisFmt.format(debut)} – ${moisFmt.format(ajouterJours(debut, 6))}`;
}

export const libelleJour = (ms: number) => {
  const t = jourLongFmt.format(ms);
  return t.charAt(0).toUpperCase() + t.slice(1);
};

export interface Jour {
  debut: number;
  creneaux: Creneau[];
}

/** Regroupe les créneaux par jour local, dans l'ordre chronologique. */
export function grouperParJour(creneaux: Creneau[]): Jour[] {
  const parJour = new Map<number, Creneau[]>();
  for (const c of [...creneaux].sort((a, b) => a.debut_ms - b.debut_ms)) {
    const j = debutJour(c.debut_ms);
    parJour.set(j, [...(parJour.get(j) ?? []), c]);
  }
  return [...parJour.entries()].map(([debut, liste]) => ({ debut, creneaux: liste }));
}

const deuxChiffres = (n: number) => String(n).padStart(2, '0');

/** Valeur d'un champ <input type="datetime-local"> : « 2026-11-03T09:00 », heure locale. */
export function versSaisie(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${deuxChiffres(d.getMonth() + 1)}-${deuxChiffres(d.getDate())}T${deuxChiffres(d.getHours())}:${deuxChiffres(d.getMinutes())}`;
}

const jourHeure = (ms: number) => `${jourFmt.format(ms)}, ${heure(ms)}`;

export const LIBELLES_CHANGEMENT: Record<TypeChangement, string> = {
  CREE: 'Ajouté',
  DEPLACE: 'Déplacé',
  ANNULE: 'Annulé',
  RETABLI: 'Rétabli',
};

/** Détail d'un changement : ancien et nouvel horaire, salle. */
export function detailChangement(ch: ChangementPlanning, nomSalle: (id: number) => string): string {
  const { avant, apres } = ch;
  if (!avant) return `${jourHeure(apres.debut_ms)}, ${nomSalle(apres.salle)}`;
  const morceaux: string[] = [];
  if (avant.debut_ms !== apres.debut_ms || avant.fin_ms !== apres.fin_ms) {
    morceaux.push(`${jourHeure(avant.debut_ms)} → ${jourHeure(apres.debut_ms)}`);
  }
  if (avant.salle !== apres.salle) morceaux.push(`${nomSalle(avant.salle)} → ${nomSalle(apres.salle)}`);
  if (avant.enseignant !== apres.enseignant) morceaux.push(`enseignant ${apres.enseignant ?? 'à définir'}`);
  if (morceaux.length === 0) morceaux.push(jourHeure(apres.debut_ms));
  return morceaux.join(' · ');
}
