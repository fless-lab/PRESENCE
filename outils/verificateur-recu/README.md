# Vérificateur de reçu

Outil indépendant, en Python, que n'importe quel tiers peut exécuter.

```bash
python verifier.py recu.json --racine <racine lue sur le registre>
```

Étapes :
1. Recalculer le hachage de chaque événement du reçu (JSON canonique, préfixe 0x00).
2. Remonter chaque preuve d'inclusion jusqu'à la racine.
3. Comparer avec la racine lue sur le registre, pas avec celle écrite dans le reçu.
4. Vérifier la signature du serveur sur le reçu.

Il réutilisera la même logique que `serveur/app/domaine/merkle.py`, recopiée ici pour rester utilisable sans le serveur.
