/* Traitement des trames ATTEST, hors de la tâche NimBLE. */
#pragma once

#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

esp_err_t attestation_demarrer(void);

/*
 * Copie la trame dans la file de vérification. Appelée depuis le rappel NimBLE.
 * Renvoie ESP_ERR_NO_MEM si la file est pleine.
 */
esp_err_t attestation_soumettre(uint16_t connexion, const uint8_t *trame, size_t lg);
