/* Balise et service GATT PRESENCE (NimBLE). */
#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

esp_err_t ble_demarrer(void);

/* Recalcule l'annonce après un changement de salle ou d'état. Appelable depuis toute tâche. */
void ble_actualiser_annonce(void);

/* RSSI de la connexion, si elle est encore ouverte. */
bool ble_rssi(uint16_t connexion, int8_t *rssi);
