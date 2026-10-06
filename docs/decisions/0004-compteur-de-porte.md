# 0004. Comptage à la porte avec deux VL53L1X

**Statut** : acceptée, octobre 2026

## Contexte

La règle de cardinalité demande un comptage physique anonyme. Les barrières infrarouges bon marché portent souvent moins de 50 cm et exigent un alignement émetteur-récepteur.

## Décision

Deux capteurs de distance VL53L1X, côte à côte sur le même montant, à environ 1 m de haut, espacés de 6 à 15 cm dans le sens de la marche. L'ordre de coupure donne le sens du passage. Un VL53L5CX au plafond reste optionnel pour les passages côte à côte.

## Conséquences

- Les deux capteurs partagent le bus I2C : il faut piloter leurs broches XSHUT pour leur attribuer des adresses distinctes.
- Les capteurs se fixent sur le cadre fixe, du côté où le battant ne passe pas.
