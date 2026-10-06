# 0006. Pas d'UWB ni de badge dédié en V1

**Statut** : acceptée, octobre 2026

## Contexte

L'UWB mesure précisément la distance, mais les modules sont chers et longs à mettre au point, et peu de téléphones l'intègrent. Un badge dédié contredit la décision 0001.

## Décision

La V1 n'utilise ni UWB ni badge. La détection d'une personne portant plusieurs identifiants repose sur la règle de cardinalité.

## Conséquences

- L'attaque par relais Bluetooth reste étudiée, mesurée et documentée, sans être résolue.
- L'UWB figure dans les perspectives.
