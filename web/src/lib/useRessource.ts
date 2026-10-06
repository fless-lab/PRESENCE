import { useCallback, useEffect, useRef, useState } from 'react';
import { messageErreur } from '../verif/recu';

export interface Ressource<T> {
  donnees: T | null;
  erreur: string | null;
  chargement: boolean;
  recharger: () => Promise<void>;
}

/**
 * Charge une ressource au montage et quand `charger` change.
 * Si `intervalle` est fourni, recharge périodiquement en silence.
 */
export function useRessource<T>(
  charger: () => Promise<T>,
  intervalle: number | null = null,
): Ressource<T> {
  const [donnees, setDonnees] = useState<T | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [chargement, setChargement] = useState(true);
  const actif = useRef(true);

  const recharger = useCallback(async () => {
    try {
      const d = await charger();
      if (!actif.current) return;
      setDonnees(d);
      setErreur(null);
    } catch (e) {
      if (actif.current) setErreur(messageErreur(e));
    } finally {
      if (actif.current) setChargement(false);
    }
  }, [charger]);

  useEffect(() => {
    actif.current = true;
    setChargement(true);
    void recharger();
    return () => {
      actif.current = false;
    };
  }, [recharger]);

  useEffect(() => {
    if (intervalle === null) return;
    const id = window.setInterval(() => void recharger(), intervalle);
    return () => window.clearInterval(id);
  }, [intervalle, recharger]);

  return { donnees, erreur, chargement, recharger };
}

/** Horloge qui avance à intervalle régulier, pour les durées relatives. */
export function useMaintenant(pas = 1000): number {
  const [maintenant, setMaintenant] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setMaintenant(Date.now()), pas);
    return () => window.clearInterval(id);
  }, [pas]);
  return maintenant;
}
