/*
 * Configuration de l'observateur, lue toutes les 10 s sur GET /equipements/moi/config.
 * La liste des clés n'est reconstruite que si le tableau "cles" a changé ; les derniers
 * compteurs des appareils toujours présents sont conservés.
 */
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "mbedtls/pk.h"
#include "mbedtls/base64.h"
#include "cJSON.h"
#include "presence_protocole.h"
#include "identite.h"
#include "reseau.h"
#include "ble.h"
#include "config.h"

static const char *TAG = "config";

#define CHEMIN_CONFIG           "/equipements/moi/config"
#define PERIODE_MS              10000
#define DUREE_CLOTURE_US        (60 * 1000000LL)  /* état CLOTUREE annoncé 60 s, puis AUCUNE */
#define TAILLE_REPONSE          16384

typedef struct {
    uint8_t id[PRESENCE_TAILLE_ID_APPAREIL];
    mbedtls_pk_context cle;
    uint32_t dernier_compteur;
} entree_cle_t;

static SemaphoreHandle_t s_verrou;
static config_etat_t s_etat;
static entree_cle_t *s_cles;
static size_t s_nb_cles;
static uint8_t s_empreinte_cles[32];
static bool s_empreinte_valide;
static int64_t s_cloture_depuis_us;
static char s_reponse[TAILLE_REPONSE];

static bool lire_hex(const char *hex, uint8_t *out, size_t lg)
{
    if (hex == NULL || strlen(hex) != 2 * lg) {
        return false;
    }
    for (size_t i = 0; i < 2 * lg; i++) {
        char c = hex[i];
        int v;
        if (c >= '0' && c <= '9') {
            v = c - '0';
        } else if (c >= 'a' && c <= 'f') {
            v = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
            v = c - 'A' + 10;
        } else {
            return false;
        }
        if (i % 2 == 0) {
            out[i / 2] = (uint8_t)(v << 4);
        } else {
            out[i / 2] |= (uint8_t)v;
        }
    }
    return true;
}

static void liberer_cles(entree_cle_t *cles, size_t nb)
{
    for (size_t i = 0; i < nb; i++) {
        mbedtls_pk_free(&cles[i].cle);
    }
    free(cles);
}

/* Construit une nouvelle liste à partir du tableau JSON. Les entrées invalides sont ignorées. */
static entree_cle_t *charger_cles(const cJSON *tableau, size_t *nb)
{
    size_t total = (size_t)cJSON_GetArraySize(tableau);
    if (total > NB_CLES_MAX) {
        ESP_LOGW(TAG, "%u clés reçues, seules les %d premières sont gardées", (unsigned)total, NB_CLES_MAX);
        total = NB_CLES_MAX;
    }
    *nb = 0;
    if (total == 0) {
        return NULL;
    }
    entree_cle_t *cles = calloc(total, sizeof(entree_cle_t));
    if (cles == NULL) {
        ESP_LOGE(TAG, "mémoire insuffisante pour %u clés", (unsigned)total);
        return NULL;
    }
    const cJSON *el;
    cJSON_ArrayForEach(el, tableau) {
        if (*nb >= total) {
            break;
        }
        const cJSON *id = cJSON_GetObjectItemCaseSensitive(el, "id");
        const cJSON *cle = cJSON_GetObjectItemCaseSensitive(el, "cle");
        entree_cle_t *e = &cles[*nb];
        uint8_t der[160];
        size_t lg_der = 0;
        if (!cJSON_IsString(id) || !cJSON_IsString(cle) ||
            !lire_hex(id->valuestring, e->id, sizeof(e->id)) ||
            mbedtls_base64_decode(der, sizeof(der), &lg_der,
                                  (const unsigned char *)cle->valuestring,
                                  strlen(cle->valuestring)) != 0) {
            ESP_LOGW(TAG, "entrée de clé illisible ignorée");
            continue;
        }
        mbedtls_pk_init(&e->cle);
        if (mbedtls_pk_parse_public_key(&e->cle, der, lg_der) != 0) {
            ESP_LOGW(TAG, "clé %s refusée", id->valuestring);
            mbedtls_pk_free(&e->cle);
            continue;
        }
        (*nb)++;
    }
    return cles;
}

/* Remplace la liste des clés si le tableau "cles" a changé. */
static void maj_cles(const cJSON *tableau)
{
    char *texte = cJSON_PrintUnformatted(tableau);
    if (texte == NULL) {
        return;
    }
    uint8_t empreinte[32];
    identite_sha256(texte, strlen(texte), empreinte);
    cJSON_free(texte);
    if (s_empreinte_valide && memcmp(empreinte, s_empreinte_cles, sizeof(empreinte)) == 0) {
        return;
    }

    size_t nb = 0;
    entree_cle_t *neuves = charger_cles(tableau, &nb);
    if (neuves == NULL && cJSON_GetArraySize(tableau) > 0) {
        return;   /* échec d'allocation : on garde l'ancienne liste et on réessaiera */
    }

    xSemaphoreTake(s_verrou, portMAX_DELAY);
    for (size_t i = 0; i < nb; i++) {
        for (size_t j = 0; j < s_nb_cles; j++) {
            if (memcmp(neuves[i].id, s_cles[j].id, PRESENCE_TAILLE_ID_APPAREIL) == 0) {
                neuves[i].dernier_compteur = s_cles[j].dernier_compteur;
                break;
            }
        }
    }
    entree_cle_t *anciennes = s_cles;
    size_t nb_anciennes = s_nb_cles;
    s_cles = neuves;
    s_nb_cles = nb;
    xSemaphoreGive(s_verrou);

    liberer_cles(anciennes, nb_anciennes);
    memcpy(s_empreinte_cles, empreinte, sizeof(empreinte));
    s_empreinte_valide = true;
    ESP_LOGI(TAG, "liste des clés mise à jour : %u appareils", (unsigned)nb);
}

static uint8_t etat_depuis_texte(const char *texte)
{
    if (texte == NULL) {
        return PRESENCE_ETAT_AUCUNE;
    }
    if (strcmp(texte, "ACTIVE") == 0) {
        return PRESENCE_ETAT_ACTIVE;
    }
    if (strcmp(texte, "PAUSE") == 0) {
        return PRESENCE_ETAT_PAUSE;
    }
    if (strcmp(texte, "CLOTUREE") == 0 || strcmp(texte, "SCELLEE") == 0) {
        return PRESENCE_ETAT_CLOTUREE;
    }
    return PRESENCE_ETAT_AUCUNE;
}

static void appliquer(const cJSON *json)
{
    config_etat_t neuf;
    config_lire(&neuf);

    const cJSON *salle = cJSON_GetObjectItemCaseSensitive(json, "salle");
    if (cJSON_IsNumber(salle) && salle->valuedouble >= 0 && salle->valuedouble <= 0xFFFF) {
        neuf.salle = (uint16_t)salle->valuedouble;
    }

    neuf.seance = 0;
    neuf.etat = PRESENCE_ETAT_AUCUNE;
    const cJSON *seance = cJSON_GetObjectItemCaseSensitive(json, "seance");
    if (cJSON_IsObject(seance)) {
        const cJSON *numero = cJSON_GetObjectItemCaseSensitive(seance, "numero");
        const cJSON *etat = cJSON_GetObjectItemCaseSensitive(seance, "etat");
        if (cJSON_IsNumber(numero) && numero->valuedouble > 0) {
            neuf.seance = (uint32_t)numero->valuedouble;
        }
        neuf.etat = etat_depuis_texte(cJSON_IsString(etat) ? etat->valuestring : NULL);
    }
    if (neuf.etat == PRESENCE_ETAT_CLOTUREE) {
        int64_t maintenant = esp_timer_get_time();
        if (s_cloture_depuis_us == 0) {
            s_cloture_depuis_us = maintenant;
        } else if (maintenant - s_cloture_depuis_us > DUREE_CLOTURE_US) {
            neuf.etat = PRESENCE_ETAT_AUCUNE;
            neuf.seance = 0;
        }
    } else {
        s_cloture_depuis_us = 0;
    }

    const cJSON *cles = cJSON_GetObjectItemCaseSensitive(json, "cles");
    if (cJSON_IsArray(cles)) {
        maj_cles(cles);
    }

    xSemaphoreTake(s_verrou, portMAX_DELAY);
    bool annonce_changee = neuf.salle != s_etat.salle || neuf.etat != s_etat.etat;
    bool change = annonce_changee || neuf.seance != s_etat.seance;
    s_etat = neuf;
    xSemaphoreGive(s_verrou);

    if (change) {
        ESP_LOGI(TAG, "salle %u, séance %lu, état %u",
                 neuf.salle, (unsigned long)neuf.seance, neuf.etat);
    }
    if (annonce_changee) {
        ble_actualiser_annonce();
    }
}

static void tache_config(void *arg)
{
    int dernier_statut = 0;
    for (;;) {
        if (reseau_heure_valide()) {
            int statut = reseau_requete("GET", CHEMIN_CONFIG, NULL, true, s_reponse, sizeof(s_reponse));
            if (statut == 200) {
                cJSON *json = cJSON_Parse(s_reponse);
                if (json != NULL) {
                    appliquer(json);
                    cJSON_Delete(json);
                } else {
                    ESP_LOGW(TAG, "configuration illisible (%u octets)", (unsigned)strlen(s_reponse));
                }
            } else if (statut != dernier_statut) {
                ESP_LOGW(TAG, "GET " CHEMIN_CONFIG " : statut %d%s", statut,
                         statut == 401 || statut == 403 ? " (observateur enregistré ?)" : "");
            }
            dernier_statut = statut;
        }
        vTaskDelay(pdMS_TO_TICKS(PERIODE_MS));
    }
}

esp_err_t config_init(void)
{
    s_verrou = xSemaphoreCreateMutex();
    if (s_verrou == NULL) {
        return ESP_ERR_NO_MEM;
    }
    s_etat.salle = (uint16_t)CONFIG_PRESENCE_SALLE;
    s_etat.seance = 0;
    s_etat.etat = PRESENCE_ETAT_AUCUNE;
    if (MODE_TEST) {
        s_etat.seance = 1;
        s_etat.etat = PRESENCE_ETAT_ACTIVE;
        ESP_LOGW(TAG, "MODE TEST : configuration du serveur ignorée, séance 1 active, salle %u",
                 s_etat.salle);
    }
    return ESP_OK;
}

esp_err_t config_demarrer(void)
{
    if (MODE_TEST) {
        return ESP_OK;
    }
    return xTaskCreate(tache_config, "config", 6144, NULL, 4, NULL) == pdPASS ? ESP_OK : ESP_ERR_NO_MEM;
}

void config_lire(config_etat_t *etat)
{
    xSemaphoreTake(s_verrou, portMAX_DELAY);
    *etat = s_etat;
    xSemaphoreGive(s_verrou);
}

config_verif_t config_verifier(const uint8_t id[8], const uint8_t condensat[32],
                               const uint8_t *sig, size_t lg_sig, uint32_t compteur)
{
    config_verif_t res = VERIF_ID_INCONNU;
    xSemaphoreTake(s_verrou, portMAX_DELAY);
    for (size_t i = 0; i < s_nb_cles; i++) {
        entree_cle_t *e = &s_cles[i];
        if (memcmp(e->id, id, PRESENCE_TAILLE_ID_APPAREIL) != 0) {
            continue;
        }
        if (mbedtls_pk_verify(&e->cle, MBEDTLS_MD_SHA256, condensat, 32, sig, lg_sig) != 0) {
            res = VERIF_SIGNATURE;
        } else if (compteur <= e->dernier_compteur) {
            res = VERIF_COMPTEUR;
        } else {
            e->dernier_compteur = compteur;
            res = VERIF_OK;
        }
        break;
    }
    xSemaphoreGive(s_verrou);
    return res;
}
