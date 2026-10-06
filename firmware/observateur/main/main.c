/*
 * Observateur de salle PRESENCE.
 *
 *   identite.c     clé ECDSA P-256 de l'observateur, gardée en NVS
 *   reseau.c       Wi-Fi, heure du serveur, requêtes HTTP signées
 *   config.c       salle, séance et clés des appareils (GET /equipements/moi/config)
 *   nonce.c        nonce de salle renouvelé toutes les 45 s
 *   ble.c          annonce et service GATT (NimBLE)
 *   attestation.c  vérification des attestations, production des témoignages
 *   journal.c      file des témoignages, envoi par lots
 *   led.c          voyant d'état
 */
#include "esp_log.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "nvs_flash.h"
#include "presence_protocole.h"
#include "identite.h"
#include "reseau.h"
#include "config.h"
#include "nonce.h"
#include "attestation.h"
#include "journal.h"
#include "ble.h"
#include "led.h"

static const char *TAG = "observateur";

void app_main(void)
{
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());

    ESP_LOGI(TAG, "PRESENCE observateur %s, salle %d, protocole v%d%s",
             CONFIG_PRESENCE_NOM_OBSERVATEUR, CONFIG_PRESENCE_SALLE, PRESENCE_VERSION_PROTOCOLE,
             MODE_TEST ? ", MODE TEST" : "");

    ESP_ERROR_CHECK(led_demarrer());
    /* Le Wi-Fi d'abord : la radio active rend esp_fill_random réellement aléatoire */
    ESP_ERROR_CHECK(reseau_demarrer());
    ESP_ERROR_CHECK(identite_init());
    ESP_ERROR_CHECK(config_init());
    ESP_ERROR_CHECK(nonce_demarrer());
    ESP_ERROR_CHECK(journal_demarrer());
    ESP_ERROR_CHECK(attestation_demarrer());
    ESP_ERROR_CHECK(ble_demarrer());
    ESP_ERROR_CHECK(config_demarrer());
}
