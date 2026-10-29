import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { FiliereNiveau, Promotion } from '../api/types';
import { useAuth } from './auth';

interface ContextePromotion {
  /** Promotions connues (filière et niveau) avec leur effectif. */
  promotions: FiliereNiveau[];
  /** Promotion choisie, ou null pour « toutes ». */
  choisie: Promotion | null;
  choisir: (p: Promotion | null) => void;
}

const Contexte = createContext<ContextePromotion>({ promotions: [], choisie: null, choisir: () => {} });
const CLE = 'presence.promotion';

function lire(): Promotion | null {
  try {
    const brut = sessionStorage.getItem(CLE);
    return brut ? (JSON.parse(brut) as Promotion) : null;
  } catch {
    return null;
  }
}

/** Retient la promotion choisie (filière et niveau) pour toutes les pages du tableau de bord. */
export function FournisseurPromotion({ children }: { children: ReactNode }) {
  const { personne } = useAuth();
  const [promotions, setPromotions] = useState<FiliereNiveau[]>([]);
  const [choisie, setChoisie] = useState<Promotion | null>(lire);

  useEffect(() => {
    if (!personne) return;
    let actif = true;
    api
      .filieres()
      .then((l) => {
        if (!actif) return;
        const connues = l.filter((p) => p.filiere || p.niveau);
        setPromotions(connues);
        // Une seule promotion connue : on la retient d'office.
        setChoisie((c) => (c === null && connues.length === 1 ? (connues[0] ?? null) : c));
      })
      .catch(() => {});
    return () => {
      actif = false;
    };
  }, [personne]);

  const choisir = useCallback((p: Promotion | null) => {
    setChoisie(p);
    try {
      if (p) sessionStorage.setItem(CLE, JSON.stringify(p));
      else sessionStorage.removeItem(CLE);
    } catch {
      /* stockage indisponible */
    }
  }, []);

  const valeur = useMemo(() => ({ promotions, choisie, choisir }), [promotions, choisie, choisir]);
  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>;
}

export const usePromotion = () => useContext(Contexte);

export const libellePromotion = (p: Promotion) => [p.filiere, p.niveau].filter(Boolean).join(' · ');
