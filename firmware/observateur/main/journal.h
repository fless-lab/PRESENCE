/* File des témoignages signés, envoyés par lots au serveur. */
#pragma once

#include <stdint.h>
#include "esp_err.h"
#include "presence_protocole.h"

#define JOURNAL_CAPACITE 128

typedef struct {
    uint32_t seance;
    uint8_t nonce[PRESENCE_TAILLE_NONCE];
    uint32_t numero_nonce;
    uint64_t recu_ms;
    uint8_t trame[PRESENCE_TAILLE_ATTESTATION_MAX];
    uint8_t lg_trame;
    uint8_t sig[PRESENCE_TAILLE_SIGNATURE_MAX];
    uint8_t lg_sig;
} temoignage_t;

/* Lance la tâche d'envoi (toutes les PRESENCE_ENVOI_LOT_S, ou dès 20 témoignages en attente). */
esp_err_t journal_demarrer(void);

/* Ajoute un témoignage. File pleine : le plus ancien est écrasé. */
void journal_ajouter(const temoignage_t *t);
