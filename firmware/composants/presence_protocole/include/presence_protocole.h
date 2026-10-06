/*
 * Constantes du protocole PRESENCE v0.1.
 * Source de vérité : protocole/constantes.yaml et protocole/API.md.
 * Toute modification doit y être reportée.
 */
#pragma once

#include <stdint.h>

#define PRESENCE_VERSION_PROTOCOLE      0x01

/* UUID Bluetooth, forme texte */
#define PRESENCE_UUID_SERVICE           "05c39e32-50d8-4214-9d1c-9ecb2c8f9364"
#define PRESENCE_UUID_INFO              "d6bed148-c226-4d44-a6fc-b1c2242f8cce"
#define PRESENCE_UUID_NONCE             "32f15e73-099d-475c-b214-2a497b29ce2d"
#define PRESENCE_UUID_ATTEST            "47ed4924-b297-4e43-8e4c-91b5665dfb9c"

/*
 * Mêmes UUID en octets petit-boutistes (ordre inverse de la forme texte),
 * tels que les attend BLE_UUID128_INIT de NimBLE et tels qu'ils passent sur l'air.
 */
#define PRESENCE_UUID_SERVICE_LE \
    0x64, 0x93, 0x8f, 0x2c, 0xcb, 0x9e, 0x1c, 0x9d, 0x14, 0x42, 0xd8, 0x50, 0x32, 0x9e, 0xc3, 0x05
#define PRESENCE_UUID_INFO_LE \
    0xce, 0x8c, 0x2f, 0x24, 0xc2, 0xb1, 0xfc, 0xa6, 0x44, 0x4d, 0x26, 0xc2, 0x48, 0xd1, 0xbe, 0xd6
#define PRESENCE_UUID_NONCE_LE \
    0x2d, 0xce, 0x29, 0x7b, 0x49, 0x2a, 0x14, 0xb2, 0x5c, 0x47, 0x9d, 0x09, 0x73, 0x5e, 0xf1, 0x32
#define PRESENCE_UUID_ATTEST_LE \
    0x9c, 0xfb, 0x5d, 0x66, 0xb5, 0x91, 0x4c, 0x8e, 0x43, 0x4e, 0x97, 0xb2, 0x24, 0x49, 0xed, 0x47

/* Données fabricant de l'annonce : identifiant réservé aux essais */
#define PRESENCE_ID_FABRICANT           0xFFFF

/* Tailles en octets */
#define PRESENCE_TAILLE_NONCE           16
#define PRESENCE_TAILLE_ID_APPAREIL     8
#define PRESENCE_TAILLE_SIGNATURE_MIN   8
#define PRESENCE_TAILLE_SIGNATURE_MAX   72
#define PRESENCE_TAILLE_ENTETE_ATTEST   (PRESENCE_TAILLE_ID_APPAREIL + 4 + 4)
#define PRESENCE_TAILLE_ATTESTATION_MIN (PRESENCE_TAILLE_ENTETE_ATTEST + PRESENCE_TAILLE_SIGNATURE_MIN)
#define PRESENCE_TAILLE_ATTESTATION_MAX (PRESENCE_TAILLE_ENTETE_ATTEST + PRESENCE_TAILLE_SIGNATURE_MAX)
#define PRESENCE_TAILLE_INFO            8   /* version | salle u16 | seance u32 | etat */
#define PRESENCE_TAILLE_LECTURE_NONCE   (PRESENCE_TAILLE_NONCE + 4)
#define PRESENCE_TAILLE_FABRICANT       6   /* id 0xFFFF | version | salle u16 | etat */

/* Étiquettes de séparation de domaine */
#define PRESENCE_ETIQUETTE_ATTEST       "PRESENCE/v0.1/ATTEST"
#define PRESENCE_ETIQUETTE_TEMOIN       "PRESENCE/v0.1/TEMOIN"
#define PRESENCE_ETIQUETTE_REQ          "PRESENCE/v0.1/REQ"

/* Temps */
#define PRESENCE_NONCE_PERIODE_S        45
#define PRESENCE_NONCE_GRACE_S          10
#define PRESENCE_ENVOI_LOT_S            30

typedef enum {
    PRESENCE_ETAT_AUCUNE   = 0,
    PRESENCE_ETAT_ACTIVE   = 1,
    PRESENCE_ETAT_PAUSE    = 2,
    PRESENCE_ETAT_CLOTUREE = 3,
} presence_etat_seance_t;

/* Trame écrite par le téléphone dans la caractéristique ATTEST */
typedef struct __attribute__((packed)) {
    uint8_t  id_appareil[PRESENCE_TAILLE_ID_APPAREIL];
    uint32_t compteur;       /* gros-boutiste sur le fil */
    uint32_t numero_nonce;   /* gros-boutiste sur le fil */
    /* suivi de la signature DER, longueur variable */
} presence_entete_attestation_t;

/* Lecture et écriture gros-boutistes */
static inline void presence_ecrire_u16(uint8_t *p, uint16_t v)
{
    p[0] = (uint8_t)(v >> 8);
    p[1] = (uint8_t)v;
}

static inline void presence_ecrire_u32(uint8_t *p, uint32_t v)
{
    p[0] = (uint8_t)(v >> 24);
    p[1] = (uint8_t)(v >> 16);
    p[2] = (uint8_t)(v >> 8);
    p[3] = (uint8_t)v;
}

static inline void presence_ecrire_u64(uint8_t *p, uint64_t v)
{
    presence_ecrire_u32(p, (uint32_t)(v >> 32));
    presence_ecrire_u32(p + 4, (uint32_t)v);
}

static inline uint32_t presence_lire_u32(const uint8_t *p)
{
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3];
}
