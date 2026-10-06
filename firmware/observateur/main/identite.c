/*
 * Identité de l'observateur : paire ECDSA P-256 créée au premier démarrage,
 * clé privée gardée en NVS (espace "presence", clé "cle") au format DER.
 */
#include <string.h>
#include "esp_log.h"
#include "esp_random.h"
#include "nvs.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "mbedtls/pk.h"
#include "mbedtls/ecp.h"
#include "mbedtls/sha256.h"
#include "mbedtls/base64.h"
#include "identite.h"

static const char *TAG = "identite";

#define NVS_ESPACE  "presence"
#define NVS_CLE     "cle"
#define TAILLE_DER  256

static mbedtls_pk_context s_cle;
static SemaphoreHandle_t s_verrou;

/* Générateur aléatoire pour mbedTLS. esp_fill_random est un vrai aléa dès que la radio est active. */
static int alea(void *ctx, unsigned char *buf, size_t lg)
{
    (void)ctx;
    esp_fill_random(buf, lg);
    return 0;
}

static esp_err_t charger(nvs_handle_t h)
{
    uint8_t der[TAILLE_DER];
    size_t lg = sizeof(der);
    esp_err_t err = nvs_get_blob(h, NVS_CLE, der, &lg);
    if (err != ESP_OK) {
        return err;
    }
    int ret = mbedtls_pk_parse_key(&s_cle, der, lg, NULL, 0, alea, NULL);
    memset(der, 0, sizeof(der));
    if (ret != 0) {
        ESP_LOGE(TAG, "clé en NVS illisible (-0x%04x)", -ret);
        return ESP_FAIL;
    }
    return ESP_OK;
}

static esp_err_t creer(nvs_handle_t h)
{
    uint8_t der[TAILLE_DER];
    int ret = mbedtls_pk_setup(&s_cle, mbedtls_pk_info_from_type(MBEDTLS_PK_ECKEY));
    if (ret == 0) {
        ret = mbedtls_ecp_gen_key(MBEDTLS_ECP_DP_SECP256R1, mbedtls_pk_ec(s_cle), alea, NULL);
    }
    if (ret != 0) {
        ESP_LOGE(TAG, "génération impossible (-0x%04x)", -ret);
        return ESP_FAIL;
    }
    /* mbedtls_pk_write_key_der écrit à la fin du tampon */
    ret = mbedtls_pk_write_key_der(&s_cle, der, sizeof(der));
    if (ret <= 0) {
        ESP_LOGE(TAG, "encodage DER impossible (-0x%04x)", -ret);
        return ESP_FAIL;
    }
    esp_err_t err = nvs_set_blob(h, NVS_CLE, der + sizeof(der) - ret, (size_t)ret);
    if (err == ESP_OK) {
        err = nvs_commit(h);
    }
    memset(der, 0, sizeof(der));
    if (err == ESP_OK) {
        ESP_LOGW(TAG, "nouvelle clé créée et enregistrée");
    }
    return err;
}

static void afficher_cle_publique(void)
{
    uint8_t der[128];
    char b64[180];
    uint8_t condensat[32];
    int lg = mbedtls_pk_write_pubkey_der(&s_cle, der, sizeof(der));
    if (lg <= 0) {
        ESP_LOGE(TAG, "export de la clé publique impossible (-0x%04x)", -lg);
        return;
    }
    const uint8_t *spki = der + sizeof(der) - lg;
    identite_sha256(spki, (size_t)lg, condensat);
    if (identite_base64(spki, (size_t)lg, b64, sizeof(b64)) < 0) {
        return;
    }
    ESP_LOGI(TAG, "PRESENCE cle publique: %s", b64);
    ESP_LOGI(TAG, "PRESENCE id: %02x%02x%02x%02x%02x%02x%02x%02x",
             condensat[0], condensat[1], condensat[2], condensat[3],
             condensat[4], condensat[5], condensat[6], condensat[7]);
}

esp_err_t identite_init(void)
{
    s_verrou = xSemaphoreCreateMutex();
    if (s_verrou == NULL) {
        return ESP_ERR_NO_MEM;
    }
    mbedtls_pk_init(&s_cle);

    nvs_handle_t h;
    esp_err_t err = nvs_open(NVS_ESPACE, NVS_READWRITE, &h);
    if (err != ESP_OK) {
        return err;
    }
    err = charger(h);
    if (err != ESP_OK) {
        mbedtls_pk_free(&s_cle);
        mbedtls_pk_init(&s_cle);
        err = creer(h);
    }
    nvs_close(h);
    if (err == ESP_OK) {
        afficher_cle_publique();
    }
    return err;
}

esp_err_t identite_signer(const uint8_t condensat[32], uint8_t *sig, size_t *lg_sig)
{
    xSemaphoreTake(s_verrou, portMAX_DELAY);
    int ret = mbedtls_pk_sign(&s_cle, MBEDTLS_MD_SHA256, condensat, 32,
                              sig, IDENTITE_TAILLE_SIG_MAX, lg_sig, alea, NULL);
    xSemaphoreGive(s_verrou);
    if (ret != 0) {
        ESP_LOGE(TAG, "signature impossible (-0x%04x)", -ret);
        return ESP_FAIL;
    }
    return ESP_OK;
}

void identite_sha256(const void *donnees, size_t lg, uint8_t condensat[32])
{
    mbedtls_sha256((const unsigned char *)donnees, lg, condensat, 0);
}

void identite_sha256_hex(const void *donnees, size_t lg, char hex[65])
{
    static const char chiffres[] = "0123456789abcdef";
    uint8_t c[32];
    identite_sha256(donnees, lg, c);
    for (int i = 0; i < 32; i++) {
        hex[2 * i] = chiffres[c[i] >> 4];
        hex[2 * i + 1] = chiffres[c[i] & 0x0f];
    }
    hex[64] = '\0';
}

int identite_base64(const uint8_t *src, size_t lg, char *dst, size_t taille_dst)
{
    size_t olen = 0;
    if (mbedtls_base64_encode((unsigned char *)dst, taille_dst, &olen, src, lg) != 0) {
        return -1;
    }
    return (int)olen;
}
