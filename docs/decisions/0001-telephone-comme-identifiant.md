# 0001. Le téléphone est le seul identifiant porté

**Statut** : acceptée, octobre 2026

## Contexte

Trois options ont été étudiées : un boîtier autonome (ESP32, radio, batterie), un badge cryptographique, le téléphone. L'étudiant ne doit porter qu'un seul objet, sans recharge supplémentaire.

## Décision

Le téléphone Android de l'étudiant porte l'identifiant : une paire ECDSA P-256 générée dans le Keystore matériel, non exportable, vérifiée par attestation de clé à l'enrôlement.

## Conséquences

- Aucun matériel à distribuer aux étudiants.
- Le téléphone doit être allumé, Bluetooth actif. Un téléphone déchargé passe par la validation manuelle.
- Un étudiant sans smartphone compatible relève d'une procédure manuelle.
