/*
 * Constantes du protocole PRESENCE v0.1.
 * Source de vérité : protocole/constantes.yaml. Toute modification doit y être reportée.
 */
#pragma once

#include <stdint.h>

#define PRESENCE_VERSION_PROTOCOLE      0x01

/* UUID Bluetooth (forme texte, à convertir pour NimBLE) */
#define PRESENCE_UUID_SERVICE           "05c39e32-50d8-4214-9d1c-9ecb2c8f9364"
#define PRESENCE_UUID_INFO              "d6bed148-c226-4d44-a6fc-b1c2242f8cce"
#define PRESENCE_UUID_NONCE             "32f15e73-099d-475c-b214-2a497b29ce2d"
#define PRESENCE_UUID_ATTEST            "47ed4924-b297-4e43-8e4c-91b5665dfb9c"

/* Tailles en octets */
#define PRESENCE_TAILLE_NONCE           16
#define PRESENCE_TAILLE_ID_APPAREIL     8
#define PRESENCE_TAILLE_SIGNATURE_MAX   72
#define PRESENCE_TAILLE_ATTESTATION_MAX (PRESENCE_TAILLE_ID_APPAREIL + 4 + 4 + PRESENCE_TAILLE_SIGNATURE_MAX)

/* Étiquette de séparation de domaine pour la signature des attestations */
#define PRESENCE_ETIQUETTE_ATTEST       "PRESENCE/v0.1/ATTEST"

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
