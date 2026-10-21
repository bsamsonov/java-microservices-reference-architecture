# Service databases in k3d

The `pg` cluster (CloudNativePG) creates the `orders` database at initdb. Every further service adds a `<db>.yaml` file here;
`make infra` applies the folder once the cluster is ready.

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Database
metadata:
  name: <db-with-dashes>      # Kubernetes resource name: no underscores
  namespace: infra
spec:
  cluster: {name: pg}
  name: <db>                  # database name in Postgres
  owner: app
```
