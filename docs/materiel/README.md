# Matériel

Prototype : une salle équipée, une deuxième salle simulée, un compteur de porte.

**Phase 1** : seulement 2 ESP32 observateurs (un troisième en option pour simuler une deuxième salle), les téléphones et le PC. **Phase 2** : on ajoute l'ESP32 de porte et les capteurs VL53L1X.

## Fourni par l'école

| Matériel | Qté | Rôle |
|---|---|---|
| ESP32 avec Bluetooth (ESP32 ou ESP32-S3) | 5 | 2 observateurs salle A, 1 observateur salle B, 1 compteur de porte, 1 de rechange ou simulateur de téléphone |
| Breadboards, câbles Dupont | 1 lot | Montage |
| Câbles USB et chargeurs 5 V | 5 | Alimentation |
| Batteries externes | 1 ou 2 | Si aucune prise près de la porte |
| Fer à souder | 1 | Assemblage |
| PC, 16 Go de RAM conseillés | 1 | Serveur, base, réseau Fabric |
| Routeur Wi-Fi | 1 | Réseau dédié au prototype |

## À acheter

| Matériel | Qté | Rôle |
|---|---|---|
| Module VL53L1X (I2C) | 2 | Détection du passage et de son sens |
| Module VL53L5CX 8×8 | 0 ou 1 | Optionnel, passages côte à côte |
| Boîtiers ou supports | 3 ou 4 | Fixation |

## Téléphones de test

3 à 5 téléphones Android de marques différentes (Samsung, Xiaomi, Tecno, Google...). Chaque constructeur gère différemment l'arrière-plan : la diversité est indispensable aux mesures.

## Placement

| Appareil | Position |
|---|---|
| Observateurs | Deux coins opposés de la salle, environ 2 m de haut |
| Capteurs de porte | Même montant, cadre fixe, côté où le battant ne passe pas ; environ 1 m de haut ; 6 à 15 cm d'écart dans le sens de la marche ; visée horizontale vers le montant opposé |
| VL53L5CX (option) | Plafond, au centre du passage, orienté vers le bas |

## Câblage du compteur de porte

| VL53L1X | ESP32 (exemple, à adapter) |
|---|---|
| VIN | 3V3 |
| GND | GND |
| SDA (les deux capteurs) | GPIO 21 |
| SCL (les deux capteurs) | GPIO 22 |
| XSHUT capteur couloir | GPIO 25 |
| XSHUT capteur salle | GPIO 26 |

Au démarrage : les deux XSHUT à l'état bas, puis on réveille le premier capteur et on lui donne l'adresse `0x30`, puis le second avec l'adresse `0x31`.
