/* Wi-Fi station, horloge réglée sur le serveur, requêtes HTTP signées. */
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

/* Démarre le Wi-Fi et la tâche de synchronisation de l'heure (GET /heure toutes les 10 min). */
esp_err_t reseau_demarrer(void);

bool reseau_connecte(void);

/* Vrai après au moins une synchronisation réussie avec le serveur. */
bool reseau_heure_valide(void);

/* Instant courant en millisecondes depuis l'époque Unix. */
uint64_t reseau_maintenant_ms(void);

/*
 * Requête HTTP vers CONFIG_PRESENCE_URL_SERVEUR + chemin.
 * methode : "GET" ou "POST". corps peut valoir NULL. Si signer est vrai, ajoute les en-têtes
 * X-Presence-* (refusé tant que l'heure n'est pas synchronisée).
 * La réponse est copiée dans reponse, terminée par un zéro, et tronquée à taille - 1.
 * Renvoie le code HTTP, ou -1 en cas d'échec de transport.
 */
int reseau_requete(const char *methode, const char *chemin, const char *corps, bool signer,
                   char *reponse, size_t taille);
