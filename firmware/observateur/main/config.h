/* Configuration reçue du serveur : salle, séance, clés des appareils autorisés. */
#pragma once

#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"
#include "sdkconfig.h"

#define NB_CLES_MAX 64

/* Mode test de l'expérience 01 (Kconfig PRESENCE_MODE_TEST), utilisable dans un if ordinaire. */
#ifdef CONFIG_PRESENCE_MODE_TEST
#define MODE_TEST 1
#else
#define MODE_TEST 0
#endif

typedef struct {
    uint16_t salle;
    uint32_t seance;    /* 0 hors séance */
    uint8_t etat;       /* presence_etat_seance_t */
} config_etat_t;

typedef enum {
    VERIF_OK = 0,
    VERIF_ID_INCONNU,
    VERIF_SIGNATURE,
    VERIF_COMPTEUR,
} config_verif_t;

/* Valeurs initiales (salle de menuconfig, aucune séance ; séance 1 active en mode test). */
esp_err_t config_init(void);

/* Lance l'interrogation de GET /equipements/moi/config toutes les 10 s (sans effet en mode test). */
esp_err_t config_demarrer(void);

void config_lire(config_etat_t *etat);

/*
 * Cherche la clé de id, vérifie la signature DER sur condensat puis exige
 * compteur > dernier compteur accepté pour cet appareil, qui est alors mis à jour.
 */
config_verif_t config_verifier(const uint8_t id[8], const uint8_t condensat[32],
                               const uint8_t *sig, size_t lg_sig, uint32_t compteur);
