/*
 * Compteur de porte PRESENCE (phase 2).
 *
 * Deux VL53L1X sur le même montant : C côté couloir, S côté salle.
 * C puis S : ENTREE. S puis C : SORTIE.
 *
 * Modules prévus :
 *   capteurs.c  initialisation I2C, adresses 0x30 et 0x31 via les broches XSHUT, lecture à 30-50 Hz
 *   passage.c   machine à états du passage, rejet des coupures partielles et des ouvertures de porte
 *   identite.c  clé ECDSA P-256 du compteur
 *   reseau.c    envoi des événements PASSAGE signés au serveur
 */
#include <stdio.h>
#include "esp_log.h"
#include "nvs_flash.h"
#include "presence_protocole.h"

static const char *TAG = "porte";

void app_main(void)
{
    ESP_ERROR_CHECK(nvs_flash_init());
    ESP_LOGI(TAG, "PRESENCE compteur de porte %s, salle %d",
             CONFIG_PRESENCE_NOM_OBSERVATEUR, CONFIG_PRESENCE_SALLE);
}
