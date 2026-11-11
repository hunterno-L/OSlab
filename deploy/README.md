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
`oslab_db`, then `mysql-init/10-load-oslab-db.sh` imports, in order, the
repository's base dump plus every learning upgrade and seed:

```text
数据库文件/oslab_db.sql            基础库（user/question/question_submit…）
数据库文件/oslab_upgrade_v3_learning.sql   learning 六张契约表
数据库文件/oslab_upgrade_v4_flow.sql       测评会话、提交幂等、事件快照
数据库文件/oslab_upgrade_v5_teaching.sql   节点共享素材表
数据库文件/oslab_demo_seed_v3.sql          演示题与映射种子
数据库文件/oslab_seed_v4_content.sql       两条知识链素材与补充题
数据库文件/oslab_upgrade_v6_courses.sql    课程、单元与内容目录
数据库文件/oslab_upgrade_v7_identity.sql  稳定身份与实验映射
数据库文件/oslab_upgrade_v8_teaching_tasks.sql 学习任务与执行副本
数据库文件/oslab_upgrade_v9_learning_activities.sql 演示运行记录
数据库文件/oslab_upgrade_v10_classes.sql 班级与成员管理
数据库文件/oslab_upgrade_v11_ai_session.sql AI 会话与资料
数据库文件/oslab_upgrade_v12_ai_evidence.sql AI 模型与学习证据
数据库文件/oslab_upgrade_v13_learning_selftest.sql 整组自测
数据库文件/oslab_seed_v14_experiment_prerequisites.sql 实验单元公共课程依赖
数据库文件/oslab_upgrade_v14_experiment_judge.sql 41 项实验与映射
数据库文件/oslab_upgrade_v14_experiment_comments.sql 实验源码注释清理
```

Every file is a mandatory read-only mount on the `mysql` service; the loader
fails fast if any mount is missing. After loading, it verifies the contract
via `information_schema` (learning tables, v4 columns, unique keys, snapshot
columns, admin account, seed row counts 26/50/12) and fails the container if a
check does not pass. The base dump already contains `uk_userAccount`; the v2
script is intentionally not executed against fresh volumes.

The v14 prerequisite seed supplies only the public dependency closure of the
18 experiment units: 52 content chunks, 33 questions and their catalog/node
references. It preserves existing rows and fills an empty v6 unit only when
its complete shipped template matches. v14 preserves non-experiment activities
and unit nodes, rejects conflicting unit edits, and can be reapplied. Run MySQL
in its default batch mode, without `--force`, so an error stops execution and
the uncommitted InnoDB transaction rolls back on disconnect. Back up an existing
database before upgrading. Do not reapply the fresh prerequisite seed as a way
to replace an independently edited curriculum.

Apply the separate comment update after v14. It removes institution labels and
internal production notes from current code templates and reference answers,
preserves reviewed revision history, and changes no executable code or mappings.

The exported state publishes 36 host-tested experiment resources and 12 units;
5 MIPS local-build resources and 6 related units remain drafts. Fresh and
upgrade SQL replay were verified with native MySQL; Docker startup, the actual
sandbox and MIPS execution still require runtime verification. v13 was also
added to first-install loading because the current application uses its columns.

Initialization scripts are not rerun for an existing named volume. For an
existing database, run the idempotent upgrade check instead:
`数据库文件/oslab_upgrade_existing_check.sql` (utf8mb4 client charset) — it
reports missing columns/indexes, adds them only when safe, and aborts with a
clear message when duplicate `userAccount` rows must be resolved first. Apply
new additive feature migrations separately to an existing volume, for example:

```sh
docker compose --env-file .env exec -T mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" oslab_db' \
  < ../数据库文件/oslab_upgrade_v10_classes.sql
```

The v10 script uses `CREATE TABLE IF NOT EXISTS` and can be rerun safely.
Demo accounts (`teacher_demo`, `student_demo1/2`) are not part of the SQL
seeds; create/verify them from a running backend with
`scripts/dev/prepare-demo-accounts.ps1 -DemoPassword <口令>`.

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

## Bare-metal deployment without Docker

For a 2 GB Ubuntu host that cannot run this Compose stack, see
[Ubuntu-2G-无Docker环境配置说明.md](Ubuntu-2G-无Docker环境配置说明.md).
It runs MySQL, Redis, Neo4j, the backend, and Nginx directly on the host,
omits Elasticsearch and the Docker-only judge sandbox, and documents the
resulting feature limits. A recorded deployment of the same size class is in
[裸机部署实录与运维手册-39.106.136.15.md](裸机部署实录与运维手册-39.106.136.15.md).

The sanitized five-chapter MySQL snapshot and full Neo4j graph export are in
[database-20261028/](database-20261028/README.md). Use them instead of the
chapter-one seed SQL and the older `graph.db.dump` when deploying the full
course.
