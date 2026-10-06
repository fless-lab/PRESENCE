/* Nonce de salle : 16 octets aléatoires renouvelés toutes les PRESENCE_NONCE_PERIODE_S. */
#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"
#include "presence_protocole.h"

/* À appeler une fois la radio active (Wi-Fi ou BLE), pour que esp_fill_random soit un vrai aléa. */
esp_err_t nonce_demarrer(void);

void nonce_courant(uint8_t nonce[PRESENCE_TAILLE_NONCE], uint32_t *numero);

/*
 * Retrouve le nonce de numéro donné : le courant, ou le précédent pendant
 * PRESENCE_NONCE_GRACE_S après le renouvellement. Renvoie faux sinon.
 */
bool nonce_retrouver(uint32_t numero, uint8_t nonce[PRESENCE_TAILLE_NONCE]);
