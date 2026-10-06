import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Erreur } from '../components/Etats';
import { Logo } from '../components/Marque';
import { useAuth } from '../lib/auth';
import { messageErreur } from '../verif/recu';

export function Connexion() {
  const { personne, connecter } = useAuth();
  const naviguer = useNavigate();
  const lieu = useLocation();
  const etat = (lieu.state ?? {}) as { depuis?: string; expire?: boolean };
  const [matricule, setMatricule] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  if (personne) return <Navigate to={etat.depuis ?? '/'} replace />;

  async function soumettre(e: FormEvent) {
    e.preventDefault();
    setEnvoi(true);
    setErreur(null);
    try {
      await connecter(matricule.trim(), motDePasse);
      naviguer(etat.depuis ?? '/', { replace: true });
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24 }}>
      <div style={{ width: '100%', maxWidth: 340 }}>
        <div className="marque" style={{ padding: 0, marginBottom: 32 }}>
          <Logo />
          PRESENCE
        </div>
        <h1 style={{ fontSize: 22, lineHeight: '28px', fontWeight: 600, marginBottom: 4 }}>Connexion</h1>
        <p className="secondaire" style={{ marginBottom: 24 }}>
          Réservé au personnel de l'établissement.
        </p>
        {etat.expire && !erreur && (
          <div className="alerte alerte-info">Votre session a expiré. Reconnectez-vous.</div>
        )}
        {erreur && <Erreur message={erreur} />}
        <form className="formulaire-pile" onSubmit={soumettre}>
          <label className="champ">
            <span>Matricule</span>
            <input
              className="saisie"
              autoComplete="username"
              autoFocus
              required
              value={matricule}
              onChange={(e) => setMatricule(e.target.value)}
            />
          </label>
          <label className="champ">
            <span>Mot de passe</span>
            <input
              className="saisie"
              type="password"
              autoComplete="current-password"
              required
              value={motDePasse}
              onChange={(e) => setMotDePasse(e.target.value)}
            />
          </label>
          <button className="bouton bouton-primaire" type="submit" disabled={envoi} style={{ marginTop: 8 }}>
            {envoi ? 'Connexion…' : 'Se connecter'}
          </button>
        </form>
        <p className="secondaire" style={{ marginTop: 32, fontSize: 13 }}>
          Vous avez un reçu de présence ? <Link to="/verifier">Le vérifier</Link>
        </p>
      </div>
    </div>
  );
}
