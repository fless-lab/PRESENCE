import type { EtatSeance, Role, Statut } from '../api/types';

export const STATUTS: Record<Statut, { libelle: string; classe: string }> = {
  PRESENT: { libelle: 'Présent', classe: 'puce-present' },
  RETARD: { libelle: 'Retard', classe: 'puce-retard' },
  DEPART_ANTICIPE: { libelle: 'Départ anticipé', classe: 'puce-depart' },
  PARTIEL: { libelle: 'Partiel', classe: 'puce-partiel' },
  A_VERIFIER: { libelle: 'À vérifier', classe: 'puce-a-verifier' },
  ABSENT: { libelle: 'Absent', classe: 'puce-absent' },
};

export const ORDRE_STATUTS: Statut[] = [
  'PRESENT',
  'RETARD',
  'DEPART_ANTICIPE',
  'PARTIEL',
  'A_VERIFIER',
  'ABSENT',
];

export const ETATS: Record<EtatSeance, { libelle: string; classe: string }> = {
  ACTIVE: { libelle: 'Active', classe: 'puce-present' },
  PAUSE: { libelle: 'En pause', classe: 'puce-retard' },
  CLOTUREE: { libelle: 'Clôturée', classe: 'puce-neutre' },
  SCELLEE: { libelle: 'Scellée', classe: 'puce-sombre' },
};

export const ROLES: Record<Role, string> = {
  ETUDIANT: 'Étudiant',
  ENSEIGNANT: 'Enseignant',
  SCOLARITE: 'Scolarité',
  ADMIN: 'Administration',
  AUDITEUR: 'Audit',
};

export const ATTESTATIONS: Record<string, string> = {
  VERIFIEE: 'Attestation vérifiée',
  REFUSEE: 'Attestation refusée',
  ABSENTE: 'Sans attestation',
};
