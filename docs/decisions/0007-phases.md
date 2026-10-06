# 0007. Développement en trois phases

**Statut** : acceptée, octobre 2026

## Contexte

Le projet Cryptographie et Blockchain est à rendre avant le projet d'innovation. Les capteurs de porte ajoutent du matériel et des incertitudes qui ne doivent pas bloquer la partie blockchain.

## Décision

- **Phase 1** : système complet sans capteur (application, 2 observateurs, serveur, Merkle, registre, tableau de bord, reçus).
- **Phase 2** : compteur de porte et règle de cardinalité, ajoutés comme un nouveau type d'événement.
- **Phase 3** : passage à l'échelle et industrialisation.

## Conséquences

- Les formats d'événements et l'arbre de Merkle sont conçus dès la phase 1 pour accueillir les passages de porte sans modification.
- La phase 1 se démontre seule.
