/* Clé ECDSA P-256 de l'observateur. */
#pragma once

#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

#define IDENTITE_TAILLE_SIG_MAX 72

/* Charge la clé depuis la NVS, ou la crée au premier démarrage. Affiche la clé publique. */
esp_err_t identite_init(void);

/* Signe un condensat SHA-256. Écrit une signature DER dans sig (72 octets au plus). */
esp_err_t identite_signer(const uint8_t condensat[32], uint8_t *sig, size_t *lg_sig);

/* Condensat SHA-256 d'un tampon. */
void identite_sha256(const void *donnees, size_t lg, uint8_t condensat[32]);

/* Condensat SHA-256 en hexadécimal minuscule (65 octets avec le zéro final). */
void identite_sha256_hex(const void *donnees, size_t lg, char hex[65]);

/* Encodage base64 standard terminé par un zéro. Renvoie la longueur, ou -1 si dst est trop petit. */
int identite_base64(const uint8_t *src, size_t lg, char *dst, size_t taille_dst);
