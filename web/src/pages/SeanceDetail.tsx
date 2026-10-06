import { ArrowLeft, Download, ListTree, PenLine } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { EtudiantSeance, EvenementJournal, SeanceDetail as Detail, Statut } from '../api/types';
import { BoutonCopie, Empreinte } from '../components/Copie';
import { Dialogue } from '../components/Dialogue';
import { EnTete } from '../components/EnTete';
import { Chargement, Erreur, Vide } from '../components/Etats';
import { PuceEtat, PuceStatut } from '../components/Puces';
import { dateHeure, duree, heure, heureSecondes, nomComplet } from '../lib/format';
import { ORDRE_STATUTS, STATUTS } from '../lib/libelles';
import { useRessource } from '../lib/useRessource';
import { messageErreur } from '../verif/recu';

export function SeanceDetail() {
  const { id = '' } = useParams();
  const charger = useCallback(() => api.seance(id), [id]);
  const [enDirect, setEnDirect] = useState(false);
  const { donnees: s, erreur, chargement, recharger } = useRessource(charger, enDirect ? 10_000 : null);
  const [journal, setJournal] = useState(false);
  const [correction, setCorrection] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [erreurExport, setErreurExport] = useState<string | null>(null);

  useEffect(() => {
    setEnDirect(s?.etat === 'ACTIVE' || s?.etat === 'PAUSE');
  }, [s?.etat]);

  async function exporter() {
    setErreurExport(null);
    try {
      const blob = await api.exporterCsv(id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `presence-${id}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setErreurExport(messageErreur(e));
    }
  }

  const retour = (
    <Link to="/seances" className="fil">
      <ArrowLeft size={14} />
      Séances
    </Link>
  );

  if (!s) {
    return (
      <>
        {retour}
        {erreur ? <Erreur message={erreur} /> : chargement && <Chargement />}
      </>
    );
  }

  return (
    <>
      <EnTete
        avant={retour}
        titre={`${s.cours.code} · ${s.cours.intitule}`}
        sousTitre={<span className="mono">{s.id}</span>}
        actions={
          <>
            <button type="button" className="bouton" onClick={() => setJournal(true)}>
              <ListTree size={15} />
              Journal des événements
            </button>
            <button type="button" className="bouton" onClick={exporter}>
              <Download size={15} />
              Export CSV
            </button>
            <button type="button" className="bouton bouton-primaire" onClick={() => setCorrection('')}>
              <PenLine size={15} />
              Correction
            </button>
          </>
        }
      />
      {erreur && <Erreur message={erreur} />}
      {erreurExport && <Erreur message={`Export impossible : ${erreurExport}`} />}
      {message && <div className="alerte alerte-succes" role="status">{message}</div>}

      <div className="entete-seance" style={{ marginTop: -12, marginBottom: 32 }}>
        <PuceEtat etat={s.etat} />
        <b>{s.salle.nom}</b>
        <b>{nomComplet(s.enseignant)}</b>
        <span className="tabulaire">
          <b>{dateHeure(s.debut_ms)}</b> à <b>{s.fin_ms ? heure(s.fin_ms) : 'maintenant'}</b>
        </span>
        <span className="tabulaire">
          <b>{duree(s.duree_active_ms)}</b> de temps actif
        </span>
      </div>

      <div className="deux-colonnes">
        <div>
          <section className="section">
            <div className="section-titre">
              <h2 style={{ font: 'inherit' }}>Déroulement</h2>
              <div className="legende">
                <span>
                  <i className="actif" /> Actif
                </span>
                <span>
                  <i /> Pause
                </span>
                <span>
                  {s.fenetres.total} fenêtres de {Math.round(s.fenetres.duree_ms / 60_000)} min
                </span>
              </div>
            </div>
            <Chronologie seance={s} />
          </section>

          <section className="section">
            <div className="section-titre">
              <h2 style={{ font: 'inherit' }}>Étudiants</h2>
              <Repartition etudiants={s.etudiants} />
            </div>
            <TableEtudiants seance={s} onCorriger={(m) => setCorrection(m)} />
          </section>
        </div>

        <aside>
          <h2 className="section-titre">Intégrité</h2>
          <PanneauIntegrite seance={s} />
        </aside>
      </div>

      <DialogueJournal id={s.id} ouvert={journal} onFermer={() => setJournal(false)} />
      <DialogueCorrection
        seance={s}
        matricule={correction}
        onFermer={() => setCorrection(null)}
        onFait={(m) => {
          setCorrection(null);
          setMessage(`Correction enregistrée pour ${m}. La racine a été ré-ancrée.`);
          void recharger();
        }}
      />
    </>
  );
}

function Chronologie({ seance: s }: { seance: Detail }) {
  const fin = s.fin_ms ?? s.maintenant_ms ?? Date.now();
  const etendue = Math.max(1, fin - s.debut_ms);
  const pct = (t: number) => `${((t - s.debut_ms) / etendue) * 100}%`;

  // Bornes des fenêtres, reportées de l'axe actif vers le temps réel.
  const graduations = useMemo(() => {
    const W = s.fenetres.duree_ms || 300_000;
    const res: number[] = [];
    let cumul = 0;
    let prochaine = W;
    for (const iv of s.intervalles) {
      const f = iv.fin_ms ?? fin;
      const longueur = f - iv.debut_ms;
      while (prochaine < cumul + longueur && res.length < s.fenetres.total - 1) {
        res.push(iv.debut_ms + (prochaine - cumul));
        prochaine += W;
      }
      cumul += longueur;
    }
    return res;
  }, [s, fin]);

  if (s.intervalles.length === 0) return <Vide>La séance n'a pas encore d'intervalle actif.</Vide>;

  return (
    <div className="chronologie">
      <div className="chronologie-bande" role="img" aria-label="Intervalles actifs et pauses de la séance">
        {s.intervalles.map((iv) => {
          const f = iv.fin_ms ?? fin;
          return (
            <div
              key={iv.debut_ms}
              className={`chronologie-actif${iv.fin_ms === null ? ' en-cours' : ''}`}
              style={{ left: pct(iv.debut_ms), width: `${((f - iv.debut_ms) / etendue) * 100}%` }}
              title={`${heure(iv.debut_ms)} à ${iv.fin_ms ? heure(iv.fin_ms) : 'maintenant'}`}
            />
          );
        })}
        {graduations.map((t) => (
          <div key={t} className="chronologie-graduation" style={{ left: pct(t) }} />
        ))}
      </div>
      <div className="chronologie-axe">
        <span>{heure(s.debut_ms)}</span>
        <span>{s.fin_ms ? heure(s.fin_ms) : 'maintenant'}</span>
      </div>
    </div>
  );
}

const PLURIELS: Record<Statut, string> = {
  PRESENT: 'présents',
  RETARD: 'retards',
  DEPART_ANTICIPE: 'départs anticipés',
  PARTIEL: 'partiels',
  A_VERIFIER: 'à vérifier',
  ABSENT: 'absents',
};

function Repartition({ etudiants }: { etudiants: EtudiantSeance[] }) {
  const compte = new Map<Statut, number>();
  for (const e of etudiants) compte.set(e.statut, (compte.get(e.statut) ?? 0) + 1);
  const parts = ORDRE_STATUTS.filter((st) => compte.get(st)).map(
    (st) => `${compte.get(st)} ${(compte.get(st) ?? 0) > 1 ? PLURIELS[st] : STATUTS[st].libelle.toLowerCase()}`,
  );
  return <span className="tertiaire" style={{ fontWeight: 400 }}>{parts.join(' · ')}</span>;
}

function TableEtudiants({ seance: s, onCorriger }: { seance: Detail; onCorriger: (m: string) => void }) {
  const lignes = useMemo(
    () => [...s.etudiants].sort((a, b) => a.nom.localeCompare(b.nom, 'fr') || a.prenom.localeCompare(b.prenom, 'fr')),
    [s.etudiants],
  );
  if (lignes.length === 0) return <Vide>Aucun inscrit pour ce cours.</Vide>;
  return (
    <div className="tableau-conteneur">
      <table className="tableau">
        <thead>
          <tr>
            <th>Étudiant</th>
            <th>Statut</th>
            <th className="num">Fenêtres</th>
            <th>Chronologie</th>
            <th>Signalements</th>
            <th>
              <span className="visuellement-cache">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {lignes.map((e) => {
            const valides = new Set(e.fenetres_validees);
            return (
              <tr key={e.matricule}>
                <td>
                  {nomComplet(e)}
                  <span className="tertiaire num"> · {e.matricule}</span>
                </td>
                <td>
                  <PuceStatut statut={e.statut} />
                </td>
                <td className="num">
                  {valides.size}
                  <span className="tertiaire"> / {s.fenetres.total}</span>
                </td>
                <td>
                  <span
                    className={`grille${s.fenetres.total > 20 ? ' dense' : ''}`}
                    aria-label={`${valides.size} fenêtres validées sur ${s.fenetres.total}`}
                  >
                    {Array.from({ length: s.fenetres.total }, (_, k) => (
                      <i key={k} className={valides.has(k) ? 'oui' : undefined} title={`Fenêtre ${k + 1}`} />
                    ))}
                  </span>
                </td>
                <td className="large">
                  <span style={{ display: 'inline-flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                    {e.manuel && <span className="drapeau">Manuel</span>}
                    {e.corrige && <span className="drapeau">Corrigé</span>}
                    {!e.appareil && <span className="drapeau">Sans appareil</span>}
                    {e.motif_a_verifier && (
                      <span style={{ color: 'var(--a-verifier)', fontSize: 13 }}>{e.motif_a_verifier}</span>
                    )}
                  </span>
                </td>
                <td style={{ textAlign: 'right' }}>
                  <button
                    type="button"
                    className="bouton bouton-icone"
                    title="Corriger le statut"
                    aria-label={`Corriger le statut de ${nomComplet(e)}`}
                    onClick={() => onCorriger(e.matricule)}
                  >
                    <PenLine size={14} />
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function PanneauIntegrite({ seance: s }: { seance: Detail }) {
  const i = s.integrite;
  return (
    <>
      <dl className="proprietes">
        <div>
          <dt>Racine de Merkle</dt>
          <dd>
            {i.racine ? (
              <span style={{ display: 'flex', gap: 4, alignItems: 'flex-start' }}>
                <span className="empreinte-complete">{i.racine}</span>
                <BoutonCopie texte={i.racine} libelle="Copier la racine" />
              </span>
            ) : (
              <span className="secondaire">
                {s.etat === 'SCELLEE' ? 'Non disponible' : 'Calculée au scellement'}
              </span>
            )}
          </dd>
        </div>
        <div>
          <dt>Événements</dt>
          <dd className="num">{i.nb_evenements}</dd>
        </div>
        <div>
          <dt>Registre</dt>
          <dd>{i.registre === 'fabric' ? 'Hyperledger Fabric' : i.registre === 'local' ? 'Local' : <span className="tertiaire">Non ancrée</span>}</dd>
        </div>
        <div>
          <dt>Transaction</dt>
          <dd>{i.transaction ? <Empreinte valeur={i.transaction} n={16} copiable /> : <span className="tertiaire">–</span>}</dd>
        </div>
        {i.corrections > 0 && (
          <div>
            <dt>Corrections ré-ancrées</dt>
            <dd className="num">{i.corrections}</dd>
          </div>
        )}
      </dl>
      <p style={{ marginTop: 16, fontSize: 13 }} className="secondaire">
        Un étudiant peut contrôler son reçu sans compte.{' '}
        <Link to="/verifier">Vérifier un reçu</Link>
      </p>
    </>
  );
}

function resumeContenu(c: Record<string, unknown>): string {
  return Object.entries(c)
    .map(([k, v]) => `${k}=${typeof v === 'string' ? v : JSON.stringify(v)}`)
    .join(' ');
}

function DialogueJournal({ id, ouvert, onFermer }: { id: string; ouvert: boolean; onFermer: () => void }) {
  const [evenements, setEvenements] = useState<EvenementJournal[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    if (!ouvert) return;
    let actif = true;
    setEvenements(null);
    setErreur(null);
    api
      .evenements(id)
      .then((e) => actif && setEvenements(e))
      .catch((e: unknown) => actif && setErreur(messageErreur(e)));
    return () => {
      actif = false;
    };
  }, [id, ouvert]);

  return (
    <Dialogue ouvert={ouvert} titre="Journal des événements" onFermer={onFermer} large>
      {erreur && <Erreur message={erreur} />}
      {!evenements && !erreur && <Chargement />}
      {evenements && evenements.length === 0 && <Vide>Aucun événement.</Vide>}
      {evenements && evenements.length > 0 && (
        <div className="journal">
          <table>
            <tbody>
              {evenements.map((e) => (
                <tr key={e.index}>
                  <td className="tertiaire" style={{ textAlign: 'right' }}>{e.index}</td>
                  <td>{heureSecondes(e.horodatage)}</td>
                  <td style={{ fontWeight: 500 }}>{e.type}</td>
                  <td className="secondaire">{e.auteur}</td>
                  <td className="tertiaire" title={e.hachage}>{e.hachage.slice(0, 12)}</td>
                  <td className="detail">{resumeContenu(e.contenu)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Dialogue>
  );
}

function DialogueCorrection({
  seance,
  matricule,
  onFermer,
  onFait,
}: {
  seance: Detail;
  matricule: string | null;
  onFermer: () => void;
  onFait: (matricule: string) => void;
}) {
  const [m, setM] = useState('');
  const [statut, setStatut] = useState<Statut>('PRESENT');
  const [motif, setMotif] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  useEffect(() => {
    if (matricule === null) return;
    setM(matricule);
    setMotif('');
    setErreur(null);
    const actuel = seance.etudiants.find((e) => e.matricule === matricule);
    setStatut(actuel?.statut === 'PRESENT' ? 'ABSENT' : 'PRESENT');
  }, [matricule, seance.etudiants]);

  async function soumettre(e: FormEvent) {
    e.preventDefault();
    setEnvoi(true);
    setErreur(null);
    try {
      await api.corriger(seance.id, { matricule: m.trim(), statut, motif: motif.trim() });
      onFait(m.trim());
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <Dialogue
      ouvert={matricule !== null}
      titre="Correction"
      onFermer={onFermer}
      pied={
        <>
          <button type="button" className="bouton" onClick={onFermer}>
            Annuler
          </button>
          <button type="submit" form="form-correction" className="bouton bouton-primaire" disabled={envoi}>
            {envoi ? 'Enregistrement…' : 'Enregistrer la correction'}
          </button>
        </>
      }
    >
      <form id="form-correction" className="formulaire-pile" onSubmit={soumettre}>
        <p className="secondaire" style={{ fontSize: 13 }}>
          La correction est ajoutée au journal, la séance est rescellée et la nouvelle racine est ancrée.
        </p>
        {erreur && <Erreur message={erreur} />}
        <label className="champ">
          <span>Matricule</span>
          <input
            className="saisie"
            list="inscrits-seance"
            required
            value={m}
            onChange={(e) => setM(e.target.value)}
          />
          <datalist id="inscrits-seance">
            {seance.etudiants.map((e) => (
              <option key={e.matricule} value={e.matricule}>
                {nomComplet(e)}
              </option>
            ))}
          </datalist>
        </label>
        <label className="champ">
          <span>Statut</span>
          <select className="saisie" value={statut} onChange={(e) => setStatut(e.target.value as Statut)}>
            {ORDRE_STATUTS.filter((st) => st !== 'A_VERIFIER').map((st) => (
              <option key={st} value={st}>
                {STATUTS[st].libelle}
              </option>
            ))}
          </select>
        </label>
        <label className="champ">
          <span>Motif</span>
          <textarea
            className="saisie"
            required
            placeholder="Justificatif médical reçu le 7 octobre"
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
          />
        </label>
      </form>
    </Dialogue>
  );
}
