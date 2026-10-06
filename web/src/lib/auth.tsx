import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api, definirSurExpiration, session } from '../api/client';
import type { Personne } from '../api/types';

interface ContexteAuth {
  personne: Personne | null;
  connecter: (matricule: string, motDePasse: string) => Promise<void>;
  deconnecter: () => void;
}

const Contexte = createContext<ContexteAuth | null>(null);

export function FournisseurAuth({ children }: { children: ReactNode }) {
  const [personne, setPersonne] = useState<Personne | null>(() =>
    session.jeton() ? session.personne() : null,
  );
  const naviguer = useNavigate();

  const deconnecter = useCallback(() => {
    session.effacer();
    setPersonne(null);
  }, []);

  useEffect(() => {
    definirSurExpiration(() => {
      setPersonne(null);
      naviguer('/connexion', { replace: true, state: { expire: true } });
    });
  }, [naviguer]);

  const connecter = useCallback(async (matricule: string, motDePasse: string) => {
    const r = await api.connexion(matricule, motDePasse);
    session.enregistrer(r);
    setPersonne(r.personne);
  }, []);

  const valeur = useMemo(
    () => ({ personne, connecter, deconnecter }),
    [personne, connecter, deconnecter],
  );
  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>;
}

export function useAuth(): ContexteAuth {
  const c = useContext(Contexte);
  if (!c) throw new Error('useAuth hors du FournisseurAuth');
  return c;
}

export function Protege({ children }: { children: ReactNode }) {
  const { personne } = useAuth();
  const lieu = useLocation();
  if (!personne) return <Navigate to="/connexion" replace state={{ depuis: lieu.pathname }} />;
  return <>{children}</>;
}
