# OSLab Docker Compose deployment

This Compose definition exposes only the frontend on host port `80`. Nginx in
the frontend image serves the Vue application and proxies `/api/` to the
internal `backend:8121` service. MySQL, Redis, Neo4j, Elasticsearch, and the
backend have no host-port mappings.

## Before the first start

1. Install Docker Engine with the Docker Compose v2 plugin.
2. For Elasticsearch on Linux, set `vm.max_map_count` to at least `262144` on
   the Docker host. Allocate enough memory for all services (at least 4 GB is a
   practical minimum for this demo).
3. Copy `.env.example` to `.env` and replace every required password and the
   sandbox shared secret with independent, long random values. Keep `.env` out
   of version control.
   The frontend is exposed on host port 80 by default. Set
   OSLAB_HTTP_PORT=8080 in .env before startup if port 80 is occupied.
4. From this `deploy` directory, inspect the resolved configuration and then
   start it:

   ```sh
   docker compose --env-file .env config
   docker compose --env-file .env up -d --build
   ```

   In PowerShell, copy the template with
   `Copy-Item .env.example .env` before editing it.

The backend receives standard Spring property environment variables:
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `SPRING_REDIS_*`, `SPRING_NEO4J_*`, and
`SPRING_ELASTICSEARCH_URIS`. It runs internally on port `8121` with the `prod`
profile. The default AI variables are intentionally empty; set `AI_ARK_API_KEY`
only when the AI assistant is required.

## Code execution sandbox

`sandbox` is an internal-only service that receives submissions exclusively
from `backend` using `SANDBOX_AUTH_SECRET`. It has no host port. For each test
case it creates a disposable worker container with no network, no host bind
mounts, a read-only root filesystem, a non-root user, dropped Linux
capabilities, PID/CPU/memory limits, and a hard timeout. Java, C, C++, and Go
are supported.

The sandbox is on a separate Compose network shared only with `backend`; the
frontend and data services cannot address its HTTP API directly.

The sandbox API needs the Docker socket only to create those workers; therefore
it is the sole service that mounts `/var/run/docker.sock`. Do not add a host
port, a reverse-proxy route, or other clients for this service. Docker Engine
on the host must permit the Compose process to access this socket. The first
build downloads the compiler toolchain; subsequent worker executions reuse the
locally built `oslab-judge-sandbox:local` image.

## Database initialization

On the **first** start of an empty `mysql_data` volume, MySQL creates
`oslab_db`, then `mysql-init/10-load-oslab-db.sh` imports the repository's
`../数据库文件/oslab_db.sql` into it. The SQL dump itself has no `USE oslab_db`,
which is why the loader script is required.

Initialization scripts are not rerun for an existing named volume. To load
different data, use a deliberate backup-and-restore procedure; do not remove a
production volume casually.

Neo4j is seeded automatically, but only on the first start of a new
`neo4j_data` named volume. Before the `neo4j` service may start, Compose runs
the short-lived `neo4j-seed` service. It loads the checked-in dump into the
default `neo4j` database and then writes the persistent marker
`/data/.oslab-graph-seeded` in the same named volume. Later starts see the
marker and leave the graph untouched.

The Neo4j **4.4.39** command has been verified against the bundled 4.4.39
admin artifact. Its syntax is:

```sh
neo4j-admin load --from=/seed/graph.db.dump --database=neo4j --force
```

Do not use `neo4j-admin database load` here: that is not a valid 4.4 command.
The seed service executes the valid command while the Neo4j server is stopped.
It prepares the fresh Docker named volume for the image's `neo4j` account
(UID/GID `7474`) and runs `neo4j-admin` as that account, so the server can
subsequently read and write the imported stores.

### Neo4j seed recovery and existing volumes

The seed guard never overwrites a volume that contains Neo4j database or
transaction directories but lacks the marker. In that case `neo4j-seed` exits
with an error and Compose will not start `neo4j`; inspect the reason with:

```sh
docker compose --env-file .env logs neo4j-seed
```

For an existing deployment, first back up and verify its graph. If that graph
is the one to retain, set `NEO4J_SEED_ACCEPT_EXISTING_DATA=true` in `.env` for
one `docker compose up -d` run. This only writes the marker; it does **not**
import the repository dump or replace data. After a successful start the
marker remains, so the variable may be set back to `false` or removed.

If the seed fails on a genuinely new volume, correct the dump mount or file
permissions and run `docker compose --env-file .env up -d` again. A successful
load is the only path that writes the marker. Replacing an established graph is
a separate, deliberate restore operation: stop Neo4j, take a backup, and use
the verified `neo4j-admin load ... --force` syntax only after deciding to
replace that data. Do not remove a production named volume merely to reseed it.

## HTTPS and browser origins

This file intentionally does not mount TLS certificates or expose port `443`.
Terminate HTTPS at an external reverse proxy, load balancer, or CDN edge and
forward traffic to this host's port `80`. Because the frontend proxies `/api/`
on the same origin, `CORS_ALLOWED_ORIGINS` should normally be blank. Add only
the exact additional HTTPS origins that must call the API directly.
