import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { EtatSeance } from '../api/types';
import { Empreinte } from '../components/Copie';
import { EnTete } from '../components/EnTete';
import { Erreur, LignesSquelette, Vide } from '../components/Etats';
import { PuceEtat } from '../components/Puces';
import { dateHeure, duree, nomComplet } from '../lib/format';
import { ETATS } from '../lib/libelles';
import { useRessource } from '../lib/useRessource';

const FILTRES: (EtatSeance | 'TOUTES')[] = ['TOUTES', 'ACTIVE', 'PAUSE', 'CLOTUREE', 'SCELLEE'];

export function Seances() {
  const { donnees, erreur, chargement } = useRessource(api.seances, 30_000);
  const [filtre, setFiltre] = useState<EtatSeance | 'TOUTES'>('TOUTES');
  const naviguer = useNavigate();

  const lignes = useMemo(
    () =>
      (donnees ?? [])
        .filter((s) => filtre === 'TOUTES' || s.etat === filtre)
        .sort((a, b) => b.debut_ms - a.debut_ms),
    [donnees, filtre],
  );

  return (
    <>
      <EnTete titre="Séances" sousTitre={donnees ? `${donnees.length} séances` : undefined} />
      {erreur && <Erreur message={erreur} />}

      <div className="filtres" role="group" aria-label="Filtrer par état">
        {FILTRES.map((f) => (
          <button key={f} type="button" aria-pressed={filtre === f} onClick={() => setFiltre(f)}>
            {f === 'TOUTES' ? 'Toutes' : ETATS[f].libelle}
          </button>
        ))}
      </div>

      <div className="tableau-conteneur">
        <table className="tableau">
          <thead>
            <tr>
              <th>Identifiant</th>
              <th>Cours</th>
              <th>Salle</th>
              <th>Enseignant</th>
              <th>Début</th>
              <th className="num">Durée active</th>
              <th className="num">Présents</th>
              <th>État</th>
              <th>Racine</th>
            </tr>
          </thead>
          <tbody>
            {chargement && !donnees && <LignesSquelette colonnes={9} lignes={6} />}
            {lignes.map((s) => (
              <tr
                key={s.id}
                className="cliquable"
                onClick={() => naviguer(`/seances/${encodeURIComponent(s.id)}`)}
              >
                <td className="mono">{s.id}</td>
                <td title={s.cours.intitule}>{s.cours.code}</td>
                <td>{s.salle.nom}</td>
                <td>{nomComplet(s.enseignant)}</td>
                <td className="tabulaire secondaire">{dateHeure(s.debut_ms)}</td>
                <td className="num">{duree(s.duree_active_ms)}</td>
                <td className="num">
                  {s.presents}
                  <span className="tertiaire"> / {s.inscrits}</span>
                </td>
                <td>
                  <PuceEtat etat={s.etat} />
                </td>
                <td>
                  <Empreinte valeur={s.racine} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {donnees && lignes.length === 0 && (
        <Vide>{filtre === 'TOUTES' ? 'Aucune séance pour le moment.' : 'Aucune séance dans cet état.'}</Vide>
      )}
    </>
  );
}
