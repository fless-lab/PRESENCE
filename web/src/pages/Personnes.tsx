import { Upload } from 'lucide-react';
import { useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import type { ImportResultat, PersonneAdmin } from '../api/types';
import { BoutonCopie } from '../components/Copie';
import { Dialogue } from '../components/Dialogue';
import { EnTete } from '../components/EnTete';
import { Erreur, LignesSquelette, Vide } from '../components/Etats';
import { nomComplet } from '../lib/format';
import { ATTESTATIONS, ROLES } from '../lib/libelles';
import { useRessource } from '../lib/useRessource';
import { messageErreur } from '../verif/recu';

export function Personnes() {
  const { donnees, erreur, chargement, recharger } = useRessource(api.personnes);
  const [recherche, setRecherche] = useState('');
  const [resultat, setResultat] = useState<ImportResultat | null>(null);
  const [erreurAction, setErreurAction] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const [cible, setCible] = useState<PersonneAdmin | null>(null);
  const fichierRef = useRef<HTMLInputElement>(null);

  const lignes = useMemo(() => {
    const q = recherche.trim().toLowerCase();
    return (donnees ?? [])
      .filter(
        (p) =>
          !q ||
          p.matricule.toLowerCase().includes(q) ||
          `${p.prenom} ${p.nom}`.toLowerCase().includes(q),
      )
      .sort((a, b) => a.nom.localeCompare(b.nom, 'fr') || a.prenom.localeCompare(b.prenom, 'fr'));
  }, [donnees, recherche]);

  async function importer(fichier: File) {
    setEnvoi(true);
    setErreurAction(null);
    setResultat(null);
    try {
      setResultat(await api.importer(fichier));
      await recharger();
    } catch (e) {
      setErreurAction(`Import impossible : ${messageErreur(e)}`);
    } finally {
      setEnvoi(false);
      if (fichierRef.current) fichierRef.current.value = '';
    }
  }

  async function nouveauCode(p: PersonneAdmin) {
    setErreurAction(null);
    try {
      await api.nouveauCode(p.matricule);
      setCible(null);
      await recharger();
    } catch (e) {
      setCible(null);
      setErreurAction(`Nouveau code impossible : ${messageErreur(e)}`);
    }
  }

  return (
    <>
      <EnTete
        titre="Personnes"
        sousTitre={donnees ? `${donnees.length} personnes` : undefined}
        actions={
          <>
            <input
              ref={fichierRef}
              type="file"
              accept=".csv,text/csv"
              className="visuellement-cache"
              id="fichier-import"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void importer(f);
              }}
            />
            <button
              type="button"
              className="bouton bouton-primaire"
              disabled={envoi}
              onClick={() => fichierRef.current?.click()}
            >
              <Upload size={15} />
              {envoi ? 'Import en cours…' : 'Importer un CSV'}
            </button>
          </>
        }
      />
      {erreur && <Erreur message={erreur} />}
      {erreurAction && <Erreur message={erreurAction} />}
      {resultat && <ResumeImport resultat={resultat} onFermer={() => setResultat(null)} />}

      <p className="secondaire" style={{ fontSize: 13, marginBottom: 16 }}>
        Colonnes attendues : <span className="mono">matricule,nom,prenom,role,cours_code,cours_intitule,groupe</span>
      </p>

      <div style={{ maxWidth: 320, marginBottom: 16 }}>
        <input
          className="saisie"
          type="search"
          placeholder="Rechercher un nom ou un matricule"
          aria-label="Rechercher"
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
        />
      </div>

      <div className="tableau-conteneur">
        <table className="tableau">
          <thead>
            <tr>
              <th>Matricule</th>
              <th>Nom</th>
              <th>Prénom</th>
              <th>Rôle</th>
              <th>Appareil</th>
              <th>Code d'enrôlement</th>
              <th>
                <span className="visuellement-cache">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {chargement && !donnees && <LignesSquelette colonnes={7} lignes={8} />}
            {lignes.map((p) => (
              <tr key={p.matricule}>
                <td className="tabulaire">{p.matricule}</td>
                <td>{p.nom}</td>
                <td>{p.prenom}</td>
                <td className="secondaire">{ROLES[p.role] ?? p.role}</td>
                <td>
                  <Appareil p={p} />
                </td>
                <td>
                  {p.code_en_attente ? (
                    <span className="empreinte" style={{ color: 'var(--texte)' }}>
                      <span className="mono">{p.code_en_attente}</span>
                      <BoutonCopie texte={p.code_en_attente} libelle="Copier le code" />
                    </span>
                  ) : (
                    <span className="tertiaire">–</span>
                  )}
                </td>
                <td style={{ textAlign: 'right' }}>
                  {p.role !== 'SCOLARITE' && p.role !== 'ADMIN' && p.role !== 'AUDITEUR' && (
                    <button type="button" className="bouton bouton-petit" onClick={() => setCible(p)}>
                      Nouveau code
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {donnees && lignes.length === 0 && (
        <Vide>{recherche ? 'Aucune personne ne correspond.' : 'Aucune personne. Importez un fichier CSV pour commencer.'}</Vide>
      )}

      <Dialogue
        ouvert={cible !== null}
        titre="Nouveau code d'enrôlement"
        onFermer={() => setCible(null)}
        pied={
          <>
            <button type="button" className="bouton" onClick={() => setCible(null)}>
              Annuler
            </button>
            <button
              type="button"
              className="bouton bouton-primaire"
              onClick={() => cible && void nouveauCode(cible)}
            >
              Générer le code
            </button>
          </>
        }
      >
        {cible && (
          <p>
            Un nouveau code sera créé pour {nomComplet(cible)} ({cible.matricule}).
            {cible.appareil?.actif
              ? " L'appareil actuel sera révoqué : la personne devra enrôler son téléphone à nouveau."
              : ''}
          </p>
        )}
      </Dialogue>
    </>
  );
}

function Appareil({ p }: { p: PersonneAdmin }) {
  const a = p.appareil;
  if (!a) return <span className="tertiaire">Aucun</span>;
  return (
    <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
      <span title={a.id}>{a.modele ?? 'Modèle inconnu'}</span>
      {!a.actif && <span className="drapeau">Révoqué</span>}
      <span
        className="tertiaire"
        style={{ fontSize: 12.5, color: a.attestation === 'REFUSEE' ? 'var(--absent)' : undefined }}
      >
        {ATTESTATIONS[a.attestation] ?? a.attestation}
      </span>
    </span>
  );
}

function ResumeImport({ resultat: r, onFermer }: { resultat: ImportResultat; onFermer: () => void }) {
  return (
    <section className="section" style={{ marginBottom: 32 }}>
      <div className="section-titre">
        <h2 style={{ font: 'inherit' }}>Résultat de l'import</h2>
        <button type="button" className="lien-discret" onClick={onFermer}>
          Masquer
        </button>
      </div>
      <p style={{ marginBottom: 12 }}>
        {r.personnes_creees} personnes créées, {r.personnes_mises_a_jour} mises à jour, {r.cours_crees} cours
        créés, {r.inscriptions} inscriptions.
      </p>
      {r.erreurs.length > 0 && (
        <div className="alerte alerte-erreur" role="alert" style={{ display: 'block' }}>
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
      {r.codes.length > 0 && (
        <>
          <h3 className="section-titre" style={{ marginTop: 16 }}>
            Nouveaux codes d'enrôlement
          </h3>
          <div className="tableau-conteneur">
            <table className="tableau">
              <thead>
                <tr>
                  <th>Matricule</th>
                  <th>Nom</th>
                  <th>Code</th>
                </tr>
              </thead>
              <tbody>
                {r.codes.map((c) => (
                  <tr key={c.matricule}>
                    <td className="tabulaire">{c.matricule}</td>
                    <td>{nomComplet(c)}</td>
                    <td>
                      <span className="empreinte" style={{ color: 'var(--texte)' }}>
                        <span className="mono">{c.code}</span>
                        <BoutonCopie texte={c.code} libelle="Copier le code" />
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
