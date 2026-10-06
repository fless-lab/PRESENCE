/*
 * Bluetooth Low Energy avec NimBLE : annonce connectable du service PRESENCE,
 * caractéristiques INFO (lecture), NONCE (lecture) et ATTEST (écriture).
 * Toutes les manipulations de l'annonce se font dans la tâche hôte NimBLE.
 */
#include <stdbool.h>
#include <string.h>
#include "esp_log.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/util/util.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include "presence_protocole.h"
#include "config.h"
#include "nonce.h"
#include "attestation.h"
#include "ble.h"

static const char *TAG = "ble";

#define NOM_APPAREIL "PRESENCE-" CONFIG_PRESENCE_NOM_OBSERVATEUR

enum { CAR_INFO = 1, CAR_NONCE, CAR_ATTEST };

static const ble_uuid128_t UUID_SERVICE = BLE_UUID128_INIT(PRESENCE_UUID_SERVICE_LE);
static const ble_uuid128_t UUID_INFO = BLE_UUID128_INIT(PRESENCE_UUID_INFO_LE);
static const ble_uuid128_t UUID_NONCE = BLE_UUID128_INIT(PRESENCE_UUID_NONCE_LE);
static const ble_uuid128_t UUID_ATTEST = BLE_UUID128_INIT(PRESENCE_UUID_ATTEST_LE);

static uint8_t s_type_adresse;
static bool s_synchro;
static struct ble_npl_event s_ev_annonce;

static int sur_acces(uint16_t connexion, uint16_t attribut, struct ble_gatt_access_ctxt *ctxt, void *arg);

static const struct ble_gatt_chr_def s_caracteristiques[] = {
    {
        .uuid = &UUID_INFO.u,
        .access_cb = sur_acces,
        .arg = (void *)CAR_INFO,
        .flags = BLE_GATT_CHR_F_READ,
    },
    {
        .uuid = &UUID_NONCE.u,
        .access_cb = sur_acces,
        .arg = (void *)CAR_NONCE,
        .flags = BLE_GATT_CHR_F_READ,
    },
    {
        .uuid = &UUID_ATTEST.u,
        .access_cb = sur_acces,
        .arg = (void *)CAR_ATTEST,
        .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_NO_RSP,
    },
    { 0 },
};

static const struct ble_gatt_svc_def s_services[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &UUID_SERVICE.u,
        .characteristics = s_caracteristiques,
    },
    { 0 },
};

static int lire_info(struct os_mbuf *om)
{
    config_etat_t etat;
    config_lire(&etat);
    uint8_t v[PRESENCE_TAILLE_INFO];
    v[0] = PRESENCE_VERSION_PROTOCOLE;
    presence_ecrire_u16(v + 1, etat.salle);
    presence_ecrire_u32(v + 3, etat.seance);
    v[7] = etat.etat;
    return os_mbuf_append(om, v, sizeof(v)) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

static int lire_nonce(struct os_mbuf *om)
{
    uint8_t v[PRESENCE_TAILLE_LECTURE_NONCE];
    uint32_t numero;
    nonce_courant(v, &numero);
    presence_ecrire_u32(v + PRESENCE_TAILLE_NONCE, numero);
    return os_mbuf_append(om, v, sizeof(v)) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

static int ecrire_attest(uint16_t connexion, struct os_mbuf *om)
{
    uint8_t trame[PRESENCE_TAILLE_ATTESTATION_MAX];
    uint16_t lg = OS_MBUF_PKTLEN(om);
    if (lg < PRESENCE_TAILLE_ATTESTATION_MIN || lg > PRESENCE_TAILLE_ATTESTATION_MAX) {
        ESP_LOGW(TAG, "attestation rejetée longueur=%u raison=longueur", lg);
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    }
    if (ble_hs_mbuf_to_flat(om, trame, sizeof(trame), &lg) != 0) {
        return BLE_ATT_ERR_UNLIKELY;
    }
    if (attestation_soumettre(connexion, trame, lg) != ESP_OK) {
        ESP_LOGW(TAG, "attestation rejetée raison=file_pleine");
        return BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    return 0;
}

static int sur_acces(uint16_t connexion, uint16_t attribut, struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    switch ((int)(intptr_t)arg) {
    case CAR_INFO:
        return ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR ? lire_info(ctxt->om) : BLE_ATT_ERR_UNLIKELY;
    case CAR_NONCE:
        return ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR ? lire_nonce(ctxt->om) : BLE_ATT_ERR_UNLIKELY;
    case CAR_ATTEST:
        return ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR ? ecrire_attest(connexion, ctxt->om)
                                                        : BLE_ATT_ERR_UNLIKELY;
    default:
        return BLE_ATT_ERR_UNLIKELY;
    }
}

static int sur_gap(struct ble_gap_event *ev, void *arg);

/* (Re)lance l'annonce avec les données courantes. Tâche hôte uniquement. */
static void annoncer(void)
{
    if (!s_synchro) {
        return;
    }
    if (ble_gap_adv_active()) {
        ble_gap_adv_stop();
    }

    config_etat_t etat;
    config_lire(&etat);
    uint8_t fabricant[PRESENCE_TAILLE_FABRICANT] = {
        PRESENCE_ID_FABRICANT & 0xFF, PRESENCE_ID_FABRICANT >> 8,   /* petit-boutiste sur l'air */
        PRESENCE_VERSION_PROTOCOLE, 0, 0, etat.etat,
    };
    presence_ecrire_u16(fabricant + 3, etat.salle);

    /* Annonce : drapeaux (3) + UUID 128 bits (18) + données fabricant (8) = 29 octets */
    struct ble_hs_adv_fields champs = { 0 };
    champs.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    champs.uuids128 = &UUID_SERVICE;
    champs.num_uuids128 = 1;
    champs.uuids128_is_complete = 1;
    champs.mfg_data = fabricant;
    champs.mfg_data_len = sizeof(fabricant);
    int rc = ble_gap_adv_set_fields(&champs);
    if (rc != 0) {
        ESP_LOGE(TAG, "données d'annonce refusées (%d)", rc);
        return;
    }

    /* Réponse au balayage : le nom */
    const char *nom = ble_svc_gap_device_name();
    struct ble_hs_adv_fields reponse = { 0 };
    size_t lg_nom = strlen(nom);
    reponse.name = (const uint8_t *)nom;
    reponse.name_len = (uint8_t)(lg_nom > 29 ? 29 : lg_nom);
    reponse.name_is_complete = lg_nom <= 29;
    rc = ble_gap_adv_rsp_set_fields(&reponse);
    if (rc != 0) {
        ESP_LOGE(TAG, "réponse au balayage refusée (%d)", rc);
        return;
    }

    struct ble_gap_adv_params params = { 0 };
    params.conn_mode = BLE_GAP_CONN_MODE_UND;
    params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    rc = ble_gap_adv_start(s_type_adresse, NULL, BLE_HS_FOREVER, &params, sur_gap, NULL);
    if (rc != 0) {
        /* Typiquement : nombre maximal de connexions atteint ; relancé à la prochaine déconnexion */
        ESP_LOGD(TAG, "annonce non relancée (%d)", rc);
    }
}

static int sur_gap(struct ble_gap_event *ev, void *arg)
{
    switch (ev->type) {
    case BLE_GAP_EVENT_CONNECT:
        ESP_LOGI(TAG, "connexion %d statut %d", ev->connect.conn_handle, ev->connect.status);
        annoncer();
        break;
    case BLE_GAP_EVENT_DISCONNECT:
        ESP_LOGI(TAG, "déconnexion %d raison 0x%x",
                 ev->disconnect.conn.conn_handle, ev->disconnect.reason);
        annoncer();
        break;
    case BLE_GAP_EVENT_ADV_COMPLETE:
        annoncer();
        break;
    case BLE_GAP_EVENT_MTU:
        ESP_LOGD(TAG, "connexion %d MTU %d", ev->mtu.conn_handle, ev->mtu.value);
        break;
    default:
        break;
    }
    return 0;
}

static void sur_ev_annonce(struct ble_npl_event *ev)
{
    annoncer();
}

static void sur_synchro(void)
{
    int rc = ble_hs_util_ensure_addr(0);
    if (rc == 0) {
        rc = ble_hs_id_infer_auto(0, &s_type_adresse);
    }
    if (rc != 0) {
        ESP_LOGE(TAG, "adresse Bluetooth indisponible (%d)", rc);
        return;
    }
    s_synchro = true;
    ESP_LOGI(TAG, "annonce de %s", ble_svc_gap_device_name());
    annoncer();
}

static void sur_reinit(int raison)
{
    s_synchro = false;
    ESP_LOGW(TAG, "pile BLE réinitialisée (raison %d)", raison);
}

static void tache_hote(void *param)
{
    nimble_port_run();
    nimble_port_freertos_deinit();
}

esp_err_t ble_demarrer(void)
{
    esp_err_t err = nimble_port_init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "nimble_port_init : %s", esp_err_to_name(err));
        return err;
    }
    ble_hs_cfg.sync_cb = sur_synchro;
    ble_hs_cfg.reset_cb = sur_reinit;

    ble_svc_gap_init();
    ble_svc_gatt_init();
    int rc = ble_gatts_count_cfg(s_services);
    if (rc == 0) {
        rc = ble_gatts_add_svcs(s_services);
    }
    if (rc == 0) {
        rc = ble_svc_gap_device_name_set(NOM_APPAREIL);
    }
    if (rc != 0) {
        ESP_LOGE(TAG, "déclaration du service GATT impossible (%d)", rc);
        return ESP_FAIL;
    }
    ble_npl_event_init(&s_ev_annonce, sur_ev_annonce, NULL);
    nimble_port_freertos_init(tache_hote);
    return ESP_OK;
}

void ble_actualiser_annonce(void)
{
    if (s_synchro) {
        ble_npl_eventq_put(nimble_port_get_dflt_eventq(), &s_ev_annonce);
    }
}

bool ble_rssi(uint16_t connexion, int8_t *rssi)
{
    return ble_gap_conn_rssi(connexion, rssi) == 0;
}
