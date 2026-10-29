import { CalendarClock, CalendarDays, FileCheck2, LayoutGrid, Radio, ShieldCheck, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { ROLES } from '../lib/libelles';
import { libellePromotion, usePromotion } from '../lib/promotion';
import { Logo } from './Marque';

const LIENS = [
  { vers: '/', libelle: 'Aperçu', icone: LayoutGrid, fin: true },
  { vers: '/planning', libelle: 'Planning', icone: CalendarDays },
  { vers: '/seances', libelle: 'Séances', icone: CalendarClock },
  { vers: '/personnes', libelle: 'Personnes', icone: Users },
  { vers: '/salles', libelle: 'Salles et équipements', icone: Radio },
  { vers: '/audit', libelle: 'Audit', icone: ShieldCheck },
];

function SelecteurPromotion() {
  const { promotions, choisie, choisir } = usePromotion();
  if (promotions.length === 0) return null;
  const valeur = choisie ? promotions.findIndex((p) => p.filiere === choisie.filiere && p.niveau === choisie.niveau) : -1;
  return (
    <label className="champ promotion">
      <span>Promotion</span>
      <select
        className="saisie"
        value={valeur}
        onChange={(e) => {
          const i = Number(e.target.value);
          choisir(i >= 0 ? { filiere: promotions[i].filiere, niveau: promotions[i].niveau } : null);
        }}
      >
        <option value={-1}>Toutes</option>
        {promotions.map((p, i) => (
          <option key={`${p.filiere}-${p.niveau}`} value={i}>
            {libellePromotion(p)}
          </option>
        ))}
      </select>
    </label>
  );
}

export function Cadre({ children }: { children?: ReactNode }) {
  const { personne, deconnecter } = useAuth();
  return (
    <div className="app">
      <aside className="barre">
        <Link to="/" className="marque">
          <Logo />
          PRESENCE
        </Link>
        <SelecteurPromotion />
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

