# Backend deployment contract

The container image exposes the API on `8121` and starts with
`--spring.profiles.active=prod`. Operators can override the profile by replacing
the container command, or supply `SPRING_PROFILES_ACTIVE=prod` explicitly in an
orchestrator. The application API context path remains `/api`.

## Required services

The production profile requires MySQL, Redis, Neo4j, and Elasticsearch to be
reachable from the backend container. Elasticsearch is not optional: the
existing `PostServiceImpl` injects `ElasticsearchRestTemplate`, and post search
and scheduled post-index synchronization use `PostEsDao`. The deployment
compose file must therefore add an Elasticsearch service (or point the variables
below at a managed cluster) before post search is considered functional.

## Runtime environment

Use standard Spring environment names in production. Keep secret values in the
orchestrator secret store or an untracked environment file, never in YAML.

| Service | Variables |
| --- | --- |
| Server | `SERVER_PORT` (defaults to `8121`), `SERVER_ADDRESS` (defaults to `0.0.0.0`) |
| MySQL | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| Redis | `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `SPRING_REDIS_DATABASE`, `SPRING_REDIS_PASSWORD`, `SPRING_REDIS_TIMEOUT` |
| Neo4j | `SPRING_NEO4J_URI`, `SPRING_NEO4J_AUTHENTICATION_USERNAME`, `SPRING_NEO4J_AUTHENTICATION_PASSWORD` |
| Elasticsearch | `SPRING_ELASTICSEARCH_URIS`, `SPRING_ELASTICSEARCH_USERNAME`, `SPRING_ELASTICSEARCH_PASSWORD` |
| Browser access | `CORS_ALLOWED_ORIGINS` (comma-separated exact HTTPS origins; empty disables cross-origin requests) |
| AI assistant | `AI_ARK_API_KEY`, `AI_ARK_BASE_URL`, `AI_ARK_MODEL` |
| API docs | `KNIFE4J_ENABLE`, `KNIFE4J_BASIC_ENABLE`, `KNIFE4J_BASIC_USERNAME`, `KNIFE4J_BASIC_PASSWORD` |

For a short transition, the production configuration also recognizes the
existing Compose aliases `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`,
`MYSQL_USER`, `MYSQL_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`,
`REDIS_PASSWORD`, `NEO4J_URI`, `NEO4J_USER`, and `NEO4J_PASSWORD` as fallbacks.
New deployment definitions should use the `SPRING_*` names above.

Knife4j is disabled in the production profile by default. If it is enabled,
enable basic authentication as well and provide a non-empty credential pair.
