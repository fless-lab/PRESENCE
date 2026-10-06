"""Calcul de la présence (SPEC §7). Déterministe et sans accès à la base.

Le même algorithme est réimplémenté dans le vérificateur de reçus du tableau de
bord et dans l'outil en ligne de commande : toute modification doit y être reportée.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass, field

from app.constantes import DUREE_FENETRE_MS, FENETRES_RETARD, RESTE_MIN_FENETRE_MS, SEUIL_PRESENT

Intervalle = tuple[int, int | None]


def intervalles(controles: Iterable[tuple[str, int]]) -> list[Intervalle]:
    """Intervalles actifs à partir des événements DEBUT, PAUSE, REPRISE, CLOTURE triés."""
    resultat: list[Intervalle] = []
    ouvert: int | None = None
    for type_, t in controles:
        if type_ in ("DEBUT", "REPRISE") and ouvert is None:
            ouvert = t
        elif type_ in ("PAUSE", "CLOTURE") and ouvert is not None:
            resultat.append((ouvert, t))
            ouvert = None
        if type_ == "CLOTURE":
            break
    if ouvert is not None:
        resultat.append((ouvert, None))
    return resultat


def fermer(ivs: list[Intervalle], maintenant: int) -> list[tuple[int, int]]:
    return [(d, f if f is not None else max(d, maintenant)) for d, f in ivs]


def duree_active(ivs: list[tuple[int, int]]) -> int:
    return sum(f - d for d, f in ivs)


def position(t: int, ivs: list[tuple[int, int]]) -> int | None:
    """Position sur l'axe actif, intervalles semi-ouverts [début, fin[."""
    cumul = 0
    for d, f in ivs:
        if d <= t < f:
            return cumul + (t - d)
        cumul += f - d
    return None


def nb_fenetres(duree_ms: int) -> int:
    n = duree_ms // DUREE_FENETRE_MS
    if duree_ms % DUREE_FENETRE_MS >= RESTE_MIN_FENETRE_MS:
        n += 1
    if duree_ms > 0 and n == 0:
        n = 1
    return n


def fenetres_validees(instants: Iterable[int], ivs: list[tuple[int, int]], n: int) -> list[int]:
    v: set[int] = set()
    for t in instants:
        p = position(t, ivs)
        if p is not None and p // DUREE_FENETRE_MS < n:
            v.add(p // DUREE_FENETRE_MS)
    return sorted(v)


@dataclass
class Presence:
    statut: str
    validees: list[int] = field(default_factory=list)
    total: int = 0
    manuel: bool = False
    corrige: bool = False
    motif_a_verifier: str | None = None


def statut(
    validees: list[int],
    total: int,
    correction: str | None = None,
    decision: str | None = None,
    validation_manuelle: bool = False,
    motif_a_verifier: str | None = None,
) -> Presence:
    p = Presence(statut="ABSENT", validees=validees, total=total)
    if correction:
        p.statut, p.corrige = correction, True
        return p
    if decision:
        p.statut = decision
        return p
    if validation_manuelle:
        p.statut, p.manuel = "PRESENT", True
        return p
    if motif_a_verifier:
        p.statut, p.motif_a_verifier = "A_VERIFIER", motif_a_verifier
        return p
    nv = len(validees)
    if nv == 0 or total == 0:
        return p
    if nv / total >= SEUIL_PRESENT:
        p.statut = "PRESENT"
        return p
    f, last = validees[0], validees[-1]
    if f >= FENETRES_RETARD and nv / (total - f) >= SEUIL_PRESENT:
        p.statut = "RETARD"
    elif last <= total - FENETRES_RETARD and nv / (last + 1) >= SEUIL_PRESENT:
        p.statut = "DEPART_ANTICIPE"
    else:
        p.statut = "PARTIEL"
    return p
