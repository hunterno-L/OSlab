# 环境配置与运行

本文配置来自仓库中的 Dockerfile、Compose、Maven、npm 锁文件和 Spring 配置。两种运行方式各有模板，不能互换文件。

## 依赖清单

| 依赖 | 容器基线 | 本机开发 |
| --- | --- | --- |
| Java | 后端 Temurin 17；沙箱 OpenJDK 17 | JDK 17+，不要使用旧说明中的 JDK 8/11 |
| Maven | Wrapper 3.8.1 | 使用 `mvnw` / `mvnw.cmd`，首次需联网下载 |
| Node.js / npm | `node:18-alpine` 提供构建环境 | Node.js 18+、npm 9+；以 `npm ci` 安装锁定版本 |
| MySQL | 8.0.36 | MySQL 8.0+，字符集 `utf8mb4` |
| Redis | 7.2.5 | 兼容 Redis 7，默认逻辑库 1 |
| Neo4j | 4.4.39 Community | 使用兼容 4.4 的服务及管理工具；dump 必须由兼容版本加载 |
| Elasticsearch | 7.17.22 | 7.17 系列，与现有 Spring 客户端匹配 |
| Nginx | 前端运行镜像 | 仅静态部署时需要；开发服务使用 Vue CLI 代理 |
| Python / 编译器 | 沙箱镜像提供 Python 3、GCC/G++、Go、JDK | 沙箱服务与 Worker 由 Docker 构建，无 pip 第三方依赖 |
| Docker | Docker Engine / Desktop + Compose v2 | 完整隔离判题需要 Docker；本机前后端开发可单独运行 |

完整栈建议 8 GB 可用内存、20 GB 磁盘。只启动本机前后端不代表已部署真实沙箱。完整依赖分别见 `package-lock.json`、`pom.xml`、Dockerfile 和 `deploy/docker-compose.yml`。

## 配置入口与优先级

- **本机 Windows**：根 `.env.example` → `.env`。`start-oslab.ps1` 自动读取；可用 `-EnvFile <路径>` 指定文件。已存在的进程环境变量优先于文件，显式 `-BackendPort` 优先于 `SERVER_PORT`。
- **Docker**：`deploy/.env.example` → `deploy/.env`。Compose 通过 `--env-file` 读取；同名 shell 环境变量优先。`run-local.ps1` 首次生成独立随机密钥，不覆盖已有文件。显式 `-Port` 优先于环境和模板端口。
- **手动 Java**：Spring Boot 本身不会读取根 `.env`，需先将变量导入启动进程。前端 Vue CLI 只自动读取其工作目录内的 `.env*`，根模板也需显式导入。

PowerShell 解析器支持 `KEY=value`、`export KEY=value`、单/双引号和整行注释。值按原文读取，不做 `$()`、变量或转义展开；不要写行尾注释。重复键、无效赋值和未闭合引号会在修改环境前报错。

复制模板后填写占位值。不要将 API Key、生产口令、个人令牌写进 YAML、源码、说明、截图或 Git 提交。模板中的所有值都是示例，`.env`、日志与私有运行目录均被忽略。

## 本机变量

| 变量 | 作用与默认值 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 本机 `dev`；容器 `prod` |
| `SERVER_ADDRESS` / `SERVER_PORT` | 根模板绑定 `127.0.0.1:8121`；容器绑定内部 `0.0.0.0:8121` |
| `SPRING_DATASOURCE_URL` | JDBC MySQL URL，含库名、字符集与时区；根模板带引号以便 shell 导入 |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | 应用数据库账号与口令，建议只授予应用库权限 |
| `SPRING_REDIS_HOST` / `PORT` / `DATABASE` / `PASSWORD` / `TIMEOUT` | `localhost` / `6379` / `1` / 空 / `5s`；这里的后四项同样带 `SPRING_REDIS_` 前缀 |
| `SPRING_NEO4J_URI` / `SPRING_NEO4J_AUTHENTICATION_USERNAME` / `SPRING_NEO4J_AUTHENTICATION_PASSWORD` | Bolt URI、用户名与口令，模板为本机 `7687` |
| `SPRING_ELASTICSEARCH_URIS` / `USERNAME` / `PASSWORD` | 本机 `http://localhost:9200`；完整变量均带 `SPRING_ELASTICSEARCH_` 前缀 |
| `VUE_APP_API_TARGET` | 开发代理目标，默认 `http://localhost:8121`；不可填入任何密钥 |
| `OSLAB_LEARNING_ENABLED_COURSES` | 默认 `course.operating-systems`；多个课程用逗号分隔 |
| `OSLAB_TASK_STORAGE_DIR` | 私有任务附件，根模板 `./data/task-files`，相对后端工作目录 |
| `CORS_ALLOWED_ORIGINS` | 需要跨源开发时填写精确来源；同源 Nginx 部署通常留空 |

## Docker 变量

`deploy/.env.example` 是 Compose 的完整用户输入模板，服务间地址由 Compose 固定配置，无需把本机 JDBC 地址复制进去。

| 变量 | 作用 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | 首次初始化 MySQL 的 root 口令 |
| `MYSQL_APP_USERNAME` / `MYSQL_APP_PASSWORD` | 应用账号，用户名省略时为 `oslab_app` |
| `REDIS_PASSWORD` / `NEO4J_PASSWORD` | Redis 与 Neo4j 服务口令，分别生成 |
| `SANDBOX_AUTH_SECRET` | 后端与内部沙箱共享的鉴权密钥，与数据库口令分开 |
| `OSLAB_AI_MASTER_KEY` | 个人模型配置的加密主密钥，首次生成并长期保存 |
| `OSLAB_HTTP_PORT` | 主机公开 HTTP 端口，默认 `80` |
| `NEO4J_SEED_ACCEPT_EXISTING_DATA` | 默认 `false`；仅在已备份并确认旧图数据后使用，详见部署说明 |
| `JAVA_TOOL_OPTIONS` | 默认 `-Xms256m -Xmx768m`，按机器内存调整 |
| `SANDBOX_MAX_CONCURRENT` | 默认 `2`，控制同时运行的判题 Worker |

环境变量的容器转发已在 Compose 中显式声明。COS、AI 与下列调优变量也可通过该模板配置。

## AI、embedding 与沙箱

| 变量 | 说明 |
| --- | --- |
| `AI_ARK_API_KEY` / `AI_ARK_BASE_URL` / `AI_ARK_MODEL` | 默认模型的 Key、兼容端点与模型标识；模型标识需与你的服务授权一致 |
| `AI_ARK_EMBEDDING_MODEL` | 有 embedding 权限时填写，否则留空并降级检索 |
| `AI_EMBEDDING_BASE_URL` / `AI_EMBEDDING_API_KEY` | embedding 独立端点/Key；留空复用聊天端点/Key |
| `AI_ALLOW_PRIVATE_BASE_URL` | 默认 `false`；仅明确部署内部模型时开启 |
| `AI_CONNECT_TIMEOUT_MS` / `AI_REQUEST_TIMEOUT_MS` | 默认 `5000` / `30000` |
| `AI_TURN_POOL_SIZE` / `AI_TURN_QUEUE_CAPACITY` | 默认 `2` / `32`，限制异步轮次并发与队列 |
| `AI_TURN_MAX_TOOL_CALLS` / `AI_TURN_MAX_MODEL_CALLS` | 默认 `3` / `2` |
| `AI_TURN_ANSWER_MAX_TOKENS` / `AI_TURN_PLAN_MAX_TOKENS` | 默认 `4096` / `2048` |
| `AI_RETRIEVAL_MAX_SOURCES` / `AI_RETRIEVAL_SEMANTIC_CANDIDATES` / `AI_RETRIEVAL_KEYWORD_CANDIDATES` | 默认 `8` / `200` / `60` |
| `AI_MATERIAL_MAX_CHARS` / `AI_MATERIAL_MAX_COUNT` / `AI_CHUNK_TARGET_CHARS` | 默认 `20000` / `20` / `700` |
| `CODESANDBOX_TYPE` | 使用 `remote`；不要以示例实现代替真实判题 |
| `CODESANDBOX_REMOTE_URL` / `CODESANDBOX_REMOTE_AUTH_SECRET` | 本机填写真实内部沙箱地址及匹配密钥；Compose 自动设置 |
| `CODESANDBOX_REMOTE_TIMEOUT_MS` | 默认 `30000` |
| `COS_ACCESS_KEY` / `COS_SECRET_KEY` / `COS_REGION` / `COS_BUCKET` | 可选对象存储；不用时 Key 与 bucket 留空 |

个人模型在界面“模型设置”中测试后激活。`OSLAB_AI_MASTER_KEY` 缺失时无法保存个人 Key；更换后需重新配置原有个人 Key。不要把此主密钥与个人 Key 混为一项。

`KNIFE4J_ENABLE` 控制 API 文档，生产默认 `false`；需开启时，同时配置 `KNIFE4J_BASIC_ENABLE`、`KNIFE4J_BASIC_USERNAME` 和 `KNIFE4J_BASIC_PASSWORD`，通过受限环境注入。不要把文档账户写入源码。

## Linux / macOS 本机开发

从仓库根目录，编辑 `.env` 后分别在两个终端运行。模板是可信本机配置；不要 source 来历不明的文件。

```bash
set -a
. ./.env
set +a
cd 后端代码/osLab-backend
./mvnw -DskipTests package
java -jar target/springboot-init-0.0.1-SNAPSHOT.jar
```

```bash
set -a
. ./.env
set +a
cd 前端代码/osLab-frontend
npm ci
npm run serve -- --port 8081
```

本机课程图片、题目和图谱依赖真实初始化数据。端口可达不代表数据库结构正确；按 [数据库说明](../数据库文件/README.md) 检查迁移版本。首次账号设置也见该文档。

## 故障排查

- 编译器报告找不到新增模块：先确认文件已在当前提交，切换分支后重启开发服务，等待编译完成再刷新；不要关闭错误遮罩来掩盖编译错误。
- MySQL 访问失败：检查账号权限、URL 库名及口令；不要用重新导入基础库解决连接错误。
- Neo4j 数据未加载：检查 dump 版本及初始化脚本日志，不要对已有图库强制覆盖。
- `FALLBACK`：检查模型、Key、端点和 embedding 权限；这是实际降级状态。
- 沙箱不可用：检查内部服务、鉴权和 Docker；没有沙箱时不应出现假通过。
- 端口占用：Compose 调整 `OSLAB_HTTP_PORT`；本机用 `-FrontendPort` / `-BackendPort`。

本轮验证区分配置检查和实际运行：当前工作站没有 Docker 命令，完整 Compose 运行须在安装 Docker 的机器执行。历史本机功能验收不能替代此项部署验证。
