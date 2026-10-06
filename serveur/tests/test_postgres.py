"""Déclencheur d'immuabilité, vérifié seulement si une base PostgreSQL de test est fournie."""

import os

import pytest
from sqlalchemy import text

pytestmark = pytest.mark.skipif(
    not os.environ.get("PRESENCE_TEST_PG"), reason="PRESENCE_TEST_PG non défini"
)


def test_evenements_immuables():
    from app import db
    from app.services import evenements

    db.initialiser(os.environ["PRESENCE_TEST_PG"])
    s = db.nouvelle_session()
    e = evenements.enregistrer(
        s,
        type="ATTESTATION",
        auteur="obs:test",
        horodatage=1,
        contenu={"x": 1},
        preuves={},
        salle_id=None,
    )
    s.commit()
    with pytest.raises(Exception, match="immuables"):
        s.execute(text("UPDATE evenements SET auteur='pirate' WHERE id=:i"), {"i": e.id})
        s.commit()
    s.rollback()
    with pytest.raises(Exception, match="immuables"):
        s.execute(text("DELETE FROM evenements WHERE id=:i"), {"i": e.id})
        s.commit()
    s.rollback()
    s.close()
