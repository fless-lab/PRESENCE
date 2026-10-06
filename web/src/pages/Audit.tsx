import { AlertTriangle } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Audit as AuditReponse } from '../api/types';
import { Empreinte } from '../components/Copie';
import { EnTete } from '../components/EnTete';
import { Erreur, LignesSquelette, Vide } from '../components/Etats';
import { dateHeure } from '../lib/format';
import { messageErreur } from '../verif/recu';

export function Audit() {
  const [resultat, setResultat] = useState<AuditReponse | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [encours, setEncours] = useState(false);

  async function verifier() {
    setEncours(true);
    setErreur(null);
    try {
      setResultat(await api.audit());
    } catch (e) {
      setErreur(messageErreur(e));
    } finally {
      setEncours(false);
    }
  }

  const ecarts = resultat?.seances.filter((s) => !s.conforme) ?? [];

  return (
    <>
      <EnTete
        titre="Audit"
        sousTitre={
          resultat
            ? `Vérification du ${dateHeure(resultat.verifie_ms)}`
            : 'Recalcule la racine de chaque séance scellée et la compare à celle ancrée sur le registre.'
        }
        actions={
          <button type="button" className="bouton bouton-primaire" onClick={verifier} disabled={encours}>
            {encours ? 'Vérification…' : 'Vérifier toutes les séances'}
          </button>
        }
      />
      {erreur && <Erreur message={erreur} />}
      {ecarts.length > 0 && (
        <div className="alerte alerte-erreur" role="alert">
          <AlertTriangle size={16} />
          <span>
            {ecarts.length === 1
              ? "1 séance présente un écart entre la racine recalculée et la racine ancrée. Son journal ne correspond plus à ce qui a été scellé."
              : `${ecarts.length} séances présentent un écart entre la racine recalculée et la racine ancrée. Leur journal ne correspond plus à ce qui a été scellé.`}
          </span>
        </div>
      )}
      {resultat && ecarts.length === 0 && resultat.seances.length > 0 && (
        <div className="alerte alerte-succes" role="status">
          Les {resultat.seances.length} séances scellées sont conformes au registre.
        </div>
      )}

      {(encours || resultat) && (
        <div className="tableau-conteneur">
          <table className="tableau">
            <thead>
              <tr>
                <th>Séance</th>
                <th>Cours</th>
                <th>Début</th>
                <th className="num">Événements</th>
                <th>Racine recalculée</th>
                <th>Racine ancrée</th>
                <th>Résultat</th>
              </tr>
            </thead>
            <tbody>
              {encours && !resultat && <LignesSquelette colonnes={7} lignes={5} />}
              {resultat?.seances.map((s) => (
                <tr key={s.seance}>
                  <td className="mono">
                    <Link to={`/seances/${encodeURIComponent(s.seance)}`}>{s.seance}</Link>
                  </td>
                  <td>{s.cours}</td>
                  <td className="tabulaire secondaire">{dateHeure(s.debut_ms)}</td>
                  <td className="num">{s.nb_evenements}</td>
                  <td>
                    <Empreinte valeur={s.racine_recalculee} n={12} />
                  </td>
                  <td>
                    <Empreinte valeur={s.racine_ancree} n={12} />
                  </td>
                  <td>
                    {s.conforme ? (
                      <span className="puce puce-present puce-nue">
                        <span className="point" />
                        Conforme
                      </span>
                    ) : (
                      <span className="puce puce-absent">
                        <span className="point" />
                        Écart détecté
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {resultat && resultat.seances.length === 0 && <Vide>Aucune séance scellée à vérifier.</Vide>}
      {!resultat && !encours && !erreur && (
        <Vide>Aucune vérification lancée dans cette session.</Vide>
      )}
    </>
  );
}
