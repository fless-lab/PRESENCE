import pytest

from app.registre.local import RegistreLocal


def test_ancrage_et_corrections(tmp_path):
    r = RegistreLocal(tmp_path / "r.jsonl")
    tx = r.ancrer_seance("S-1", 204, 0, 10, "a" * 64, 3, ["204-A"])
    assert r.lire("S-1")["transaction"] == tx
    with pytest.raises(ValueError):
        r.ancrer_seance("S-1", 204, 0, 10, "b" * 64, 3, [])
    with pytest.raises(ValueError):
        r.ancrer_correction("S-1", "c" * 64, "f" * 64, "mauvaise racine précédente")
    r.ancrer_correction("S-1", "c" * 64, "a" * 64, "justificatif")
    assert r.lire("S-1")["corrections"][0]["racine"] == "c" * 64
    assert r.lire("S-2") is None


def test_alteration_detectee(tmp_path):
    chemin = tmp_path / "r.jsonl"
    r = RegistreLocal(chemin)
    r.ancrer_seance("S-1", 204, 0, 10, "a" * 64, 3, [])
    chemin.write_text(chemin.read_text().replace("a" * 64, "b" * 64))
    with pytest.raises(RuntimeError):
        r.lire("S-1")
