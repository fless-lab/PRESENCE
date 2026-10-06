"""Moteur SQLAlchemy et session par requête."""

from __future__ import annotations

from collections.abc import Iterator

from sqlalchemy import create_engine, event, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import StaticPool


class Base(DeclarativeBase):
    pass


_moteur: Engine | None = None
_fabrique: sessionmaker[Session] | None = None

DDL_IMMUABLE = """
CREATE OR REPLACE FUNCTION presence_refuser_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Les événements sont immuables : ajouter un événement CORRECTION';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS evenements_immuables ON evenements;
CREATE TRIGGER evenements_immuables BEFORE UPDATE OR DELETE ON evenements
    FOR EACH ROW EXECUTE FUNCTION presence_refuser_modification();
"""


def initialiser(url: str) -> Engine:
    """Crée le moteur et les tables. Sur PostgreSQL, interdit toute modification d'événement."""
    global _moteur, _fabrique
    options: dict = {}
    if url.startswith("sqlite"):
        options["connect_args"] = {"check_same_thread": False}
        if ":memory:" in url:
            options["poolclass"] = StaticPool
    _moteur = create_engine(url, **options)
    if url.startswith("sqlite"):

        @event.listens_for(_moteur, "connect")
        def _sqlite(connexion, _):  # pragma: no cover - configuration
            connexion.execute("PRAGMA foreign_keys=ON")

    from app import modeles  # noqa: F401  enregistre les tables

    Base.metadata.create_all(_moteur)
    if _moteur.dialect.name == "postgresql":
        with _moteur.begin() as cx:
            cx.execute(text(DDL_IMMUABLE))
    _fabrique = sessionmaker(bind=_moteur, expire_on_commit=False)
    return _moteur


def moteur() -> Engine:
    assert _moteur is not None, "base non initialisée"
    return _moteur


def session() -> Iterator[Session]:
    assert _fabrique is not None, "base non initialisée"
    s = _fabrique()
    try:
        yield s
        s.commit()
    except Exception:
        s.rollback()
        raise
    finally:
        s.close()


def nouvelle_session() -> Session:
    assert _fabrique is not None, "base non initialisée"
    return _fabrique()
