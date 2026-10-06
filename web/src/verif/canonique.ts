// JSON canonique : clés triées récursivement, aucun espace.
// Sortie identique à Python json.dumps(x, sort_keys=True, separators=(',', ':'), ensure_ascii=False)
// pour les valeurs admises : entiers, chaînes, booléens, null, tableaux, objets.

export type ValeurJson =
  | null
  | boolean
  | number
  | string
  | ValeurJson[]
  | { [cle: string]: ValeurJson };

/** Comparaison par points de code, comme le tri des chaînes en Python. */
export function comparerPointsDeCode(a: string, b: string): number {
  const ia = a[Symbol.iterator]();
  const ib = b[Symbol.iterator]();
  for (;;) {
    const ca = ia.next();
    const cb = ib.next();
    if (ca.done && cb.done) return 0;
    if (ca.done) return -1;
    if (cb.done) return 1;
    const pa = ca.value.codePointAt(0) ?? 0;
    const pb = cb.value.codePointAt(0) ?? 0;
    if (pa !== pb) return pa < pb ? -1 : 1;
  }
}

export function canonique(valeur: unknown): string {
  if (valeur === null) return 'null';
  switch (typeof valeur) {
    case 'boolean':
      return valeur ? 'true' : 'false';
    case 'number':
      if (!Number.isSafeInteger(valeur)) {
        throw new Error(`Nombre non admis dans le JSON canonique : ${valeur}`);
      }
      return String(valeur);
    case 'string':
      // JSON.stringify échappe les mêmes caractères que Python avec ensure_ascii=False :
      // guillemet, barre oblique inverse et caractères de contrôle (\u00xx en minuscules).
      return JSON.stringify(valeur);
    case 'object': {
      if (Array.isArray(valeur)) {
        return '[' + valeur.map(canonique).join(',') + ']';
      }
      const objet = valeur as Record<string, unknown>;
      const cles = Object.keys(objet).sort(comparerPointsDeCode);
      return (
        '{' + cles.map((c) => JSON.stringify(c) + ':' + canonique(objet[c])).join(',') + '}'
      );
    }
    default:
      throw new Error(`Type non admis dans le JSON canonique : ${typeof valeur}`);
  }
}
