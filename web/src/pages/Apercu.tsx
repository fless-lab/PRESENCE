import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Equipement } from '../api/types';
import { EnTete } from '../components/EnTete';
import { Erreur, LignesSquelette, Vide } from '../components/Etats';
import { EnLigne, PuceEtat } from '../components/Puces';
import { date, dateHeure, duree, nomComplet, pourcentage, relatif } from '../lib/format';
import { useMaintenant, useRessource } from '../lib/useRessource';

export function estEnLigne(e: Equipement, maintenant: number): boolean {
  if (typeof e.en_ligne === 'boolean') return e.en_ligne;
  return e.vu_ms !== null && maintenant - e.vu_ms < 90_000;
}

export function Apercu() {
  const { donnees, erreur, chargement } = useRessource(api.apercu, 15_000);
  const maintenant = useMaintenant();
  const naviguer = useNavigate();
  const c = donnees?.chiffres;

  return (
    <>
      <EnTete titre="Aperçu" sousTitre={date(maintenant)} />
      {erreur && <Erreur message={erreur} />}

      <section className="section">
        <div className="chiffres">
          <Chiffre libelle="Séances actives" valeur={c?.seances_actives} />
          <Chiffre libelle="Séances du jour" valeur={c?.seances_du_jour} />
          <Chiffre libelle="Taux de présence" valeur={c ? pourcentage(c.taux_presence) : undefined} />
          <Chiffre
            libelle="Équipements en ligne"
            valeur={c?.equipements_en_ligne}
            suffixe={c ? ` / ${c.equipements_total}` : undefined}
          />
        </div>
      </section>

      <section className="section">
        <h2 className="section-titre">Séances en cours</h2>
        <div className="tableau-conteneur">
          <table className="tableau">
            <thead>
              <tr>
                <th>Cours</th>
                <th>Salle</th>
                <th>Enseignant</th>
                <th>État</th>
                <th className="num">Présents</th>
                <th className="num">Depuis</th>
              </tr>
            </thead>
            <tbody>
              {chargement && !donnees && <LignesSquelette colonnes={6} lignes={3} />}
              {donnees?.seances_actives.map((s) => (
                <tr key={s.id} className="cliquable" onClick={() => naviguer(`/seances/${encodeURIComponent(s.id)}`)}>
                  <td>
                    <span title={s.cours.intitule}>{s.cours.code}</span>
                    <span className="tertiaire"> · {s.cours.intitule}</span>
                  </td>
                  <td>{s.salle.nom}</td>
                  <td>{nomComplet(s.enseignant)}</td>
                  <td>
                    <PuceEtat etat={s.etat} />
                  </td>
                  <td className="num">
                    {s.presents}
                    <span className="tertiaire"> / {s.inscrits}</span>
                  </td>
                  <td className="num" title={dateHeure(s.debut_ms)}>
                    {duree(maintenant - s.debut_ms)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {donnees && donnees.seances_actives.length === 0 && <Vide>Aucune séance en cours.</Vide>}
      </section>

      <section className="section">
        <h2 className="section-titre">Équipements</h2>
        <TableEquipements equipements={donnees?.equipements} chargement={chargement && !donnees} maintenant={maintenant} />
      </section>
    </>
  );
}

function Chiffre({ libelle, valeur, suffixe }: { libelle: string; valeur?: number | string; suffixe?: string }) {
  return (
    <div className="chiffre">
      <div className="chiffre-libelle">{libelle}</div>
      <div className="chiffre-valeur">
        {valeur ?? <span className="tertiaire">–</span>}
        {suffixe && <small>{suffixe}</small>}
      </div>
    </div>
  );
}

export function TableEquipements({
  equipements,
  chargement,
  maintenant,
}: {
  equipements: Equipement[] | undefined;
  chargement: boolean;
  maintenant: number;
}) {
  return (
    <>
      <div className="tableau-conteneur">
        <table className="tableau">
          <thead>
            <tr>
              <th>Nom</th>
              <th>Salle</th>
              <th>Type</th>
              <th>État</th>
              <th className="num">Dernier contact</th>
            </tr>
          </thead>
          <tbody>
            {chargement && <LignesSquelette colonnes={5} lignes={3} />}
            {equipements?.map((e) => (
              <tr key={e.nom}>
                <td className="mono">{e.nom}</td>
                <td>{e.nom_salle ?? e.salle}</td>
                <td className="secondaire">{e.type === 'PORTE' ? 'Porte' : 'Observateur'}</td>
                <td>{e.actif ? <EnLigne oui={estEnLigne(e, maintenant)} /> : <span className="tertiaire">Désactivé</span>}</td>
                <td className="num secondaire">{relatif(e.vu_ms, maintenant)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {equipements && equipements.length === 0 && <Vide>Aucun équipement enregistré.</Vide>}
    </>
  );
}
