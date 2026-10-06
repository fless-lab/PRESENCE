// Calcul déterministe du statut de présence (SPEC section 7).
// Le serveur et tout vérificateur de reçu doivent obtenir le même résultat.

export const DUREE_FENETRE_MS = 300_000;
export const RESTE_MINIMAL_MS = 120_000;

export type Statut =
  | 'PRESENT'
  | 'RETARD'
  | 'DEPART_ANTICIPE'
  | 'PARTIEL'
  | 'A_VERIFIER'
  | 'ABSENT';

export const STATUTS: readonly Statut[] = [
  'PRESENT',
  'RETARD',
  'DEPART_ANTICIPE',
  'PARTIEL',
  'A_VERIFIER',
  'ABSENT',
];

/** Événement tel qu'il figure, sous forme canonique, dans un reçu. */
export interface EvenementSeance {
  type: string;
  horodatage: number;
  contenu: Record<string, unknown>;
}

export interface Titulaire {
  matricule: string;
  appareil: string;
}

export interface Intervalle {
  debut: number;
  fin: number;
}

/** Règle qui a produit le statut, dans l'ordre de la SPEC. */
export type Regle = 'CORRECTION' | 'DECISION' | 'VALIDATION_MANUELLE' | 'FENETRES';

export interface ResultatStatut {
  statut: Statut;
  regle: Regle;
  manuel: boolean;
  intervalles: Intervalle[];
  dureeActive: number;
  total: number;
  validees: number[];
}

/** Tri stable par horodatage : l'ordre d'origine départage les égalités. */
function trier<T extends EvenementSeance>(evenements: T[]): T[] {
  return evenements
    .map((e, i) => [e, i] as const)
    .sort((a, b) => a[0].horodatage - b[0].horodatage || a[1] - b[1])
    .map(([e]) => e);
}

/**
 * Intervalles actifs : de DEBUT à la première PAUSE ou CLOTURE, puis de chaque
 * REPRISE à la PAUSE ou CLOTURE suivante. Une séance non clôturée s'arrête à `maintenant`.
 */
export function intervallesActifs(evenements: EvenementSeance[], maintenant: number): Intervalle[] {
  const intervalles: Intervalle[] = [];
  let ouvert: number | null = null;
  let demarree = false;
  let cloturee = false;
  for (const e of trier(evenements)) {
    if (cloturee) break;
    switch (e.type) {
      case 'DEBUT':
        if (!demarree) {
          demarree = true;
          ouvert = e.horodatage;
        }
        break;
      case 'REPRISE':
        if (demarree && ouvert === null) ouvert = e.horodatage;
        break;
      case 'PAUSE':
        if (ouvert !== null) {
          intervalles.push({ debut: ouvert, fin: e.horodatage });
          ouvert = null;
        }
        break;
      case 'CLOTURE':
        if (ouvert !== null) {
          intervalles.push({ debut: ouvert, fin: e.horodatage });
          ouvert = null;
        }
        cloturee = demarree;
        break;
    }
  }
  if (ouvert !== null) intervalles.push({ debut: ouvert, fin: Math.max(ouvert, maintenant) });
  return intervalles;
}

/**
 * Position sur l'axe actif, ou null si l'instant est hors des intervalles.
 * Un intervalle est pris comme [début, fin[ : un instant égal à une fin compte dans
 * l'intervalle suivant s'il commence au même instant, sinon il est ignoré.
 */
export function positionActive(t: number, intervalles: Intervalle[]): number | null {
  let cumul = 0;
  for (const iv of intervalles) {
    if (t >= iv.debut && t < iv.fin) return cumul + (t - iv.debut);
    cumul += iv.fin - iv.debut;
  }
  return null;
}

/** Nombre de fenêtres N pour une durée active A. */
export function nombreFenetres(dureeActive: number): number {
  let n = Math.floor(dureeActive / DUREE_FENETRE_MS);
  if (dureeActive % DUREE_FENETRE_MS >= RESTE_MINIMAL_MS) n += 1;
  if (dureeActive > 0 && n === 0) n = 1;
  return n;
}

/** Statut fondé sur les seules fenêtres (dernières règles de la SPEC section 7). */
export function statutFenetres(validees: number[], total: number): Statut {
  const v = validees.length;
  if (v === 0 || total === 0) return 'ABSENT';
  // Comparaisons entières : v / x >= 0,8  <=>  5v >= 4x
  if (5 * v >= 4 * total) return 'PRESENT';
  const f = Math.min(...validees);
  const l = Math.max(...validees);
  if (f >= 3 && 5 * v >= 4 * (total - f)) return 'RETARD';
  if (l <= total - 3 && 5 * v >= 4 * (l + 1)) return 'DEPART_ANTICIPE';
  return 'PARTIEL';
}

function plusRecent(evenements: EvenementSeance[]): EvenementSeance | undefined {
  const tries = trier(evenements);
  return tries[tries.length - 1];
}

/**
 * Recalcule le statut du titulaire. La règle « attesté dans une autre salle »
 * (A_VERIFIER) ne peut pas être évaluée à partir d'un reçu : elle est ignorée ici,
 * et `regle` vaut alors FENETRES.
 */
export function calculerStatut(
  evenements: EvenementSeance[],
  titulaire: Titulaire,
  maintenant: number,
): ResultatStatut {
  const intervalles = intervallesActifs(evenements, maintenant);
  const dureeActive = intervalles.reduce((s, iv) => s + (iv.fin - iv.debut), 0);
  const total = nombreFenetres(dureeActive);

  const indices = new Set<number>();
  for (const e of evenements) {
    if (e.type !== 'ATTESTATION' || e.contenu.appareil !== titulaire.appareil) continue;
    const pos = positionActive(e.horodatage, intervalles);
    if (pos === null) continue;
    const k = Math.floor(pos / DUREE_FENETRE_MS);
    if (k < total) indices.add(k);
  }
  const validees = [...indices].sort((a, b) => a - b);
  const base = { intervalles, dureeActive, total, validees };

  const duTitulaire = (type: string) =>
    evenements.filter((e) => e.type === type && e.contenu.matricule === titulaire.matricule);

  const correction = plusRecent(duTitulaire('CORRECTION'));
  if (correction) {
    return { ...base, statut: correction.contenu.statut as Statut, regle: 'CORRECTION', manuel: false };
  }
  const decision = plusRecent(duTitulaire('DECISION'));
  if (decision) {
    return { ...base, statut: decision.contenu.decision as Statut, regle: 'DECISION', manuel: false };
  }
  if (duTitulaire('VALIDATION_MANUELLE').length > 0) {
    return { ...base, statut: 'PRESENT', regle: 'VALIDATION_MANUELLE', manuel: true };
  }
  return { ...base, statut: statutFenetres(validees, total), regle: 'FENETRES', manuel: false };
}
