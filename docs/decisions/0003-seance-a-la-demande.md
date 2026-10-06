# 0003. Séance à la demande, sans emploi du temps

**Statut** : acceptée, octobre 2026

## Contexte

La gestion d'un emploi du temps (créneaux, décalages, salles, conflits) n'apporte rien aux questions de recherche et multiplie les cas particuliers.

## Décision

Une séance n'existe que lorsqu'un enseignant, attesté dans la salle, la démarre en choisissant un cours. Les cours et les inscrits sont importés par fichier CSV.

## Conséquences

- Un enseignant en retard démarre simplement plus tard.
- La présence de l'enseignant est garantie au démarrage.
- Un cours prévu mais jamais démarré n'est pas détecté en V1.
