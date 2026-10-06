// Client HTTP du tableau de bord. Base configurable avec VITE_API_URL (par défaut /api).

import type {
  Apercu,
  Audit,
  AncrageRegistre,
  CleServeur,
  ConnexionReponse,
  CorrectionDemande,
  EquipementDemande,
  Equipement,
  EvenementJournal,
  EtatSeance,
  ImportResultat,
  NouveauCode,
  PersonneAdmin,
  RefSalle,
  SeanceDetail,
  SeanceResume,
} from './types';

export const BASE_API = (import.meta.env.VITE_API_URL ?? '/api').replace(/\/$/, '');

const CLE_JETON = 'presence.jeton';
const CLE_PERSONNE = 'presence.personne';

export const session = {
  jeton(): string | null {
    try {
      return sessionStorage.getItem(CLE_JETON);
    } catch {
      return null;
    }
  },
  personne(): ConnexionReponse['personne'] | null {
    try {
      const brut = sessionStorage.getItem(CLE_PERSONNE);
      return brut ? (JSON.parse(brut) as ConnexionReponse['personne']) : null;
    } catch {
      return null;
    }
  },
  enregistrer(r: ConnexionReponse) {
    sessionStorage.setItem(CLE_JETON, r.jeton);
    sessionStorage.setItem(CLE_PERSONNE, JSON.stringify(r.personne));
  },
  effacer() {
    try {
      sessionStorage.removeItem(CLE_JETON);
      sessionStorage.removeItem(CLE_PERSONNE);
    } catch {
      /* stockage indisponible */
    }
  },
};

export class ErreurApi extends Error {
  constructor(
    message: string,
    readonly statut: number,
  ) {
    super(message);
  }
}

/** Appelé quand le serveur répond 401 : la session est effacée et l'application redirige. */
let surExpiration: () => void = () => {};
export function definirSurExpiration(f: () => void) {
  surExpiration = f;
}

function messageDetail(corps: unknown, statut: number): string {
  if (corps && typeof corps === 'object' && 'detail' in corps) {
    const d = (corps as { detail: unknown }).detail;
    if (typeof d === 'string') return d;
    if (Array.isArray(d)) {
      return d
        .map((x) => (x && typeof x === 'object' && 'msg' in x ? String(x.msg) : String(x)))
        .join(' ; ');
    }
  }
  if (statut === 0) return 'Serveur injoignable.';
  return `Erreur ${statut}.`;
}

async function requete(
  chemin: string,
  options: RequestInit & { auth?: boolean } = {},
): Promise<Response> {
  const { auth = true, headers, ...reste } = options;
  const entetes = new Headers(headers);
  const jeton = session.jeton();
  if (auth && jeton) entetes.set('Authorization', `Bearer ${jeton}`);
  let reponse: Response;
  try {
    reponse = await fetch(BASE_API + chemin, { ...reste, headers: entetes });
  } catch {
    throw new ErreurApi('Serveur injoignable.', 0);
  }
  if (!reponse.ok) {
    let corps: unknown = null;
    try {
      corps = await reponse.json();
    } catch {
      /* corps vide ou non JSON */
    }
    if (reponse.status === 401 && auth) {
      session.effacer();
      surExpiration();
    }
    throw new ErreurApi(messageDetail(corps, reponse.status), reponse.status);
  }
  return reponse;
}

async function json<T>(chemin: string, options?: RequestInit & { auth?: boolean }): Promise<T> {
  const r = await requete(chemin, options);
  if (r.status === 204) return undefined as T;
  return (await r.json()) as T;
}

function envoyer<T>(chemin: string, corps: unknown, auth = true): Promise<T> {
  return json<T>(chemin, {
    method: 'POST',
    auth,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(corps),
  });
}

/** Accepte { cle: [...] } ou directement [...]. */
function liste<T>(reponse: unknown, cle: string): T[] {
  if (Array.isArray(reponse)) return reponse as T[];
  if (reponse && typeof reponse === 'object') {
    const v = (reponse as Record<string, unknown>)[cle];
    if (Array.isArray(v)) return v as T[];
  }
  return [];
}

const enc = encodeURIComponent;

export const api = {
  connexion: (matricule: string, mot_de_passe: string) =>
    envoyer<ConnexionReponse>('/auth/connexion', { matricule, mot_de_passe }, false),

  apercu: () => json<Apercu>('/admin/apercu'),

  seances: async (etat?: EtatSeance) =>
    liste<SeanceResume>(
      await json<unknown>('/admin/seances' + (etat ? `?etat=${enc(etat)}` : '')),
      'seances',
    ),
  seance: (id: string) => json<SeanceDetail>(`/admin/seances/${enc(id)}`),
  evenements: async (id: string) =>
    liste<EvenementJournal>(await json<unknown>(`/admin/seances/${enc(id)}/evenements`), 'evenements'),
  corriger: (id: string, corps: CorrectionDemande) =>
    envoyer<unknown>(`/admin/seances/${enc(id)}/corrections`, corps),
  async exporterCsv(id: string): Promise<Blob> {
    const r = await requete(`/admin/seances/${enc(id)}/export.csv`);
    return r.blob();
  },

  personnes: async () => liste<PersonneAdmin>(await json<unknown>('/admin/personnes'), 'personnes'),
  importer: (fichier: File) => {
    const corps = new FormData();
    corps.append('fichier', fichier);
    return json<ImportResultat>('/admin/import', { method: 'POST', body: corps });
  },
  nouveauCode: (matricule: string) =>
    envoyer<NouveauCode>(`/admin/personnes/${enc(matricule)}/code`, {}),

  salles: async () => liste<RefSalle>(await json<unknown>('/admin/salles'), 'salles'),
  ajouterSalle: (salle: RefSalle) => envoyer<unknown>('/admin/salles', salle),
  equipements: async () =>
    liste<Equipement>(await json<unknown>('/admin/equipements'), 'equipements'),
  ajouterEquipement: (e: EquipementDemande) => envoyer<unknown>('/admin/equipements', e),

  audit: () => json<Audit>('/audit'),

  cleServeur: () => json<CleServeur>('/cle-serveur', { auth: false }),
  ancrage: (seance: string) => json<AncrageRegistre>(`/ancrages/${enc(seance)}`, { auth: false }),
};
