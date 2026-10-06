/*
 * Journal des témoignages : file circulaire en RAM, envoyée sur
 * POST /equipements/moi/temoignages. Un lot n'est retiré qu'après une réponse 200.
 * Limite : la file est perdue au redémarrage et ne garde que JOURNAL_CAPACITE éléments.
 */
#include <inttypes.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "cJSON.h"
#include "identite.h"
#include "reseau.h"
#include "journal.h"

static const char *TAG = "journal";

#define CHEMIN_TEMOIGNAGES  "/equipements/moi/temoignages"
#define TAILLE_LOT          20
#define TAILLE_ELEMENT_JSON 400     /* un témoignage encodé, marge comprise */
#define TAILLE_REPONSE      512

static temoignage_t s_file[JOURNAL_CAPACITE];
static uint32_t s_seq_tete;          /* numéro de séquence du plus ancien élément */
static uint32_t s_nb;
static uint32_t s_perdus;
static SemaphoreHandle_t s_verrou;
static TaskHandle_t s_tache;

void journal_ajouter(const temoignage_t *t)
{
    bool perte = false;
    xSemaphoreTake(s_verrou, portMAX_DELAY);
    if (s_nb == JOURNAL_CAPACITE) {
        s_seq_tete++;
        s_nb--;
        s_perdus++;
        perte = true;
    }
    s_file[(s_seq_tete + s_nb) % JOURNAL_CAPACITE] = *t;
    s_nb++;
    uint32_t nb = s_nb;
    uint32_t perdus = s_perdus;
    xSemaphoreGive(s_verrou);

    if (perte && perdus % 16 == 1) {
        ESP_LOGW(TAG, "file pleine, %lu témoignages perdus depuis le démarrage", (unsigned long)perdus);
    }
    if (nb >= TAILLE_LOT && s_tache != NULL) {
        xTaskNotifyGive(s_tache);
    }
}

/* Encode un témoignage en JSON à la position p. Renvoie le nombre d'octets écrits ou -1. */
static int encoder(const temoignage_t *t, char *p, size_t place)
{
    char nonce[32], trame[128], sig[100];
    if (identite_base64(t->nonce, sizeof(t->nonce), nonce, sizeof(nonce)) < 0 ||
        identite_base64(t->trame, t->lg_trame, trame, sizeof(trame)) < 0 ||
        identite_base64(t->sig, t->lg_sig, sig, sizeof(sig)) < 0) {
        return -1;
    }
    int n = snprintf(p, place,
                     "{\"seance\":%" PRIu32 ",\"nonce\":\"%s\",\"numero_nonce\":%" PRIu32
                     ",\"recu_ms\":%" PRIu64 ",\"trame\":\"%s\",\"sig\":\"%s\"}",
                     t->seance, nonce, t->numero_nonce, t->recu_ms, trame, sig);
    return (n < 0 || (size_t)n >= place) ? -1 : n;
}

/* Envoie au plus TAILLE_LOT témoignages. Renvoie le nombre retiré de la file, ou -1 en cas d'échec. */
static int envoyer_lot(char *corps, size_t taille, char *reponse)
{
    static temoignage_t lot[TAILLE_LOT];
    xSemaphoreTake(s_verrou, portMAX_DELAY);
    uint32_t seq = s_seq_tete;
    uint32_t nb = s_nb < TAILLE_LOT ? s_nb : TAILLE_LOT;
    for (uint32_t i = 0; i < nb; i++) {
        lot[i] = s_file[(seq + i) % JOURNAL_CAPACITE];
    }
    xSemaphoreGive(s_verrou);
    if (nb == 0) {
        return 0;
    }

    size_t pos = (size_t)snprintf(corps, taille, "{\"temoignages\":[");
    for (uint32_t i = 0; i < nb; i++) {
        if (i > 0 && pos + 1 < taille) {
            corps[pos++] = ',';
        }
        int n = encoder(&lot[i], corps + pos, taille - pos);
        if (n < 0) {
            ESP_LOGE(TAG, "encodage du lot impossible");
            return -1;
        }
        pos += (size_t)n;
    }
    snprintf(corps + pos, taille - pos, "]}");

    int statut = reseau_requete("POST", CHEMIN_TEMOIGNAGES, corps, true, reponse, TAILLE_REPONSE);
    if (statut != 200) {
        ESP_LOGW(TAG, "envoi de %lu témoignages : statut %d, nouvel essai plus tard",
                 (unsigned long)nb, statut);
        return -1;
    }

    /* Retire les éléments envoyés, sauf s'ils ont déjà été écrasés pendant l'envoi */
    xSemaphoreTake(s_verrou, portMAX_DELAY);
    uint32_t fin = seq + nb;
    while (s_nb > 0 && (int32_t)(fin - s_seq_tete) > 0) {
        s_seq_tete++;
        s_nb--;
    }
    uint32_t restants = s_nb;
    xSemaphoreGive(s_verrou);

    cJSON *json = cJSON_Parse(reponse);
    const cJSON *acceptes = cJSON_GetObjectItemCaseSensitive(json, "acceptes");
    const cJSON *rejetes = cJSON_GetObjectItemCaseSensitive(json, "rejetes");
    ESP_LOGI(TAG, "lot envoyé : %lu témoignages, %d acceptés, %d rejetés, %lu en attente",
             (unsigned long)nb, cJSON_IsNumber(acceptes) ? acceptes->valueint : -1,
             cJSON_IsArray(rejetes) ? cJSON_GetArraySize(rejetes) : -1, (unsigned long)restants);
    cJSON_Delete(json);
    return (int)nb;
}

static void tache_envoi(void *arg)
{
    const size_t taille = 32 + TAILLE_LOT * TAILLE_ELEMENT_JSON;
    char *corps = malloc(taille);
    char *reponse = malloc(TAILLE_REPONSE);
    if (corps == NULL || reponse == NULL) {
        ESP_LOGE(TAG, "mémoire insuffisante, envoi désactivé");
        free(corps);
        free(reponse);
        vTaskDelete(NULL);
        return;
    }
    for (;;) {
        ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(PRESENCE_ENVOI_LOT_S * 1000));
        if (!reseau_heure_valide()) {
            continue;
        }
        /* Vide la file par lots tant que le serveur accepte */
        while (envoyer_lot(corps, taille, reponse) == TAILLE_LOT) {
        }
    }
}

esp_err_t journal_demarrer(void)
{
    s_verrou = xSemaphoreCreateMutex();
    if (s_verrou == NULL) {
        return ESP_ERR_NO_MEM;
    }
    if (xTaskCreate(tache_envoi, "journal", 6144, NULL, 3, &s_tache) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }
    return ESP_OK;
}
