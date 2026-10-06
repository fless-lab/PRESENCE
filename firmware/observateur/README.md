# Observateur de salle

ESP32 fixé dans la salle (deux par salle, coins opposés). Phase 1.

| Fonction | Détail |
|---|---|
| Balise | Annonce le service PRESENCE avec la salle et l'état de la séance |
| Nonce | 16 octets aléatoires, renouvelés toutes les 45 s |
| Vérification | Signature ECDSA P-256 de chaque attestation, avec la clé publique de l'inscrit |
| Témoignage | Chaque attestation valide est signée par l'observateur et horodatée |
| Hors ligne | Les témoignages sont gardés en flash et renvoyés au retour du réseau |

Première étape (expérience 01) : annonce, nonce, écriture reçue affichée sur le port série, sans vérification.
