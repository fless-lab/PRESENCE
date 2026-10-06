"""Commandes d'administration : `presence <commande>`."""

from __future__ import annotations

import argparse
import getpass
import sys

from sqlalchemy import select

from app import db
from app.config import config
from app.modeles import Personne, Salle
from app.securite.jetons import hacher_mot_de_passe


def _session():
    db.initialiser(config.base_url)
    return db.nouvelle_session()


def creer_personnel(args) -> None:
    mot = args.mot_de_passe or getpass.getpass("Mot de passe : ")
    with _session() as s:
        p = s.scalar(select(Personne).where(Personne.matricule == args.matricule))
        if p is None:
            p = Personne(matricule=args.matricule, nom=args.nom, prenom=args.prenom, role=args.role)
            s.add(p)
        p.role, p.mot_de_passe = args.role, hacher_mot_de_passe(mot)
        s.commit()
    print(f"Compte {args.role} prêt : {args.matricule}")


def ajouter_salle(args) -> None:
    with _session() as s:
        if s.get(Salle, args.id) is None:
            s.add(Salle(id=args.id, nom=args.nom))
            s.commit()
    print(f"Salle {args.id} : {args.nom}")


def demo(args) -> None:
    from app.demo import executer

    seances = executer(args.mot_de_passe, args.jours)
    print(f"{len(seances)} séances créées et scellées. Compte : A001 / {args.mot_de_passe}")


def simuler(args) -> None:
    from pathlib import Path

    from app.simulateur import executer

    executer(
        args.url,
        args.minutes,
        args.periode,
        args.cours,
        args.enseignant,
        args.salle,
        not args.laisser_ouverte,
        Path(args.sortie),
    )


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="presence")
    sous = parser.add_subparsers(dest="commande", required=True)

    p = sous.add_parser("creer-personnel", help="compte scolarité, admin ou auditeur")
    p.add_argument("matricule")
    p.add_argument("--nom", default="")
    p.add_argument("--prenom", default="")
    p.add_argument("--role", choices=["SCOLARITE", "ADMIN", "AUDITEUR"], default="ADMIN")
    p.add_argument("--mot-de-passe", dest="mot_de_passe")
    p.set_defaults(fonction=creer_personnel)

    p = sous.add_parser("ajouter-salle")
    p.add_argument("id", type=int)
    p.add_argument("nom")
    p.set_defaults(fonction=ajouter_salle)

    p = sous.add_parser("demo", help="remplit la base avec un historique de séances scellées")
    p.add_argument("--mot-de-passe", dest="mot_de_passe", default="presence")
    p.add_argument("--jours", type=int, default=5)
    p.set_defaults(fonction=demo)

    p = sous.add_parser("simuler", help="joue une séance en direct contre un serveur en marche")
    p.add_argument("--url", default="http://localhost:8000")
    p.add_argument("--minutes", type=int, default=10)
    p.add_argument("--periode", type=int, default=20, help="secondes entre deux attestations")
    p.add_argument("--cours", default="SECRES")
    p.add_argument("--enseignant", default="P001")
    p.add_argument("--salle", type=int, default=204)
    p.add_argument("--laisser-ouverte", action="store_true")
    p.add_argument("--sortie", default="recu_E001.json")
    p.set_defaults(fonction=simuler)

    args = parser.parse_args(argv)
    args.fonction(args)


if __name__ == "__main__":
    main(sys.argv[1:])
