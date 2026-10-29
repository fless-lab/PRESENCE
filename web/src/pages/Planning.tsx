import { ChevronLeft, ChevronRight, Plus, Upload } from 'lucide-react';
import { useCallback, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Creneau, ImportPlanningResultat } from '../api/types';
import { Dialogue } from '../components/Dialogue';
import { EnTete } from '../components/EnTete';
import { Erreur, Vide } from '../components/Etats';
import { heure, nomComplet, relatif } from '../lib/format';
import {
  ajouterJours,
  debutSemaine,
  detailChangement,
  grouperParJour,
  LIBELLES_CHANGEMENT,
  libelleJour,
  libelleSemaine,
  versSaisie,
} from '../lib/planning';
import { usePromotion } from '../lib/promotion';
import { useRessource } from '../lib/useRessource';
import { messageErreur } from '../verif/recu';

type Action =
  | { type: 'ajout' }
  | { type: 'deplacement'; creneau: Creneau }
  | { type: 'annulation'; creneau: Creneau };

export function Planning() {
  const { choisie } = usePromotion();
  const [semaine, setSemaine] = useState(() => debutSemaine(Date.now()));
  const [action, setAction] = useState<Action | null>(null);
  const [resultat, setResultat] = useState<ImportPlanningResultat | null>(null);
  const [erreurAction, setErreurAction] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const fichierRef = useRef<HTMLInputElement>(null);

  const finSemaine = ajouterJours(semaine, 7);
  const chargerCreneaux = useCallback(
    () => api.planning(choisie, semaine, finSemaine),
    [choisie, semaine, finSemaine],
  );
  const chargerChangements = useCallback(() => api.changementsPlanning(choisie), [choisie]);
  const creneaux = useRessource(chargerCreneaux, 30_000);
  const changements = useRessource(chargerChangements, 30_000);
  const salles = useRessource(api.salles);

  const nomSalle = useCallback(
    (id: number) => salles.donnees?.find((s) => s.id === id)?.nom ?? `Salle ${id}`,
    [salles.donnees],
  );
  const jours = useMemo(() => grouperParJour(creneaux.donnees ?? []), [creneaux.donnees]);

  async function recharger() {
    await Promise.all([creneaux.recharger(), changements.recharger()]);
  }

  async function importer(fichier: File) {
    setEnvoi(true);
    setErreurAction(null);
    setResultat(null);
    try {
      setResultat(await api.importerPlanning(fichier));
      await recharger();
    } catch (e) {
      setErreurAction(`Import impossible : ${messageErreur(e)}`);
    } finally {
      setEnvoi(false);
      if (fichierRef.current) fichierRef.current.value = '';
    }
  }

  async function retablir(c: Creneau) {
    setErreurAction(null);
    try {
      await api.retablirCreneau(c.id);
      await recharger();
    } catch (e) {
      setErreurAction(`Rétablissement impossible : ${messageErreur(e)}`);
    }
  }

  return (
    <>
      <EnTete
        titre="Planning"
        sousTitre="Les étudiants et les enseignants concernés sont prévenus dans l'application dès qu'un cours change."
        actions={
          <>
            <input
              ref={fichierRef}
              type="file"
              accept=".csv,text/csv"
              className="visuellement-cache"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void importer(f);
              }}
            />
            <button type="button" className="bouton" disabled={envoi} onClick={() => fichierRef.current?.click()}>
              <Upload size={15} />
              {envoi ? 'Import en cours…' : 'Importer un CSV'}
            </button>
            <button type="button" className="bouton bouton-primaire" onClick={() => setAction({ type: 'ajout' })}>
              <Plus size={15} />
              Ajouter un cours
            </button>
          </>
        }
      />
      {creneaux.erreur && <Erreur message={creneaux.erreur} />}
      {erreurAction && <Erreur message={erreurAction} />}
      {resultat && <ResumeImport resultat={resultat} onFermer={() => setResultat(null)} />}

      <div className="semaine-nav">
        <button type="button" className="bouton bouton-icone" aria-label="Semaine précédente" onClick={() => setSemaine(ajouterJours(semaine, -7))}>
          <ChevronLeft size={16} />
        </button>
        <span className="semaine-libelle">{libelleSemaine(semaine)}</span>
        <button type="button" className="bouton bouton-icone" aria-label="Semaine suivante" onClick={() => setSemaine(ajouterJours(semaine, 7))}>
          <ChevronRight size={16} />
        </button>
        <button type="button" className="lien-discret" onClick={() => setSemaine(debutSemaine(Date.now()))}>
          Cette semaine
        </button>
      </div>

      {jours.map((j) => (
        <section key={j.debut} className="jour">
          <h2 className="jour-titre">{libelleJour(j.debut)}</h2>
          <div className="tableau-conteneur">
            <table className="tableau">
              <tbody>
                {j.creneaux.map((c) => (
                  <tr key={c.id} className={c.etat === 'ANNULE' ? 'annule' : undefined}>
                    <td className="tabulaire creneau-heure">
                      {heure(c.debut_ms)} – {heure(c.fin_ms)}
                    </td>
                    <td>
                      <span className="creneau-cours">{c.cours.code}</span>{' '}
                      <span className="secondaire">{c.cours.intitule}</span>
                    </td>
                    <td className="secondaire">{c.salle.nom}</td>
                    <td className="secondaire">{c.enseignant ? nomComplet(c.enseignant) : '–'}</td>
                    <td>
                      {c.etat === 'ANNULE' && (
                        <span className="puce puce-absent">
                          <span className="point" aria-hidden="true" />
                          Annulé
                        </span>
                      )}
                    </td>
                    <td className="actions-ligne">
                      {c.etat === 'ANNULE' ? (
                        <button type="button" className="bouton bouton-petit" onClick={() => void retablir(c)}>
                          Rétablir
                        </button>
                      ) : (
                        <>
                          <button type="button" className="bouton bouton-petit" onClick={() => setAction({ type: 'deplacement', creneau: c })}>
                            Déplacer
                          </button>
                          <button type="button" className="bouton bouton-petit" onClick={() => setAction({ type: 'annulation', creneau: c })}>
                            Annuler
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}
      {creneaux.donnees && jours.length === 0 && (
        <Vide>Aucun cours cette semaine. Importez le planning de l'école ou ajoutez un cours.</Vide>
      )}

      <section className="section" style={{ marginTop: 40 }}>
        <div className="section-titre">
          <h2 style={{ font: 'inherit' }}>Derniers changements</h2>
        </div>
        {(changements.donnees ?? []).filter((c) => c.type !== 'CREE' || c.origine === 'LOCAL').length === 0 ? (
          <p className="secondaire">Aucun changement pour le moment.</p>
        ) : (
          <ul className="changements">
            {(changements.donnees ?? [])
              .filter((c) => c.type !== 'CREE' || c.origine === 'LOCAL')
              .slice(0, 10)
              .map((c) => (
                <li key={c.id}>
                  <div>
                    <strong>{c.cours.code}</strong> <span className="secondaire">{LIBELLES_CHANGEMENT[c.type]}</span>
                    <span className="tertiaire"> · {detailChangement(c, nomSalle)}</span>
                    {c.motif && <div className="secondaire">{c.motif}</div>}
                  </div>
                  <span className="tertiaire">
                    {c.auteur} · {relatif(c.horodatage)}
                  </span>
                </li>
              ))}
          </ul>
        )}
      </section>

      <FormulaireCreneau
        action={action}
        salles={salles.donnees ?? []}
        onFermer={() => setAction(null)}
        onFait={async () => {
          setAction(null);
          await recharger();
        }}
      />
    </>
  );
}

function FormulaireCreneau({
  action,
  salles,
  onFermer,
  onFait,
}: {
  action: Action | null;
  salles: { id: number; nom: string }[];
  onFermer: () => void;
  onFait: () => Promise<void>;
}) {
  const { choisie } = usePromotion();
  const cours = useRessource(useCallback(() => api.cours(choisie), [choisie]));
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const c = action && action.type !== 'ajout' ? action.creneau : null;
  // Le formulaire est remonté à chaque ouverture : les valeurs initiales sont celles du créneau.
  const cle = action ? `${action.type}-${c?.id ?? 'nouveau'}` : 'ferme';

  async function envoyer(form: FormData) {
    if (!action) return;
    setEnvoi(true);
    setErreur(null);
    const texte = (k: string) => String(form.get(k) ?? '').trim();
    try {
      if (action.type === 'ajout') {
        await api.ajouterCreneau({
          cours: texte('cours'),
          salle: Number(texte('salle')),
          debut: texte('debut'),
          fin: texte('fin'),
          motif: texte('motif') || undefined,
        });
      } else if (action.type === 'deplacement') {
        await api.deplacerCreneau(action.creneau.id, {
          debut: texte('debut'),
          fin: texte('fin'),
          salle: Number(texte('salle')),
          motif: texte('motif'),
        });
      } else {
        await api.annulerCreneau(action.creneau.id, texte('motif'));
      }
      await onFait();
    } catch (e) {
      setErreur(messageErreur(e));
    } finally {
      setEnvoi(false);
    }
  }

  const titre =
    action?.type === 'ajout'
      ? 'Ajouter un cours'
      : action?.type === 'deplacement'
        ? 'Déplacer le cours'
        : 'Annuler le cours';

  return (
    <Dialogue
      ouvert={action !== null}
      titre={titre}
      onFermer={() => {
        setErreur(null);
        onFermer();
      }}
      pied={
        <>
          <button type="button" className="bouton" onClick={onFermer}>
            Fermer
          </button>
          <button type="submit" form="formulaire-creneau" className="bouton bouton-primaire" disabled={envoi}>
            {action?.type === 'annulation' ? 'Annuler le cours' : 'Enregistrer'}
          </button>
        </>
      }
    >
      <form
        key={cle}
        id="formulaire-creneau"
        className="formulaire-pile"
        onSubmit={(e) => {
          e.preventDefault();
          void envoyer(new FormData(e.currentTarget));
        }}
      >
        {erreur && <Erreur message={erreur} />}
        {c && (
          <p>
            <strong>{c.cours.code}</strong> <span className="secondaire">{c.cours.intitule}</span>
          </p>
        )}
        {action?.type === 'ajout' && (
          <label className="champ">
            <span>Cours</span>
            <select className="saisie" name="cours" required defaultValue="">
              <option value="" disabled>
                Choisir un cours
              </option>
              {(cours.donnees ?? []).map((k) => (
                <option key={k.code} value={k.code}>
                  {k.code} · {k.intitule}
                </option>
              ))}
            </select>
          </label>
        )}
        {action?.type !== 'annulation' && (
          <>
            <div className="formulaire-ligne">
              <label className="champ">
                <span>Début</span>
                <input className="saisie" type="datetime-local" name="debut" required defaultValue={c ? versSaisie(c.debut_ms) : ''} />
              </label>
              <label className="champ">
                <span>Fin</span>
                <input className="saisie" type="datetime-local" name="fin" required defaultValue={c ? versSaisie(c.fin_ms) : ''} />
              </label>
            </div>
            <label className="champ">
              <span>Salle</span>
              <select className="saisie" name="salle" required defaultValue={c?.salle.id ?? salles[0]?.id}>
                {salles.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.nom}
                  </option>
                ))}
              </select>
            </label>
          </>
        )}
        <label className="champ">
          <span>{action?.type === 'ajout' ? 'Motif (facultatif)' : 'Motif, communiqué aux étudiants'}</span>
          <textarea className="saisie" name="motif" required={action?.type !== 'ajout'} placeholder="Ex. enseignant en déplacement" />
        </label>
      </form>
    </Dialogue>
  );
}

function ResumeImport({ resultat: r, onFermer }: { resultat: ImportPlanningResultat; onFermer: () => void }) {
  return (
    <section className="section" style={{ marginBottom: 24 }}>
      <div className="section-titre">
        <h2 style={{ font: 'inherit' }}>Résultat de l'import</h2>
        <button type="button" className="lien-discret" onClick={onFermer}>
          Masquer
        </button>
      </div>
      <p>
        {r.crees} cours ajoutés, {r.modifies} modifiés, {r.inchanges} inchangés.
      </p>
      {r.ecarts.length > 0 && (
        <div className="alerte alerte-info" style={{ display: 'block', marginTop: 12 }}>
          {r.ecarts.length} cours modifiés ici ont aussi changé dans le fichier. Vos modifications sont conservées :{' '}
          {r.ecarts.map((e) => e.ref).join(', ')}.
        </div>
      )}
      {r.non_retrouves.length > 0 && (
        <div className="alerte alerte-info" style={{ display: 'block', marginTop: 12 }}>
          Absents du fichier, laissés tels quels : {r.non_retrouves.join(', ')}.
        </div>
      )}
      {r.erreurs.length > 0 && (
        <div className="alerte alerte-erreur" role="alert" style={{ display: 'block', marginTop: 12 }}>
          {r.erreurs.length} ligne{r.erreurs.length > 1 ? 's' : ''} ignorée{r.erreurs.length > 1 ? 's' : ''} :
          <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
            {r.erreurs.slice(0, 20).map((e) => (
              <li key={e.ligne}>
                ligne {e.ligne} : {e.message}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
