# Vérificateur de reçu

Outil indépendant : il n'utilise ni le code ni la base du serveur.

```bash
pip install cryptography
python verifier.py recu.json --serveur http://localhost:8000
python verifier.py recu.json --cle <clé publique base64> --racine <racine du registre>
```

Quatre contrôles :

1. **Preuves d'inclusion** : chaque événement du reçu appartient à l'arbre de la séance (RFC 9162).
2. **Racine** : la racine du reçu est celle ancrée sur le registre, corrections comprises.
3. **Signature** : le reçu est signé par le serveur.
4. **Statut** : recalculé à partir des événements du reçu, avec l'algorithme de la SPEC §7.

Le code de sortie vaut 0 si le reçu est valide, 1 sinon.
