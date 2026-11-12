# 部署入口

完整项目介绍与团队信息见 [项目 README](../README.md)，所有环境变量与依赖见 [环境配置](../docs/environment.md)。本目录保留一套 Compose 配置，历史重复部署文档可从 Git 历史查阅。

## 首次部署

在仓库根目录执行，先安装 Docker Engine / Desktop 和 Compose v2：

```bash
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env：替换全部 replace-with-... 占位值，各密钥使用独立随机值
docker compose --project-name oslab --env-file deploy/.env -f deploy/docker-compose.yml config --quiet
docker compose --project-name oslab --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
docker compose --project-name oslab --env-file deploy/.env -f deploy/docker-compose.yml ps
```

Windows PowerShell 可用 `pwsh -File ./run-local.ps1 -Port 8080`，首次自动生成随机密钥；已有 `.env` 不会覆盖。Linux/macOS 需要手动生成，如分别执行 `openssl rand -hex 32`。不要在输出或报告中公开生成值。

默认公开端口为 80，由 `OSLAB_HTTP_PORT` 修改。MySQL、Redis、Neo4j、Elasticsearch、后端与沙箱仅容器网络可达。浏览器访问同源 `/api`，通常无需开放额外 CORS。

健康检查：`http://localhost:<公开端口>/api/health/liveness`。`ps` 中后端健康后再进行登录和业务验收。

## 数据初始化

- MySQL 的 `mysql_data` 为空时，`mysql-init/10-load-oslab-db.sh` 依次导入基础库及 v3–v14，并检查结构、41 项实验和映射数量。
- Neo4j 的 seed 服务仅向空且未初始化的 `neo4j_data` 加载 `graph.db.dump`，完成后主服务才启动。
- 已有卷不重跑初始化，修改源码后重建镜像也不会升级数据库。升级顺序见 [数据库说明](../数据库文件/README.md)。
- `NEO4J_SEED_ACCEPT_EXISTING_DATA=true` 只用于明确接纳已备份的既有图库，该模式不导入 dump，不是“覆盖失败再试”开关。
- 五章快照见 [database-20261028](database-20261028/README.md)，它是另一个空库恢复路径，不能覆盖默认首装库。

## 停止、更新与日志

```bash
docker compose --project-name oslab --env-file deploy/.env -f deploy/docker-compose.yml logs --tail 100 backend
docker compose --project-name oslab --env-file deploy/.env -f deploy/docker-compose.yml down
# 拉取新代码并检查迁移后，重新构建启动
docker compose --project-name oslab --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

`down` 保留数据卷；正常运维不要添加 `--volumes`。升级前备份 MySQL、Neo4j、任务私有附件和环境密钥，特别是 `OSLAB_AI_MASTER_KEY`。

## 目录

| 路径 | 用途 |
| --- | --- |
| `.env.example` | 全部 Compose 用户配置项的模板 |
| `docker-compose.yml` | 服务、内部网络、持久卷、环境转发与健康检查 |
| `mysql-init/` | 基础库和迁移加载脚本 |
| `neo4j-init/` | 图数据初始化与重复导入保护 |
| `database-20261028/` | 脱敏五章课程快照、恢复说明与校验和 |

本轮工作站未安装 Docker，容器运行验证需在具备 Docker 的主机执行；配置与脚本检查不等于完整容器部署通过。
