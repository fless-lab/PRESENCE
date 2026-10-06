import pytest

from app.domaine.merkle import (
    hacher_feuille,
    preuve_inclusion,
    racine,
    verifier_inclusion,
)


def feuilles(n: int) -> list[bytes]:
    return [f"evenement-{i}".encode() for i in range(n)]


def test_une_feuille():
    assert racine([b"a"]) == hacher_feuille(b"a")


@pytest.mark.parametrize("n", range(1, 34))
def test_toutes_les_preuves_sont_valides(n):
    f = feuilles(n)
    r = racine(f)
    for i in range(n):
        assert verifier_inclusion(i, n, f[i], preuve_inclusion(i, f), r)


@pytest.mark.parametrize("n", [2, 5, 8, 13])
def test_une_feuille_modifiee_est_rejetee(n):
    f = feuilles(n)
    r = racine(f)
    for i in range(n):
        assert not verifier_inclusion(i, n, b"falsifie", preuve_inclusion(i, f), r)


def test_modifier_un_evenement_change_la_racine():
    f = feuilles(10)
    g = list(f)
    g[4] = b"absent devenu present"
    assert racine(f) != racine(g)


def test_mauvais_index_rejete():
    f = feuilles(7)
    r = racine(f)
    assert not verifier_inclusion(3, 7, f[2], preuve_inclusion(2, f), r)
