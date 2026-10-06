import { CalendarClock, FileCheck2, LayoutGrid, Radio, ShieldCheck, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { ROLES } from '../lib/libelles';
import { Logo } from './Marque';

const LIENS = [
  { vers: '/', libelle: 'Aperçu', icone: LayoutGrid, fin: true },
  { vers: '/seances', libelle: 'Séances', icone: CalendarClock },
  { vers: '/personnes', libelle: 'Personnes', icone: Users },
  { vers: '/salles', libelle: 'Salles et équipements', icone: Radio },
  { vers: '/audit', libelle: 'Audit', icone: ShieldCheck },
];

export function Cadre({ children }: { children?: ReactNode }) {
  const { personne, deconnecter } = useAuth();
  return (
    <div className="app">
      <aside className="barre">
        <Link to="/" className="marque">
          <Logo />
          PRESENCE
        </Link>
        <nav className="nav" aria-label="Navigation principale">
          {LIENS.map(({ vers, libelle, icone: Icone, fin }) => (
            <NavLink key={vers} to={vers} end={fin}>
              <Icone size={16} strokeWidth={1.75} />
              {libelle}
            </NavLink>
          ))}
          <div className="nav-separateur" />
          <NavLink to="/verifier">
            <FileCheck2 size={16} strokeWidth={1.75} />
            Vérifier un reçu
          </NavLink>
        </nav>
        {personne && (
          <div className="barre-pied">
            <div className="nom">
              {personne.prenom} {personne.nom}
            </div>
            <div className="tertiaire">
              {ROLES[personne.role] ?? personne.role} · {personne.matricule}
            </div>
            <button type="button" className="lien-discret" style={{ marginTop: 8 }} onClick={deconnecter}>
              Se déconnecter
            </button>
          </div>
        )}
      </aside>
      <main className="contenu">
        <div className="contenu-interieur">
          {children ?? <Outlet />}
        </div>
      </main>
    </div>
  );
}

