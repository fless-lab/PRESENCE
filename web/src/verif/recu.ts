// Vérification complète d'un reçu de présence (API section 9), côté client.
// Les accès réseau sont injectés pour que la logique reste testable.

import { canonique } from './canonique';
import { feuilleCanonique, verifierInclusion } from './merkle';
import { depuisHex, utf8 } from './octets';
import { importerClePublique, verifierSignature } from './signature';
import { calculerStatut, type EvenementSeance, type ResultatStatut, type Statut } from './statut';

export interface EvenementRecu {
  index: number;
  canonique: string;
  preuve: string[];
}

export interface Recu {
  v: string;
  type: string;
  seance: { id: string; cours: string; salle: number };
  titulaire: { matricule: string; appareil: string };
  statut: Statut;
  fenetres: { validees: number; total: number };
  arbre: { taille: number; racine: string };
  evenements: EvenementRecu[];
  ancrage: { registre: string; transaction: string | null };
  emis_ms: number;
  sig_serveur: string;
}

/** Correction ancrée : objet avec la nouvelle racine, ou directement la racine. */
export type CorrectionAncree =
  | string
  | { racine?: string; nouvelle_racine?: string };

export interface Ancrage {
  seance: string;
  racine: string;
  corrections: CorrectionAncree[];
  transaction: string | null;
  registre: string;
}

export type EtatControle = 'ok' | 'echec' | 'indetermine';

export interface Controle {
  id: 'inclusion' | 'ancrage' | 'signature' | 'statut';
  titre: string;
  etat: EtatControle;
  detail: string;
}

export interface ResultatVerification {
  recu: Recu;
  controles: Controle[];
  racineAncree: string | null;
  recalcul: ResultatStatut | null;
}

export interface Sources {
  lireAncrage: (seance: string) => Promise<Ancrage>;
  lireCleServeur: () => Promise<string>;
}

const estObjet = (x: unknown): x is Record<string, unknown> =>
  typeof x === 'object' && x !== null && !Array.isArray(x);

/** Contrôle de forme minimal : lève une erreur lisible si le fichier n'est pas un reçu. */
export function lireRecu(texte: string): Recu {
  let brut: unknown;
  try {
    brut = JSON.parse(texte);
  } catch {
    throw new Error("Le fichier n'est pas un JSON valide.");
  }
  if (!estObjet(brut) || brut.type !== 'RECU') {
    throw new Error("Ce fichier n'est pas un reçu PRESENCE.");
  }
  const manquants = [
    'seance',
    'titulaire',
    'statut',
    'arbre',
    'evenements',
    'emis_ms',
    'sig_serveur',
  ].filter((c) => !(c in brut));
  if (manquants.length > 0) {
    throw new Error(`Champs manquants : ${manquants.join(', ')}.`);
  }
  if (!Array.isArray(brut.evenements)) throw new Error('La liste des événements est invalide.');
  return brut as unknown as Recu;
}

/** Racine de référence : celle de la dernière correction si elle existe, sinon la racine initiale. */
export function racineDeReference(ancrage: Ancrage): string {
  const derniere = ancrage.corrections?.[ancrage.corrections.length - 1];
  if (derniere === undefined) return ancrage.racine;
  if (typeof derniere === 'string') return derniere;
  return derniere.racine ?? derniere.nouvelle_racine ?? ancrage.racine;
}

export function messageErreur(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

async function controlerInclusion(recu: Recu, evenements: EvenementSeance[]): Promise<Controle> {
  const titre = "Preuves d'inclusion";
  if (recu.evenements.length === 0) {
    return { id: 'inclusion', titre, etat: 'echec', detail: 'Le reçu ne contient aucun événement.' };
  }
  try {
    const racine = depuisHex(recu.arbre.racine);
    let echecs = 0;
    for (const ev of recu.evenements) {
      const feuille = await feuilleCanonique(ev.canonique);
      const preuve = ev.preuve.map(depuisHex);
      const ok = await verifierInclusion(ev.index, recu.arbre.taille, feuille, preuve, racine);
      if (!ok) echecs += 1;
    }
    const autres = evenements.filter(
      (e) => (e as unknown as { seance?: unknown }).seance !== recu.seance.id,
    ).length;
    if (echecs > 0) {
      return {
        id: 'inclusion',
        titre,
        etat: 'echec',
        detail: `${echecs} preuve${echecs > 1 ? 's' : ''} sur ${recu.evenements.length} ne mène${echecs > 1 ? 'nt' : ''} pas à la racine du reçu.`,
      };
    }
    if (autres > 0) {
      return {
        id: 'inclusion',
        titre,
        etat: 'echec',
        detail: `${autres} événement${autres > 1 ? 's' : ''} ne concerne${autres > 1 ? 'nt' : ''} pas la séance ${recu.seance.id}.`,
      };
    }
    return {
      id: 'inclusion',
      titre,
      etat: 'ok',
      detail: `${recu.evenements.length} événements sur ${recu.arbre.taille} reliés à la racine.`,
    };
  } catch (e) {
    return { id: 'inclusion', titre, etat: 'echec', detail: messageErreur(e) };
  }
}

async function controlerAncrage(
  recu: Recu,
  sources: Sources,
): Promise<{ controle: Controle; racine: string | null }> {
  const titre = 'Racine ancrée sur le registre';
  try {
    const ancrage = await sources.lireAncrage(recu.seance.id);
    const reference = racineDeReference(ancrage).toLowerCase();
    const corrigee = (ancrage.corrections?.length ?? 0) > 0;
    const ok = reference === recu.arbre.racine.toLowerCase();
    const origine = `registre ${ancrage.registre}${corrigee ? ', après correction' : ''}`;
    return {
      racine: reference,
      controle: {
        id: 'ancrage',
        titre,
        etat: ok ? 'ok' : 'echec',
        detail: ok
          ? `La racine du reçu est celle ancrée (${origine}).`
          : `La racine ancrée (${origine}) diffère de celle du reçu.`,
      },
    };
  } catch (e) {
    return {
      racine: null,
      controle: {
        id: 'ancrage',
        titre,
        etat: 'indetermine',
        detail: `Ancrage illisible : ${messageErreur(e)}`,
      },
    };
  }
}

async function controlerSignature(recu: Recu, sources: Sources): Promise<Controle> {
  const titre = 'Signature du serveur';
  let cle: CryptoKey;
  try {
    cle = await importerClePublique(await sources.lireCleServeur());
  } catch (e) {
    return {
      id: 'signature',
      titre,
      etat: 'indetermine',
      detail: `Clé du serveur illisible : ${messageErreur(e)}`,
    };
  }
  try {
    const { sig_serveur, ...reste } = recu;
    const ok = await verifierSignature(cle, sig_serveur, utf8(canonique(reste)));
    return {
      id: 'signature',
      titre,
      etat: ok ? 'ok' : 'echec',
      detail: ok
        ? "Le reçu a été signé par le serveur et n'a pas été modifié."
        : 'La signature ne correspond pas au contenu du reçu.',
    };
  } catch (e) {
    return { id: 'signature', titre, etat: 'echec', detail: messageErreur(e) };
  }
}

const LIBELLES: Record<Statut, string> = {
  PRESENT: 'Présent',
  RETARD: 'Retard',
  DEPART_ANTICIPE: 'Départ anticipé',
  PARTIEL: 'Partiel',
  A_VERIFIER: 'À vérifier',
  ABSENT: 'Absent',
};

function controlerStatut(recu: Recu, recalcul: ResultatStatut): Controle {
  const titre = 'Statut recalculé';
  const fenetres = `${recalcul.validees.length}/${recalcul.total} fenêtres`;
  const fenetresOk =
    recalcul.validees.length === recu.fenetres?.validees && recalcul.total === recu.fenetres?.total;

  if (recu.statut === 'A_VERIFIER' && recalcul.regle === 'FENETRES') {
    return {
      id: 'statut',
      titre,
      etat: 'indetermine',
      detail: `Le reçu indique « À vérifier ». Cette règle dépend d'attestations dans d'autres salles, absentes du reçu : elle ne peut pas être recalculée. Sans elle : ${LIBELLES[recalcul.statut]}, ${fenetres}.`,
    };
  }
  const statutOk = recalcul.statut === recu.statut;
  if (statutOk && fenetresOk) {
    return {
      id: 'statut',
      titre,
      etat: 'ok',
      detail: `${LIBELLES[recalcul.statut]}, ${fenetres}, identique au reçu.`,
    };
  }
  const ecarts: string[] = [];
  if (!statutOk) ecarts.push(`statut ${LIBELLES[recalcul.statut] ?? recalcul.statut} au lieu de ${LIBELLES[recu.statut] ?? recu.statut}`);
  if (!fenetresOk) ecarts.push(`${fenetres} au lieu de ${recu.fenetres?.validees}/${recu.fenetres?.total}`);
  return { id: 'statut', titre, etat: 'echec', detail: `Recalcul : ${ecarts.join(', ')}.` };
}

export async function verifierRecu(recu: Recu, sources: Sources): Promise<ResultatVerification> {
  let evenements: EvenementSeance[] = [];
  let erreurLecture: string | null = null;
  try {
    evenements = recu.evenements.map((e) => JSON.parse(e.canonique) as EvenementSeance);
  } catch {
    erreurLecture = "Un événement du reçu n'est pas un JSON valide.";
  }

  const [inclusion, ancrage, signature] = await Promise.all([
    controlerInclusion(recu, evenements),
    controlerAncrage(recu, sources),
    controlerSignature(recu, sources),
  ]);

  let recalcul: ResultatStatut | null = null;
  let statut: Controle;
  if (erreurLecture) {
    statut = { id: 'statut', titre: 'Statut recalculé', etat: 'echec', detail: erreurLecture };
  } else {
    recalcul = calculerStatut(evenements, recu.titulaire, recu.emis_ms);
    statut = controlerStatut(recu, recalcul);
  }

  return {
    recu,
    controles: [inclusion, ancrage.controle, signature, statut],
    racineAncree: ancrage.racine,
    recalcul,
  };
}
