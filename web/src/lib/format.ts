// Formats d'affichage en français.

const heureFmt = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' });
const heureSecFmt = new Intl.DateTimeFormat('fr-FR', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});
const dateFmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
const jourFmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });

export const heure = (ms: number) => heureFmt.format(ms);
export const heureSecondes = (ms: number) => heureSecFmt.format(ms);
export const date = (ms: number) => dateFmt.format(ms);

/** « 14:05 » si aujourd'hui, sinon « 3 oct., 14:05 ». */
export function dateHeure(ms: number, maintenant = Date.now()): string {
  const a = new Date(ms);
  const b = new Date(maintenant);
  const memeJour =
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate();
  return memeJour ? heure(ms) : `${jourFmt.format(ms)}, ${heure(ms)}`;
}

/** « 42 min », « 1 h 05 », « 35 s ». */
export function duree(ms: number): string {
  const s = Math.max(0, Math.round(ms / 1000));
  if (s < 60) return `${s} s`;
  const min = Math.floor(s / 60);
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  return `${h} h ${String(min % 60).padStart(2, '0')}`;
}

/** « il y a 12 s », « il y a 3 min », « il y a 2 h », puis la date. */
export function relatif(ms: number | null, maintenant = Date.now()): string {
  if (ms === null) return 'jamais';
  const s = Math.max(0, Math.round((maintenant - ms) / 1000));
  if (s < 60) return `il y a ${s} s`;
  const min = Math.floor(s / 60);
  if (min < 60) return `il y a ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `il y a ${h} h`;
  return `le ${date(ms)}`;
}

export function pourcentage(fraction: number | null): string {
  if (fraction === null || Number.isNaN(fraction)) return '–';
  return `${Math.round(fraction * 100)} %`;
}

export const hachageCourt = (h: string | null | undefined, n = 10) =>
  h ? h.slice(0, n) : '–';

export const nomComplet = (p: { prenom: string; nom: string }) => `${p.prenom} ${p.nom}`;
