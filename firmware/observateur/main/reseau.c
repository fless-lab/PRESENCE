/*
 * Réseau : Wi-Fi station avec reconnexion automatique, réglage de l'heure sur GET /heure,
 * requêtes HTTP signées selon protocole/API.md section 2.
 */
#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <sys/time.h>
#include "esp_log.h"
#include "esp_wifi.h"
#include "esp_netif.h"
#include "esp_event.h"
#include "esp_timer.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/task.h"
#include "cJSON.h"
#include "presence_protocole.h"
#include "identite.h"
#include "reseau.h"

static const char *TAG = "reseau";

#define BIT_CONNECTE            BIT0
#define SYNCHRO_PERIODE_MS      (10 * 60 * 1000)
#define SYNCHRO_REESSAI_MS      5000
#define DELAI_HTTP_MS           8000
#define ID_EQUIPEMENT           "equ:" CONFIG_PRESENCE_NOM_OBSERVATEUR

static EventGroupHandle_t s_evenements;
static volatile bool s_heure_valide;
static uint32_t s_tentatives;

typedef struct {
    char *buf;
    size_t taille;
    size_t lg;
} tampon_reponse_t;

static void sur_evenement(void *arg, esp_event_base_t base, int32_t id, void *donnees)
{
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_START) {
        esp_wifi_connect();
    } else if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        xEventGroupClearBits(s_evenements, BIT_CONNECTE);
        const wifi_event_sta_disconnected_t *d = donnees;
        if (s_tentatives++ % 10 == 0) {
            ESP_LOGW(TAG, "Wi-Fi déconnecté (raison %d), nouvelle tentative", d->reason);
        }
        esp_wifi_connect();
    } else if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        const ip_event_got_ip_t *e = donnees;
        ESP_LOGI(TAG, "connecté, adresse " IPSTR, IP2STR(&e->ip_info.ip));
        s_tentatives = 0;
        xEventGroupSetBits(s_evenements, BIT_CONNECTE);
    }
}

static esp_err_t sur_http(esp_http_client_event_t *evt)
{
    tampon_reponse_t *t = evt->user_data;
    if (evt->event_id == HTTP_EVENT_ON_DATA && t != NULL && t->buf != NULL && evt->data_len > 0) {
        size_t place = t->taille - 1 - t->lg;
        size_t n = (size_t)evt->data_len < place ? (size_t)evt->data_len : place;
        memcpy(t->buf + t->lg, evt->data, n);
        t->lg += n;
        t->buf[t->lg] = '\0';
    }
    return ESP_OK;
}

static esp_err_t ajouter_signature(esp_http_client_handle_t client, const char *methode,
                                   const char *chemin, const char *corps)
{
    char hex[65];
    char horodatage[24];
    char chaine[256];
    uint8_t condensat[32];
    uint8_t sig[IDENTITE_TAILLE_SIG_MAX];
    size_t lg_sig = 0;
    char sig_b64[100];

    identite_sha256_hex(corps ? corps : "", corps ? strlen(corps) : 0, hex);
    snprintf(horodatage, sizeof(horodatage), "%" PRIu64, reseau_maintenant_ms());
    int n = snprintf(chaine, sizeof(chaine), PRESENCE_ETIQUETTE_REQ "\n%s\n%s\n%s\n%s",
                     methode, chemin, horodatage, hex);
    if (n < 0 || n >= (int)sizeof(chaine)) {
        return ESP_ERR_INVALID_SIZE;
    }
    identite_sha256(chaine, (size_t)n, condensat);
    esp_err_t err = identite_signer(condensat, sig, &lg_sig);
    if (err != ESP_OK) {
        return err;
    }
    if (identite_base64(sig, lg_sig, sig_b64, sizeof(sig_b64)) < 0) {
        return ESP_ERR_INVALID_SIZE;
    }
    esp_http_client_set_header(client, "X-Presence-Id", ID_EQUIPEMENT);
    esp_http_client_set_header(client, "X-Presence-Horodatage", horodatage);
    esp_http_client_set_header(client, "X-Presence-Signature", sig_b64);
    return ESP_OK;
}

int reseau_requete(const char *methode, const char *chemin, const char *corps, bool signer,
                   char *reponse, size_t taille)
{
    if (!reseau_connecte() || (signer && !s_heure_valide) || reponse == NULL || taille == 0) {
        return -1;
    }
    char url[160];
    snprintf(url, sizeof(url), "%s%s", CONFIG_PRESENCE_URL_SERVEUR, chemin);
    reponse[0] = '\0';
    tampon_reponse_t t = { .buf = reponse, .taille = taille, .lg = 0 };
    bool post = strcmp(methode, "POST") == 0;

    esp_http_client_config_t cfg = {
        .url = url,
        .method = post ? HTTP_METHOD_POST : HTTP_METHOD_GET,
        .event_handler = sur_http,
        .user_data = &t,
        .timeout_ms = DELAI_HTTP_MS,
        .crt_bundle_attach = esp_crt_bundle_attach,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (client == NULL) {
        return -1;
    }
    int statut = -1;
    if (signer && ajouter_signature(client, methode, chemin, corps) != ESP_OK) {
        esp_http_client_cleanup(client);
        return -1;
    }
    if (corps != NULL) {
        esp_http_client_set_header(client, "Content-Type", "application/json");
        esp_http_client_set_post_field(client, corps, (int)strlen(corps));
    }
    esp_err_t err = esp_http_client_perform(client);
    if (err == ESP_OK) {
        statut = esp_http_client_get_status_code(client);
    } else {
        ESP_LOGW(TAG, "%s %s : %s", methode, chemin, esp_err_to_name(err));
    }
    esp_http_client_cleanup(client);
    return statut;
}

/* GET /heure, corrigé de la moitié du temps d'aller-retour. */
static bool synchroniser_heure(void)
{
    char reponse[96];
    int64_t debut = esp_timer_get_time();
    int statut = reseau_requete("GET", "/heure", NULL, false, reponse, sizeof(reponse));
    int64_t aller_retour_us = esp_timer_get_time() - debut;
    if (statut != 200) {
        ESP_LOGW(TAG, "GET /heure : statut %d", statut);
        return false;
    }
    cJSON *json = cJSON_Parse(reponse);
    const cJSON *ms = cJSON_GetObjectItemCaseSensitive(json, "ms");
    bool ok = cJSON_IsNumber(ms) && ms->valuedouble > 0;
    if (ok) {
        uint64_t t = (uint64_t)ms->valuedouble + (uint64_t)(aller_retour_us / 2000);
        struct timeval tv = {
            .tv_sec = (time_t)(t / 1000),
            .tv_usec = (suseconds_t)((t % 1000) * 1000),
        };
        settimeofday(&tv, NULL);
        s_heure_valide = true;
        ESP_LOGI(TAG, "heure réglée : %" PRIu64 " ms (aller-retour %" PRId64 " ms)",
                 t, aller_retour_us / 1000);
    } else {
        ESP_LOGW(TAG, "réponse /heure invalide");
    }
    cJSON_Delete(json);
    return ok;
}

static void tache_heure(void *arg)
{
    for (;;) {
        xEventGroupWaitBits(s_evenements, BIT_CONNECTE, pdFALSE, pdTRUE, portMAX_DELAY);
        bool ok = synchroniser_heure();
        vTaskDelay(pdMS_TO_TICKS(ok ? SYNCHRO_PERIODE_MS : SYNCHRO_REESSAI_MS));
    }
}

esp_err_t reseau_demarrer(void)
{
    s_evenements = xEventGroupCreate();
    if (s_evenements == NULL) {
        return ESP_ERR_NO_MEM;
    }
    esp_netif_create_default_wifi_sta();
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT, ESP_EVENT_ANY_ID,
                                                        sur_evenement, NULL, NULL));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(IP_EVENT, IP_EVENT_STA_GOT_IP,
                                                        sur_evenement, NULL, NULL));
    wifi_config_t wc = { 0 };
    strlcpy((char *)wc.sta.ssid, CONFIG_PRESENCE_WIFI_SSID, sizeof(wc.sta.ssid));
    strlcpy((char *)wc.sta.password, CONFIG_PRESENCE_WIFI_MDP, sizeof(wc.sta.password));
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wc));
    ESP_ERROR_CHECK(esp_wifi_start());
    ESP_LOGI(TAG, "Wi-Fi démarré, réseau \"%s\", serveur %s",
             CONFIG_PRESENCE_WIFI_SSID, CONFIG_PRESENCE_URL_SERVEUR);

    if (xTaskCreate(tache_heure, "heure", 4096, NULL, 4, NULL) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }
    return ESP_OK;
}

bool reseau_connecte(void)
{
    return s_evenements != NULL && (xEventGroupGetBits(s_evenements) & BIT_CONNECTE) != 0;
}

bool reseau_heure_valide(void)
{
    return s_heure_valide;
}

uint64_t reseau_maintenant_ms(void)
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (uint64_t)tv.tv_sec * 1000u + (uint64_t)tv.tv_usec / 1000u;
}
