# Expérience 01. Fiabilité de la détection Bluetooth en arrière-plan

## Question

Un téléphone Android verrouillé, dans une poche, répond-il assez souvent à un observateur ESP32 pour valider chaque fenêtre de 5 minutes ?

## Montage

- 1 ESP32 avec le firmware `firmware/observateur` en mode test : balise, nonce toutes les 45 s, affichage de chaque attestation reçue sur le port série avec l'heure.
- 1 téléphone avec l'application en mode test : service de premier plan, journal local de chaque tentative.
- Distance : environ 5 m, dans une salle de cours.

## Déroulé

1. Lancer l'application, verrouiller le téléphone, le mettre en poche.
2. Rester une heure dans la salle, en conditions normales (assis, déplacements occasionnels).
3. Récupérer le journal de l'ESP32 et celui du téléphone.
4. Recommencer sur chaque téléphone de test.

## Mesures

- Fenêtres validées sur 12.
- Délai entre l'entrée dans la salle et la première attestation.
- Nombre de tentatives échouées et leur cause (connexion, MTU, écriture).
- Batterie consommée pendant l'heure.

## Critère de réussite

Au moins 11 fenêtres sur 12 pour la majorité des téléphones testés.

## Décision selon le résultat

| Résultat | Suite |
|---|---|
| Fiable sur la plupart des téléphones | Architecture maintenue |
| Fiable sur certaines marques seulement | Limite documentée, validation manuelle en secours |
| Peu fiable partout | Revoir le cycle ou le sens du protocole avant toute interface |
