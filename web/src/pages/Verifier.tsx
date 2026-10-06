import { Check, Minus, Upload, X } from 'lucide-react';
import { useRef, useState, type DragEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { BoutonCopie } from '../components/Copie';
import { EnTete } from '../components/EnTete';
import { Erreur } from '../components/Etats';
import { Logo } from '../components/Marque';
import { PuceStatut } from '../components/Puces';
import { useAuth } from '../lib/auth';
import { dateHeure } from '../lib/format';
import {
  lireRecu,
  messageErreur,
  verifierRecu,
  type EtatControle,
  type ResultatVerification,
  type Sources,
} from '../verif/recu';

const sources: Sources = {
  lireAncrage: (seance) => api.ancrage(seance),
  lireCleServeur: async () => (await api.cleServeur()).cle_publique,
};

const ICONES: Record<EtatControle, typeof Check> = { ok: Check, echec: X, indetermine: Minus };
const LIBELLES_ETAT: Record<EtatControle, string> = {
  ok: 'Vérifié',
  echec: 'Échec',
  indetermine: 'Non concluant',
};

export function Verifier() {
  const { personne } = useAuth();
  const contenu = <ContenuVerifier />;
  if (personne) return contenu;
  return (
    <div className="publique">
      <div className="publique-entete">
        <span className="marque" style={{ padding: 0, margin: 0 }}>
          <Logo />
          PRESENCE
        </span>
        <Link to="/connexion" className="secondaire" style={{ fontSize: 13 }}>
          Espace du personnel
        </Link>
      </div>
      {contenu}
    </div>
  );
}

function ContenuVerifier() {
  const [resultat, setResultat] = useState<ResultatVerification | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [encours, setEncours] = useState(false);
  const [survol, setSurvol] = useState(false);
  const [nomFichier, setNomFichier] = useState<string | null>(null);
  const entree = useRef<HTMLInputElement>(null);

  async function traiter(fichier: File) {
    setErreur(null);
    setResultat(null);
    setNomFichier(fichier.name);
    setEncours(true);
    try {
      const recu = lireRecu(await fichier.text());
      setResultat(await verifierRecu(recu, sources));
    } catch (e) {
      setErreur(messageErreur(e));
    } finally {
      setEncours(false);
      if (entree.current) entree.current.value = '';
    }
  }

  function deposer(e: DragEvent) {
    e.preventDefault();
    setSurvol(false);
    const f = e.dataTransfer.files[0];
    if (f) void traiter(f);
  }

  return (
    <>
      <EnTete
        titre="Vérifier un reçu"
        sousTitre="La vérification se fait dans votre navigateur. Le reçu n'est envoyé à aucun serveur ; seuls la racine ancrée et la clé publique du serveur sont lues."
      />

      <label
        className={`depot${survol ? ' survol' : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setSurvol(true);
        }}
        onDragLeave={() => setSurvol(false)}
        onDrop={deposer}
      >
        <Upload size={18} />
        <span>
          <strong>Déposez le reçu ici</strong> ou cliquez pour choisir le fichier JSON
        </span>
        {nomFichier && <span className="tertiaire mono">{nomFichier}</span>}
        <input
          ref={entree}
          type="file"
          accept="application/json,.json"
          className="visuellement-cache"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) void traiter(f);
          }}
        />
      </label>

      <div style={{ marginTop: 32 }}>
        {encours && <p className="chargement-texte">Vérification…</p>}
        {erreur && <Erreur message={erreur} />}
        {resultat && <Resultat r={resultat} />}
      </div>

      {!resultat && !encours && <Explication />}
    </>
  );
}

function Resultat({ r }: { r: ResultatVerification }) {
  const etats = r.controles.map((c) => c.etat);
  const global: EtatControle = etats.includes('echec')
    ? 'echec'
    : etats.includes('indetermine')
      ? 'indetermine'
      : 'ok';
  const verdict = {
    ok: 'Reçu authentique',
    echec: 'Reçu non conforme',
    indetermine: 'Vérification incomplète',
  }[global];
  const { recu } = r;

  return (
    <div className="deux-colonnes">
      <section>
        <p className={`verdict ${global}`} role="status">
          {verdict}
        </p>
        <ul className="controles">
          {r.controles.map((c) => {
            const Icone = ICONES[c.etat];
            return (
              <li key={c.id}>
                <Icone size={16} className={`icone ${c.etat}`} aria-hidden="true" strokeWidth={2.25} />
                <div>
                  <div className="titre">{c.titre}</div>
                  <div className="detail">{c.detail}</div>
                </div>
                <span className={c.etat} style={{ fontSize: 12.5, fontWeight: 500 }}>
                  {LIBELLES_ETAT[c.etat]}
                </span>
              </li>
            );
          })}
        </ul>
      </section>

      <aside>
        <dl className="proprietes">
          <div>
            <dt>Séance</dt>
            <dd>
              <span className="mono">{recu.seance.id}</span>
              <div className="secondaire" style={{ fontSize: 13 }}>
                {recu.seance.cours}, salle {recu.seance.salle}
              </div>
            </dd>
          </div>
          <div>
            <dt>Titulaire</dt>
            <dd>
              <span className="num">{recu.titulaire.matricule}</span>
              <div className="secondaire mono">appareil {recu.titulaire.appareil}</div>
            </dd>
          </div>
          <div>
            <dt>Statut du reçu</dt>
            <dd style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <PuceStatut statut={recu.statut} />
              <span className="secondaire num" style={{ fontSize: 13 }}>
                {recu.fenetres?.validees} / {recu.fenetres?.total} fenêtres
              </span>
            </dd>
          </div>
          <div>
            <dt>Racine de Merkle</dt>
            <dd style={{ display: 'flex', gap: 4, alignItems: 'flex-start' }}>
              <span className="empreinte-complete">{recu.arbre.racine}</span>
              <BoutonCopie texte={recu.arbre.racine} libelle="Copier la racine" />
            </dd>
          </div>
          {r.racineAncree && r.racineAncree !== recu.arbre.racine.toLowerCase() && (
            <div>
              <dt>Racine ancrée</dt>
              <dd>
                <span className="empreinte-complete" style={{ color: 'var(--absent)' }}>
                  {r.racineAncree}
                </span>
              </dd>
            </div>
          )}
          <div>
            <dt>Ancrage</dt>
            <dd>
              {recu.ancrage?.registre === 'fabric' ? 'Hyperledger Fabric' : (recu.ancrage?.registre ?? '–')}
              {recu.ancrage?.transaction && (
                <div className="secondaire mono" title={recu.ancrage.transaction}>
                  {recu.ancrage.transaction.slice(0, 24)}
                </div>
              )}
            </dd>
          </div>
          <div>
            <dt>Émis le</dt>
            <dd className="num">{dateHeure(recu.emis_ms, 0)}</dd>
          </div>
        </dl>
      </aside>
    </div>
  );
}

function Explication() {
  return (
    <section className="section" style={{ maxWidth: 680 }}>
      <h2 className="section-titre">Ce qui est vérifié</h2>
      <ol className="secondaire" style={{ margin: 0, paddingLeft: 18, lineHeight: '22px' }}>
        <li>Chaque événement du reçu appartient à l'arbre de Merkle de la séance (preuve d'inclusion).</li>
        <li>La racine de cet arbre est celle ancrée sur le registre, après correction éventuelle.</li>
        <li>Le reçu porte une signature valide du serveur PRESENCE.</li>
        <li>Le statut se recalcule à l'identique à partir des événements du reçu.</li>
      </ol>
    </section>
  );
}
