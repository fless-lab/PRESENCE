"""Source unique de l'heure, remplaçable dans les tests."""

import time


def maintenant_ms() -> int:
    return int(time.time() * 1000)
