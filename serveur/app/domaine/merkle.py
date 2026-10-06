"""Arbre de Merkle et preuves d'inclusion, selon le RFC 6962 / RFC 9162.

Les feuilles et les nœuds internes sont hachés avec un préfixe différent
(0x00 et 0x01) pour qu'une feuille ne puisse jamais passer pour un nœud.
Un nombre de feuilles qui n'est pas une puissance de deux est découpé à la plus
grande puissance de deux strictement inférieure, sans dupliquer de feuille.
"""

from __future__ import annotations

import hashlib
from collections.abc import Sequence

PREFIXE_FEUILLE = b"\x00"
PREFIXE_NOEUD = b"\x01"


def _sha256(donnees: bytes) -> bytes:
    return hashlib.sha256(donnees).digest()


def hacher_feuille(donnees: bytes) -> bytes:
    return _sha256(PREFIXE_FEUILLE + donnees)


def hacher_noeud(gauche: bytes, droite: bytes) -> bytes:
    return _sha256(PREFIXE_NOEUD + gauche + droite)


def _decoupe(n: int) -> int:
    """Plus grande puissance de deux strictement inférieure à n (n >= 2)."""
    k = 1
    while k * 2 < n:
        k *= 2
    return k


def racine(feuilles: Sequence[bytes]) -> bytes:
    """Racine de l'arbre construit sur les données brutes des feuilles."""
    n = len(feuilles)
    if n == 0:
        return _sha256(b"")
    if n == 1:
        return hacher_feuille(feuilles[0])
    k = _decoupe(n)
    return hacher_noeud(racine(feuilles[:k]), racine(feuilles[k:]))


def preuve_inclusion(index: int, feuilles: Sequence[bytes]) -> list[bytes]:
    """Hachages nécessaires pour relier la feuille `index` à la racine."""
    n = len(feuilles)
    if not 0 <= index < n:
        raise IndexError("index hors de l'arbre")
    if n == 1:
        return []
    k = _decoupe(n)
    if index < k:
        return preuve_inclusion(index, feuilles[:k]) + [racine(feuilles[k:])]
    return preuve_inclusion(index - k, feuilles[k:]) + [racine(feuilles[:k])]


def verifier_inclusion(
    index: int, taille: int, feuille: bytes, preuve: Sequence[bytes], racine_attendue: bytes
) -> bool:
    """Vérifie une preuve d'inclusion (RFC 9162, section 2.1.3.2).

    `feuille` est la donnée brute ; elle est hachée ici.
    """
    if not 0 <= index < taille:
        return False
    fn, sn = index, taille - 1
    r = hacher_feuille(feuille)
    for p in preuve:
        if sn == 0:
            return False
        if fn & 1 or fn == sn:
            r = hacher_noeud(p, r)
            if not fn & 1:
                while not fn & 1 and fn != 0:
                    fn >>= 1
                    sn >>= 1
        else:
            r = hacher_noeud(r, p)
        fn >>= 1
        sn >>= 1
    return sn == 0 and r == racine_attendue
