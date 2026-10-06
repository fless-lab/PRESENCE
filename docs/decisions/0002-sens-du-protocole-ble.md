# 0002. L'observateur émet, le téléphone répond

**Statut** : acceptée, octobre 2026

## Contexte

Les téléphones changent régulièrement d'adresse Bluetooth et Android limite l'activité en arrière-plan. Un observateur qui chercherait les téléphones les suivrait mal.

## Décision

L'observateur annonce un service et un nonce tournant. Le téléphone, dans un service de premier plan filtré sur l'UUID du service, se connecte, signe le nonce et écrit sa réponse, puis se déconnecte.

## Conséquences

- Pas besoin d'Internet côté téléphone.
- Un ESP32 accepte un nombre limité de connexions simultanées : les échanges doivent rester courts. Le passage à l'échelle d'un amphithéâtre est à mesurer.
