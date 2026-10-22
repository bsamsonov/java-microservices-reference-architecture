# Service databases in k3d

Each service has its own Postgres user and database in the shared `pg` cluster (ADR-0007). A new service needs two edits:

1. `<db-with-dashes>.yaml` here:

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Database
metadata:
  name: <db-with-dashes>      # Kubernetes resource name: no underscores
  namespace: infra
spec:
  cluster: {name: pg}
  name: <db>                  # database name in Postgres
  owner: <db>                 # the service's own user
```

2. A role in `../postgres.yaml` (`spec.managed.roles`): `{name: <db>, login: true, passwordSecret: {name: pg-<db-with-dashes>}}`.

`make infra` then creates the secret `pg-<db-with-dashes>` (user `<db>`, random password; never overwritten), applies the folder and revokes
CONNECT from PUBLIC on every database, so only its owner can connect. `make apps-namespace` copies the secrets to namespace `apps`;
the service reads it through `secretEnv` in `deploy/values.yaml`.
