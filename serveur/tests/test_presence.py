from app.domaine import presence as p

M = 60_000


def _calc(controles, attestations, maintenant=0):
    ivs = p.fermer(p.intervalles(controles), maintenant)
    n = p.nb_fenetres(p.duree_active(ivs))
    return p.statut(p.fenetres_validees(attestations, ivs, n), n), n


def test_nombre_de_fenetres():
    assert p.nb_fenetres(0) == 0
    assert p.nb_fenetres(60_000) == 1
    assert p.nb_fenetres(300_000) == 1
    assert p.nb_fenetres(300_000 + 119_999) == 1
    assert p.nb_fenetres(300_000 + 120_000) == 2


def test_present_retard_depart_partiel_absent():
    seance = [("DEBUT", 0), ("CLOTURE", 60 * M)]
    tout = [i * M for i in range(60)]
    assert _calc(seance, tout)[0].statut == "PRESENT"
    assert _calc(seance, [t for t in tout if t >= 20 * M])[0].statut == "RETARD"
    assert _calc(seance, [t for t in tout if t < 40 * M])[0].statut == "DEPART_ANTICIPE"
    assert _calc(seance, [t for t in tout if 10 * M <= t < 40 * M])[0].statut == "PARTIEL"
    assert _calc(seance, [])[0].statut == "ABSENT"


def test_pause_exclue_et_bornes_semi_ouvertes():
    seance = [("DEBUT", 0), ("PAUSE", 30 * M), ("REPRISE", 45 * M), ("CLOTURE", 75 * M)]
    res, n = _calc(seance, [i * M for i in range(30)] + [i * M for i in range(45, 75)])
    assert n == 12 and res.statut == "PRESENT" and res.validees == list(range(12))
    # Une attestation pile à l'instant de la pause ou pendant la pause ne compte pas
    res, _ = _calc(seance, [30 * M, 40 * M])
    assert res.validees == []


def test_priorites():
    assert p.statut([], 10, correction="PRESENT").statut == "PRESENT"
    assert p.statut([0], 10, decision="ABSENT").statut == "ABSENT"
    r = p.statut([], 10, validation_manuelle=True)
    assert r.statut == "PRESENT" and r.manuel
    assert p.statut(list(range(10)), 10, motif_a_verifier="x").statut == "A_VERIFIER"


def test_seance_ouverte_jusqua_maintenant():
    res, n = _calc([("DEBUT", 0)], [i * M for i in range(12)], maintenant=12 * M)
    assert n == 3 and res.statut == "PRESENT"
