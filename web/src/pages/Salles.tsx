import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { TypeEquipement } from '../api/types';
import { EnTete } from '../components/EnTete';
import { Erreur, LignesSquelette, Vide } from '../components/Etats';
import { useMaintenant, useRessource } from '../lib/useRessource';
import { messageErreur } from '../verif/recu';
import { TableEquipements } from './Apercu';

export function Salles() {
  const salles = useRessource(api.salles);
  const equipements = useRessource(api.equipements, 10_000);
  const maintenant = useMaintenant();

  return (
    <>
      <EnTete titre="Salles et équipements" />
      {salles.erreur && <Erreur message={salles.erreur} />}
      {equipements.erreur && <Erreur message={equipements.erreur} />}

      <section className="section">
        <h2 className="section-titre">Salles</h2>
        <FormulaireSalle onAjout={salles.recharger} />
        <div className="tableau-conteneur" style={{ marginTop: 16 }}>
          <table className="tableau">
            <thead>
              <tr>
                <th className="num" style={{ width: 120, textAlign: 'left' }}>Identifiant</th>
                <th>Nom</th>
                <th className="num">Équipements</th>
              </tr>
            </thead>
            <tbody>
              {salles.chargement && !salles.donnees && <LignesSquelette colonnes={3} lignes={3} />}
              {salles.donnees
                ?.slice()
                .sort((a, b) => a.id - b.id)
                .map((s) => (
                  <tr key={s.id}>
                    <td className="num" style={{ textAlign: 'left' }}>{s.id}</td>
                    <td>{s.nom}</td>
                    <td className="num secondaire">
                      {equipements.donnees?.filter((e) => e.salle === s.id).length ?? '–'}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
        {salles.donnees && salles.donnees.length === 0 && <Vide>Aucune salle enregistrée.</Vide>}
      </section>

      <section className="section">
        <h2 className="section-titre">Équipements</h2>
        <FormulaireEquipement
          salles={salles.donnees ?? []}
          onAjout={equipements.recharger}
        />
        <div style={{ marginTop: 16 }}>
          <TableEquipements
            equipements={equipements.donnees ?? undefined}
            chargement={equipements.chargement && !equipements.donnees}
            maintenant={maintenant}
          />
        </div>
      </section>
    </>
  );
}

function FormulaireSalle({ onAjout }: { onAjout: () => Promise<void> }) {
  const [id, setId] = useState('');
  const [nom, setNom] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  async function soumettre(e: FormEvent) {
    e.preventDefault();
    setEnvoi(true);
    setErreur(null);
    try {
      await api.ajouterSalle({ id: Number(id), nom: nom.trim() });
      setId('');
      setNom('');
      await onAjout();
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <form onSubmit={soumettre}>
      {erreur && <Erreur message={erreur} />}
      <div className="formulaire-ligne">
        <label className="champ" style={{ flex: '0 0 140px' }}>
          <span>Identifiant numérique</span>
          <input
            className="saisie num"
            inputMode="numeric"
            pattern="[0-9]+"
            min={0}
            max={65535}
            required
            value={id}
            onChange={(e) => setId(e.target.value.replace(/\D/g, ''))}
          />
        </label>
        <label className="champ">
          <span>Nom</span>
          <input className="saisie" required placeholder="Amphi B, salle 204" value={nom} onChange={(e) => setNom(e.target.value)} />
        </label>
        <button type="submit" className="bouton" disabled={envoi}>
          Ajouter la salle
        </button>
      </div>
    </form>
  );
}

function FormulaireEquipement({
  salles,
  onAjout,
}: {
  salles: { id: number; nom: string }[];
  onAjout: () => Promise<void>;
}) {
  const [nom, setNom] = useState('');
  const [salle, setSalle] = useState('');
  const [type, setType] = useState<TypeEquipement>('OBSERVATEUR');
  const [cle, setCle] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  async function soumettre(e: FormEvent) {
    e.preventDefault();
    const clePropre = cle.replace(/\s+/g, '');
    if (!/^[A-Za-z0-9+/]+={0,2}$/.test(clePropre)) {
      setErreur("La clé publique doit être en base64 (copiée depuis le journal série de l'équipement).");
      return;
    }
    setEnvoi(true);
    setErreur(null);
    try {
      await api.ajouterEquipement({ nom: nom.trim(), salle: Number(salle), type, cle_publique: clePropre });
      setNom('');
      setCle('');
      await onAjout();
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <form onSubmit={soumettre} className="formulaire-pile">
      {erreur && <Erreur message={erreur} />}
      <div className="formulaire-ligne">
        <label className="champ">
          <span>Nom</span>
          <input className="saisie mono" required placeholder="204-A" value={nom} onChange={(e) => setNom(e.target.value)} />
        </label>
        <label className="champ">
          <span>Salle</span>
          <select className="saisie" required value={salle} onChange={(e) => setSalle(e.target.value)}>
            <option value="" disabled>
              Choisir
            </option>
            {salles.map((s) => (
              <option key={s.id} value={s.id}>
                {s.id} · {s.nom}
              </option>
            ))}
          </select>
        </label>
        <label className="champ">
          <span>Type</span>
          <select className="saisie" value={type} onChange={(e) => setType(e.target.value as TypeEquipement)}>
            <option value="OBSERVATEUR">Observateur</option>
            <option value="PORTE">Compteur de porte</option>
          </select>
        </label>
      </div>
      <label className="champ">
        <span>Clé publique (SPKI DER en base64, depuis le journal série)</span>
        <textarea
          className="saisie mono"
          required
          rows={3}
          spellCheck={false}
          placeholder="MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE…"
          value={cle}
          onChange={(e) => setCle(e.target.value)}
        />
      </label>
      <div>
        <button type="submit" className="bouton" disabled={envoi}>
          Ajouter l'équipement
        </button>
      </div>
    </form>
  );
}
