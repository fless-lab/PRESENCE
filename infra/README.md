# Infrastructure locale

```bash
cd infra
docker compose up -d          # base PostgreSQL et serveur
docker compose logs -f serveur
docker compose down -v        # tout arrêter et effacer la base
```

Le réseau Hyperledger Fabric se lance séparément : voir [`registre/reseau/`](../registre/reseau/).
