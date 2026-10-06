/*
 * Vérification des attestations (SPEC-v0.1 section 4) et production des témoignages.
 * Le rappel NimBLE ne fait que copier la trame ; la cryptographie tourne dans cette tâche.
 */
#include <stdio.h>
#include <string.h>
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "presence_protocole.h"
#include "identite.h"
#include "reseau.h"
#include "nonce.h"
#include "config.h"
#include "journal.h"
#include "ble.h"
#include "led.h"
#include "attestation.h"

static const char *TAG = "attestation";

#define LONGUEUR_FILE   16
#define LG_ETIQUETTE    (sizeof(PRESENCE_ETIQUETTE_ATTEST) - 1)   /* identique pour TEMOIN */

typedef struct {
    uint16_t connexion;
    uint8_t lg;
    uint64_t recu_ms;
    uint8_t trame[PRESENCE_TAILLE_ATTESTATION_MAX];
} element_t;

static QueueHandle_t s_file;

static void id_en_hex(const uint8_t *id, char hex[17])
{
    for (int i = 0; i < PRESENCE_TAILLE_ID_APPAREIL; i++) {
        snprintf(hex + 2 * i, 3, "%02x", id[i]);
    }
}

static void rejeter(const char *id, uint32_t compteur, uint32_t numero, const char *raison)
{
    ESP_LOGW(TAG, "attestation rejetée id=%s compteur=%lu numero_nonce=%lu raison=%s",
             id, (unsigned long)compteur, (unsigned long)numero, raison);
}

/* Mode test (expérience 01) : aucune clé connue, la trame est seulement décrite. */
static void decrire(const element_t *e, const char *id, uint32_t compteur, uint32_t numero)
{
    uint8_t nonce[PRESENCE_TAILLE_NONCE];
    uint32_t numero_courant;
    nonce_courant(nonce, &numero_courant);
    const char *etat_nonce = numero == numero_courant ? "courant"
                             : nonce_retrouver(numero, nonce) ? "precedent" : "inconnu";
    int8_t rssi = 0;
    char texte_rssi[8] = "inconnu";
    if (ble_rssi(e->connexion, &rssi)) {
        snprintf(texte_rssi, sizeof(texte_rssi), "%d", rssi);
    }
    ESP_LOGI(TAG, "trame reçue id=%s compteur=%lu numero_nonce=%lu (%s) longueur=%u rssi=%s",
             id, (unsigned long)compteur, (unsigned long)numero, etat_nonce, e->lg, texte_rssi);
    led_eclat();
}

static void traiter(const element_t *e)
{
    const uint8_t *trame = e->trame;
    const uint8_t *sig = trame + PRESENCE_TAILLE_ENTETE_ATTEST;
    size_t lg_sig = e->lg - PRESENCE_TAILLE_ENTETE_ATTEST;
    uint32_t compteur = presence_lire_u32(trame + PRESENCE_TAILLE_ID_APPAREIL);
    uint32_t numero = presence_lire_u32(trame + PRESENCE_TAILLE_ID_APPAREIL + 4);
    char id[17];
    id_en_hex(trame, id);

    if (MODE_TEST) {
        decrire(e, id, compteur, numero);
        return;
    }

    int64_t debut = esp_timer_get_time();
    uint8_t nonce[PRESENCE_TAILLE_NONCE];
    if (!nonce_retrouver(numero, nonce)) {
        rejeter(id, compteur, numero, "nonce_perime");
        return;
    }
    config_etat_t etat;
    config_lire(&etat);

    /* m = "PRESENCE/v0.1/ATTEST" | salle | seance | nonce | compteur */
    uint8_t m[LG_ETIQUETTE + 2 + 4 + PRESENCE_TAILLE_NONCE + 4];
    uint8_t *p = m;
    memcpy(p, PRESENCE_ETIQUETTE_ATTEST, LG_ETIQUETTE);
    p += LG_ETIQUETTE;
    presence_ecrire_u16(p, etat.salle);
    p += 2;
    presence_ecrire_u32(p, etat.seance);
    p += 4;
    memcpy(p, nonce, PRESENCE_TAILLE_NONCE);
    p += PRESENCE_TAILLE_NONCE;
    presence_ecrire_u32(p, compteur);

    uint8_t condensat[32];
    identite_sha256(m, sizeof(m), condensat);
    switch (config_verifier(trame, condensat, sig, lg_sig, compteur)) {
    case VERIF_OK:
        break;
    case VERIF_ID_INCONNU:
        rejeter(id, compteur, numero, "id_inconnu");
        return;
    case VERIF_SIGNATURE:
        rejeter(id, compteur, numero, "signature");
        return;
    case VERIF_COMPTEUR:
        rejeter(id, compteur, numero, "compteur");
        return;
    }

    /* Témoignage : "PRESENCE/v0.1/TEMOIN" | salle | seance | nonce | numero_nonce | recu_ms | trame */
    uint8_t t[LG_ETIQUETTE + 2 + 4 + PRESENCE_TAILLE_NONCE + 4 + 8 + PRESENCE_TAILLE_ATTESTATION_MAX];
    p = t;
    memcpy(p, PRESENCE_ETIQUETTE_TEMOIN, LG_ETIQUETTE);
    p += LG_ETIQUETTE;
    presence_ecrire_u16(p, etat.salle);
    p += 2;
    presence_ecrire_u32(p, etat.seance);
    p += 4;
    memcpy(p, nonce, PRESENCE_TAILLE_NONCE);
    p += PRESENCE_TAILLE_NONCE;
    presence_ecrire_u32(p, numero);
    p += 4;
    presence_ecrire_u64(p, e->recu_ms);
    p += 8;
    memcpy(p, trame, e->lg);
    p += e->lg;

    temoignage_t tem = {
        .seance = etat.seance,
        .numero_nonce = numero,
        .recu_ms = e->recu_ms,
        .lg_trame = e->lg,
    };
    memcpy(tem.nonce, nonce, sizeof(tem.nonce));
    memcpy(tem.trame, trame, e->lg);
    size_t lg_tem_sig = 0;
    identite_sha256(t, (size_t)(p - t), condensat);
    if (identite_signer(condensat, tem.sig, &lg_tem_sig) != ESP_OK) {
        rejeter(id, compteur, numero, "signature_temoin");
        return;
    }
    tem.lg_sig = (uint8_t)lg_tem_sig;
    journal_ajouter(&tem);
    led_eclat();
    ESP_LOGI(TAG, "attestation acceptée id=%s compteur=%lu numero_nonce=%lu seance=%lu traitement=%lldms",
             id, (unsigned long)compteur, (unsigned long)numero, (unsigned long)etat.seance,
             (long long)((esp_timer_get_time() - debut) / 1000));
}

static void tache_attestation(void *arg)
{
    static element_t e;
    for (;;) {
        if (xQueueReceive(s_file, &e, portMAX_DELAY) == pdTRUE) {
            traiter(&e);
        }
    }
}

esp_err_t attestation_demarrer(void)
{
    _Static_assert(sizeof(PRESENCE_ETIQUETTE_ATTEST) == sizeof(PRESENCE_ETIQUETTE_TEMOIN),
                   "les deux étiquettes doivent avoir la même longueur");
    s_file = xQueueCreate(LONGUEUR_FILE, sizeof(element_t));
    if (s_file == NULL) {
        return ESP_ERR_NO_MEM;
    }
    if (xTaskCreate(tache_attestation, "attestation", 8192, NULL, 5, NULL) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }
    return ESP_OK;
}

esp_err_t attestation_soumettre(uint16_t connexion, const uint8_t *trame, size_t lg)
{
    if (lg < PRESENCE_TAILLE_ATTESTATION_MIN || lg > PRESENCE_TAILLE_ATTESTATION_MAX) {
        return ESP_ERR_INVALID_SIZE;
    }
    element_t e = {
        .connexion = connexion,
        .lg = (uint8_t)lg,
        .recu_ms = reseau_maintenant_ms(),
    };
    memcpy(e.trame, trame, lg);
    return xQueueSend(s_file, &e, 0) == pdTRUE ? ESP_OK : ESP_ERR_NO_MEM;
}
