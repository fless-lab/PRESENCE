/*
 * Observateur de salle PRESENCE.
 *
 * Modules prévus (un fichier chacun, à ajouter au fil de la phase 1) :
 *   identite.c  clé ECDSA P-256 de l'observateur, générée au premier démarrage et gardée en NVS
 *   nonce.c     tirage du nonce (esp_fill_random), renouvellement toutes les 45 s, nonce précédent accepté 10 s
 *   ble.c       annonce du service, caractéristiques INFO, NONCE et ATTEST (NimBLE)
 *   verif.c     reconstitution du message signé et vérification ECDSA (mbedTLS)
 *   journal.c   témoignages signés, file en mémoire flash si le réseau est coupé
 *   reseau.c    Wi-Fi, envoi des lots au serveur, réception de la liste des inscrits
 */
#include <stdio.h>
#include "esp_log.h"
#include "nvs_flash.h"
#include "presence_protocole.h"

static const char *TAG = "observateur";

void app_main(void)
{
    ESP_ERROR_CHECK(nvs_flash_init());
    ESP_LOGI(TAG, "PRESENCE observateur %s, salle %d, protocole v%d",
             CONFIG_PRESENCE_NOM_OBSERVATEUR, CONFIG_PRESENCE_SALLE, PRESENCE_VERSION_PROTOCOLE);

    /* Ordre de démarrage prévu :
     * 1. identite_charger_ou_creer();
     * 2. reseau_demarrer();
     * 3. nonce_demarrer();
     * 4. ble_demarrer();      annonce l'état AUCUNE tant qu'aucune séance n'est active
     * 5. journal_demarrer();  envoi des lots toutes les 30 s
     */
}
