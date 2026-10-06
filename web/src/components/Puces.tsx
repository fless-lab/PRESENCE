import type { EtatSeance, Statut } from '../api/types';
import { ETATS, STATUTS } from '../lib/libelles';

export function PuceStatut({ statut, nue = false }: { statut: Statut; nue?: boolean }) {
  const s = STATUTS[statut] ?? { libelle: statut, classe: 'puce-neutre' };
  return (
    <span className={`puce ${s.classe}${nue ? ' puce-nue' : ''}`}>
      <span className="point" aria-hidden="true" />
      {s.libelle}
    </span>
  );
}

export function PuceEtat({ etat }: { etat: EtatSeance }) {
  const e = ETATS[etat] ?? { libelle: etat, classe: 'puce-neutre' };
  return (
    <span className={`puce ${e.classe}`}>
      <span className="point" aria-hidden="true" />
      {e.libelle}
    </span>
  );
}

export function EnLigne({ oui, libelle }: { oui: boolean; libelle?: string }) {
  return (
    <span className="en-ligne">
      <span className={`point ${oui ? 'oui' : 'non'}`} aria-hidden="true" />
      <span>{libelle ?? (oui ? 'En ligne' : 'Hors ligne')}</span>
    </span>
  );
}
