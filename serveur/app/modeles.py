"""Tables de la base. Les événements ne sont jamais modifiés ni supprimés."""

from __future__ import annotations

from sqlalchemy import (
    JSON,
    BigInteger,
    Boolean,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


class Personne(Base):
    __tablename__ = "personnes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    matricule: Mapped[str] = mapped_column(String(32), unique=True, index=True)
    nom: Mapped[str] = mapped_column(String(120))
    prenom: Mapped[str] = mapped_column(String(120))
    role: Mapped[str] = mapped_column(String(16))
    code_enrolement: Mapped[str | None] = mapped_column(String(16), nullable=True)
    mot_de_passe: Mapped[str | None] = mapped_column(String(255), nullable=True)

    appareils: Mapped[list[Appareil]] = relationship(back_populates="personne")


class Appareil(Base):
    __tablename__ = "appareils"

    id: Mapped[str] = mapped_column(String(16), primary_key=True)
    personne_id: Mapped[int] = mapped_column(ForeignKey("personnes.id"), index=True)
    cle_publique: Mapped[str] = mapped_column(Text)
    modele: Mapped[str | None] = mapped_column(String(120), nullable=True)
    attestation: Mapped[str] = mapped_column(String(16), default="ABSENTE")
    attestation_detail: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    deverrouillage_recent: Mapped[bool] = mapped_column(Boolean, default=False)
    actif: Mapped[bool] = mapped_column(Boolean, default=True)
    enrole_ms: Mapped[int] = mapped_column(BigInteger)
    revoque_ms: Mapped[int | None] = mapped_column(BigInteger, nullable=True)

    personne: Mapped[Personne] = relationship(back_populates="appareils")


class Defi(Base):
    __tablename__ = "defis"

    defi: Mapped[str] = mapped_column(String(64), primary_key=True)
    matricule: Mapped[str] = mapped_column(String(32))
    expire_ms: Mapped[int] = mapped_column(BigInteger)


class Salle(Base):
    __tablename__ = "salles"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=False)
    nom: Mapped[str] = mapped_column(String(120))


class Equipement(Base):
    __tablename__ = "equipements"

    nom: Mapped[str] = mapped_column(String(32), primary_key=True)
    salle_id: Mapped[int] = mapped_column(ForeignKey("salles.id"))
    type: Mapped[str] = mapped_column(String(16), default="OBSERVATEUR")
    cle_publique: Mapped[str] = mapped_column(Text)
    actif: Mapped[bool] = mapped_column(Boolean, default=True)
    vu_ms: Mapped[int | None] = mapped_column(BigInteger, nullable=True)

    salle: Mapped[Salle] = relationship()


class Cours(Base):
    __tablename__ = "cours"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    code: Mapped[str] = mapped_column(String(32), unique=True, index=True)
    intitule: Mapped[str] = mapped_column(String(200))
    groupe: Mapped[str] = mapped_column(String(64), default="")
    enseignant_id: Mapped[int | None] = mapped_column(ForeignKey("personnes.id"), nullable=True)

    enseignant: Mapped[Personne | None] = relationship()


class Inscription(Base):
    __tablename__ = "inscriptions"

    cours_id: Mapped[int] = mapped_column(ForeignKey("cours.id"), primary_key=True)
    personne_id: Mapped[int] = mapped_column(ForeignKey("personnes.id"), primary_key=True)


class Seance(Base):
    __tablename__ = "seances"

    id: Mapped[str] = mapped_column(String(40), primary_key=True)
    numero: Mapped[int] = mapped_column(Integer, unique=True, index=True)
    cours_id: Mapped[int] = mapped_column(ForeignKey("cours.id"))
    salle_id: Mapped[int] = mapped_column(ForeignKey("salles.id"))
    enseignant_id: Mapped[int] = mapped_column(ForeignKey("personnes.id"))
    etat: Mapped[str] = mapped_column(String(16), index=True)
    debut_ms: Mapped[int] = mapped_column(BigInteger)
    fin_ms: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    racine: Mapped[str | None] = mapped_column(String(64), nullable=True)
    nb_evenements: Mapped[int | None] = mapped_column(Integer, nullable=True)
    ancrage: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    presents: Mapped[int | None] = mapped_column(Integer, nullable=True)
    inscrits: Mapped[int | None] = mapped_column(Integer, nullable=True)

    cours: Mapped[Cours] = relationship()
    salle: Mapped[Salle] = relationship()
    enseignant: Mapped[Personne] = relationship()


class Evenement(Base):
    __tablename__ = "evenements"
    __table_args__ = (
        UniqueConstraint("appareil", "compteur", name="attestation_unique"),
        Index("evenements_par_seance", "seance_id", "horodatage"),
        Index("evenements_par_appareil", "appareil", "horodatage"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    seance_id: Mapped[str | None] = mapped_column(ForeignKey("seances.id"), nullable=True)
    type: Mapped[str] = mapped_column(String(24))
    auteur: Mapped[str] = mapped_column(String(48))
    salle_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    horodatage: Mapped[int] = mapped_column(BigInteger)
    appareil: Mapped[str | None] = mapped_column(String(16), nullable=True)
    compteur: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    matricule: Mapped[str | None] = mapped_column(String(32), nullable=True)
    canonique: Mapped[str] = mapped_column(Text)
    hachage: Mapped[str] = mapped_column(String(64), unique=True)
    recu_ms: Mapped[int] = mapped_column(BigInteger)
