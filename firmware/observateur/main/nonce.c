/* Renouvellement du nonce par esp_timer, nonce précédent gardé pendant la période de grâce. */
#include <string.h>
#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "nonce.h"

static const char *TAG = "nonce";

static portMUX_TYPE s_verrou = portMUX_INITIALIZER_UNLOCKED;
static uint8_t s_courant[PRESENCE_TAILLE_NONCE];
static uint8_t s_precedent[PRESENCE_TAILLE_NONCE];
static uint32_t s_numero;
static int64_t s_renouvele_us;
static esp_timer_handle_t s_minuteur;

static void renouveler(void *arg)
{
    uint8_t neuf[PRESENCE_TAILLE_NONCE];
    esp_fill_random(neuf, sizeof(neuf));
    taskENTER_CRITICAL(&s_verrou);
    memcpy(s_precedent, s_courant, sizeof(s_courant));
    memcpy(s_courant, neuf, sizeof(neuf));
    s_numero++;
    s_renouvele_us = esp_timer_get_time();
    uint32_t numero = s_numero;
    taskEXIT_CRITICAL(&s_verrou);
    ESP_LOGD(TAG, "nonce %lu", (unsigned long)numero);
}

esp_err_t nonce_demarrer(void)
{
    renouveler(NULL);
    const esp_timer_create_args_t args = {
        .callback = renouveler,
        .name = "nonce",
    };
    esp_err_t err = esp_timer_create(&args, &s_minuteur);
    if (err == ESP_OK) {
        err = esp_timer_start_periodic(s_minuteur, (uint64_t)PRESENCE_NONCE_PERIODE_S * 1000000ULL);
    }
    return err;
}

void nonce_courant(uint8_t nonce[PRESENCE_TAILLE_NONCE], uint32_t *numero)
{
    taskENTER_CRITICAL(&s_verrou);
    memcpy(nonce, s_courant, PRESENCE_TAILLE_NONCE);
    *numero = s_numero;
    taskEXIT_CRITICAL(&s_verrou);
}

bool nonce_retrouver(uint32_t numero, uint8_t nonce[PRESENCE_TAILLE_NONCE])
{
    bool trouve = false;
    int64_t maintenant = esp_timer_get_time();
    taskENTER_CRITICAL(&s_verrou);
    if (numero == s_numero) {
        memcpy(nonce, s_courant, PRESENCE_TAILLE_NONCE);
        trouve = true;
    } else if (numero + 1 == s_numero && s_numero > 1 &&
               maintenant - s_renouvele_us < (int64_t)PRESENCE_NONCE_GRACE_S * 1000000) {
        memcpy(nonce, s_precedent, PRESENCE_TAILLE_NONCE);
        trouve = true;
    }
    taskEXIT_CRITICAL(&s_verrou);
    return trouve;
}
