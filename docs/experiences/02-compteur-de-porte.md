# Expérience 02. Précision du compteur de porte

## Question

Deux VL53L1X sur le même montant comptent-ils correctement les entrées, les sorties et leur sens ?

## Montage

Voir [`docs/materiel/README.md`](../materiel/README.md) : capteurs à 1 m de haut, espacés de 6 à 15 cm, firmware `firmware/porte` affichant chaque passage détecté.

## Déroulé

| Série | Passages | Description |
|---|---|---|
| A | 50 | Entrées seules, allure normale |
| B | 50 | Sorties seules |
| C | 30 | Entrées et sorties alternées rapidement |
| D | 20 | Deux personnes côte à côte |
| E | 20 | Deux personnes à la suite, très rapprochées |
| F | 20 | Ouverture et fermeture de la porte sans passage |

## Mesures

Pour chaque série : passages réels, passages détectés, erreurs de sens, faux positifs.

## Critère de réussite

Erreur inférieure à 5 % sur les séries A, B et C. Les séries D et E décident de l'achat du VL53L5CX.
