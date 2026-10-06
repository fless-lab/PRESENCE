/* Voyant d'état (Kconfig PRESENCE_GPIO_LED, -1 pour le désactiver). */
#pragma once

#include "esp_err.h"

/* Clignotement lent sans Wi-Fi, allumé fixe une fois connecté. */
esp_err_t led_demarrer(void);

/* Bref éclat signalant une attestation acceptée. */
void led_eclat(void);
