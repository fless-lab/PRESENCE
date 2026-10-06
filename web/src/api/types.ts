/*
 * Types des réponses du serveur consommées par le tableau de bord.
 *
 * Hypothèses (à aligner côté serveur) :
 * - Tous les instants sont des entiers en millisecondes Unix (suffixe _ms), comme dans API.md.
 * - Les hachages et racines sont en hexadécimal minuscule sur 64 caractères.
 * - Les listes sont renvoyées dans un objet ({ seances: [...] }) plutôt qu'un tableau nu,
 *   pour pouvoir ajouter des champs sans casser le contrat. Le client accepte aussi un tableau nu.
 * - « presents » compte les inscrits dont le statut courant est PRESENT ou RETARD.
 * - « taux_presence » est une fraction entre 0 et 1, ou null s'il n'y a aucune séance aujourd'hui.
 * - Un équipement est « en ligne » si le serveur l'a vu depuis moins de 90 s ; le serveur
 *   renvoie en_ligne, et le client recalcule à partir de vu_ms si le champ manque.
 * - Les erreurs suivent le format FastAPI : { detail: string } (ou une liste de validation).
 */

export type Role = 'ETUDIANT' | 'ENSEIGNANT' | 'SCOLARITE' | 'ADMIN' | 'AUDITEUR';
export type EtatSeance = 'ACTIVE' | 'PAUSE' | 'CLOTUREE' | 'SCELLEE';
export type Statut = 'PRESENT' | 'RETARD' | 'DEPART_ANTICIPE' | 'PARTIEL' | 'A_VERIFIER' | 'ABSENT';
export type TypeEquipement = 'OBSERVATEUR' | 'PORTE';
export type Registre = 'fabric' | 'local';

export interface Personne {
  matricule: string;
  nom: string;
  prenom: string;
  role: Role;
}

/* POST /auth/connexion */
export interface ConnexionReponse {
  jeton: string;
  personne: Personne;
}

export interface RefCours {
  code: string;
  intitule: string;
}

export interface RefSalle {
  id: number;
  nom: string;
}

export interface RefEnseignant {
  matricule: string;
  nom: string;
  prenom: string;
}

/* Ligne de GET /admin/seances et de GET /admin/apercu (seances_actives) */
export interface SeanceResume {
  id: string;
  cours: RefCours;
  salle: RefSalle;
  enseignant: RefEnseignant;
  etat: EtatSeance;
  debut_ms: number;
  fin_ms: number | null;
  duree_active_ms: number;
  presents: number;
  inscrits: number;
  racine: string | null;
}

/* GET /admin/equipements, et GET /admin/apercu (equipements) */
export interface Equipement {
  nom: string;
  salle: number;
  nom_salle: string;
  type: TypeEquipement;
  actif: boolean;
  vu_ms: number | null;
  en_ligne: boolean;
}

/* GET /admin/apercu */
export interface Apercu {
  maintenant_ms: number;
  chiffres: {
    seances_actives: number;
    seances_du_jour: number;
    taux_presence: number | null;
    equipements_en_ligne: number;
    equipements_total: number;
  };
  seances_actives: SeanceResume[];
  equipements: Equipement[];
}

/* GET /admin/seances?etat=ACTIVE (paramètre facultatif) */
export interface ListeSeances {
  seances: SeanceResume[];
}

export interface IntervalleActif {
  debut_ms: number;
  /** null : intervalle en cours */
  fin_ms: number | null;
}

export interface EtudiantSeance {
  matricule: string;
  nom: string;
  prenom: string;
  appareil: string | null;
  statut: Statut;
  /** Indices des fenêtres validées, entre 0 et fenetres.total - 1 */
  fenetres_validees: number[];
  manuel: boolean;
  corrige: boolean;
  /** Raison de l'état « À vérifier », sinon null */
  motif_a_verifier: string | null;
}

export interface Integrite {
  /** Racine de Merkle courante (après corrections éventuelles), null tant que non scellée */
  racine: string | null;
  nb_evenements: number;
  registre: Registre | null;
  transaction: string | null;
  /** Nombre de corrections ré-ancrées */
  corrections: number;
}

/* GET /admin/seances/{id} */
export interface SeanceDetail extends SeanceResume {
  maintenant_ms: number;
  intervalles: IntervalleActif[];
  fenetres: { total: number; duree_ms: number };
  etudiants: EtudiantSeance[];
  integrite: Integrite;
}

/* GET /admin/seances/{id}/evenements */
export interface EvenementJournal {
  index: number;
  type: string;
  horodatage: number;
  auteur: string;
  /** Hachage de feuille : SHA-256(0x00 | canonique) */
  hachage: string;
  contenu: Record<string, unknown>;
}

export interface JournalEvenements {
  evenements: EvenementJournal[];
}

/* POST /admin/seances/{id}/corrections (corps) */
export interface CorrectionDemande {
  matricule: string;
  statut: Statut;
  motif: string;
}

/* GET /admin/personnes */
export interface AppareilPersonne {
  id: string;
  modele: string | null;
  actif: boolean;
  /** Résultat de la vérification de l'attestation de clé */
  attestation: 'VERIFIEE' | 'REFUSEE' | 'ABSENTE';
  enrole_ms: number;
}

export interface PersonneAdmin extends Personne {
  appareil: AppareilPersonne | null;
  code_en_attente: string | null;
}

export interface ListePersonnes {
  personnes: PersonneAdmin[];
}

/* POST /admin/import (multipart, champ « fichier ») */
export interface ImportResultat {
  personnes_creees: number;
  personnes_mises_a_jour: number;
  cours_crees: number;
  inscriptions: number;
  codes: { matricule: string; nom: string; prenom: string; code: string }[];
  erreurs: { ligne: number; message: string }[];
}

/* POST /admin/personnes/{matricule}/code */
export interface NouveauCode {
  matricule: string;
  code: string;
}

/* GET /admin/salles ; POST /admin/salles prend { id, nom } */
export interface ListeSalles {
  salles: RefSalle[];
}

/* GET /admin/equipements ; POST /admin/equipements prend EquipementDemande */
export interface ListeEquipements {
  equipements: Equipement[];
}

export interface EquipementDemande {
  nom: string;
  salle: number;
  type: TypeEquipement;
  cle_publique: string;
}

/* GET /audit */
export interface LigneAudit {
  seance: string;
  cours: string;
  debut_ms: number;
  nb_evenements: number;
  racine_recalculee: string;
  racine_ancree: string | null;
  registre: Registre | null;
  conforme: boolean;
}

export interface Audit {
  verifie_ms: number;
  seances: LigneAudit[];
}

/* Routes publiques : GET /cle-serveur et GET /ancrages/{seance} */
export interface CleServeur {
  cle_publique: string;
}

export interface CorrectionAncree {
  racine: string;
  racine_precedente: string;
  motif: string;
  transaction: string | null;
}

export interface AncrageRegistre {
  seance: string;
  racine: string;
  corrections: CorrectionAncree[];
  transaction: string | null;
  registre: Registre;
}
