-- Schéma initial PRESENCE (phase 1, extensible en phase 2).
-- Les événements ne sont jamais modifiés ni supprimés.

CREATE TABLE personnes (
    id              BIGSERIAL PRIMARY KEY,
    matricule       TEXT UNIQUE NOT NULL,
    nom             TEXT NOT NULL,
    prenom          TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('ETUDIANT', 'ENSEIGNANT', 'SCOLARITE', 'ADMIN', 'AUDITEUR')),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE appareils (
    id_appareil     BYTEA PRIMARY KEY CHECK (length(id_appareil) = 8),
    personne_id     BIGINT NOT NULL REFERENCES personnes(id),
    cle_publique    BYTEA NOT NULL,              -- SPKI DER
    attestation     JSONB,                       -- chaîne d'attestation de clé
    modele          TEXT,
    actif           BOOLEAN NOT NULL DEFAULT true,
    enrole_le       TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoque_le      TIMESTAMPTZ
);
CREATE UNIQUE INDEX un_appareil_actif_par_personne ON appareils(personne_id) WHERE actif;

CREATE TABLE salles (
    id              INTEGER PRIMARY KEY,
    nom             TEXT NOT NULL
);

CREATE TABLE equipements (
    nom             TEXT PRIMARY KEY,            -- ex. 204-A, 204-P
    salle_id        INTEGER NOT NULL REFERENCES salles(id),
    type            TEXT NOT NULL CHECK (type IN ('OBSERVATEUR', 'PORTE')),
    cle_publique    BYTEA NOT NULL,
    actif           BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE cours (
    id              BIGSERIAL PRIMARY KEY,
    code            TEXT UNIQUE NOT NULL,
    intitule        TEXT NOT NULL,
    groupe          TEXT NOT NULL,
    enseignant_id   BIGINT NOT NULL REFERENCES personnes(id)
);

CREATE TABLE inscriptions (
    cours_id        BIGINT NOT NULL REFERENCES cours(id),
    personne_id     BIGINT NOT NULL REFERENCES personnes(id),
    PRIMARY KEY (cours_id, personne_id)
);

CREATE TABLE seances (
    id              TEXT PRIMARY KEY,            -- ex. S-2026-1006-204-01
    cours_id        BIGINT NOT NULL REFERENCES cours(id),
    salle_id        INTEGER NOT NULL REFERENCES salles(id),
    etat            TEXT NOT NULL CHECK (etat IN ('ACTIVE', 'PAUSE', 'CLOTUREE', 'SCELLEE')),
    debut           TIMESTAMPTZ NOT NULL,
    fin             TIMESTAMPTZ,
    racine_merkle   BYTEA,
    nb_evenements   INTEGER,
    ancrage_tx      TEXT
);

CREATE TABLE evenements (
    id              BIGSERIAL PRIMARY KEY,
    seance_id       TEXT REFERENCES seances(id),
    type            TEXT NOT NULL CHECK (type IN (
                        'DEBUT', 'PAUSE', 'REPRISE', 'CLOTURE', 'ATTESTATION', 'PASSAGE',
                        'VALIDATION_MANUELLE', 'DECISION', 'CORRECTION')),
    auteur          TEXT NOT NULL,               -- ex. obs:204-A, ens:12, porte:204-P
    horodatage      TIMESTAMPTZ NOT NULL,
    contenu         JSONB NOT NULL,
    canonique       BYTEA NOT NULL,              -- JSON canonique (RFC 8785) haché dans l'arbre
    hachage         BYTEA NOT NULL UNIQUE,       -- SHA-256(0x00 || canonique)
    recu_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX evenements_par_seance ON evenements(seance_id, horodatage);

-- Interdire toute modification ou suppression d'un événement
CREATE FUNCTION refuser_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Les événements sont immuables : ajouter un événement CORRECTION';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER evenements_immuables
    BEFORE UPDATE OR DELETE ON evenements
    FOR EACH ROW EXECUTE FUNCTION refuser_modification();
