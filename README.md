# OSLab

面向操作系统课程的在线学习与实验平台。项目将知识图谱、章节题目、在线编程判题、学习资料和 AI 问答整合在同一套 Web 应用中。

## 当前能力

- 按题目或章节浏览操作系统实验，并进入在线做题页面。
- 根据知识图谱查看关联概念、关联题目和学习资料。
- 管理员可维护图谱节点、关系和题目内容。
- 支持 Java、C、C++、Go 的隔离式在线判题。
- 支持账号注册、登录、学生批量管理和密码重置。
- 支持可选的操作系统课程 AI 问答。

## 架构

~~~
浏览器
  │
  ▼
Vue 3 + Nginx ── /api ──> Spring Boot
                               │
                 ┌─────────────┼─────────────┐
                 ▼             ▼             ▼
               MySQL         Redis         Neo4j
                               │
                               ▼
                         Elasticsearch
                               │
                               ▼
                     内部隔离代码沙箱服务
~~~

前端通过同源 Nginx 代理访问 API，浏览器不需要知道后端容器地址。代码沙箱仅在内部网络中开放，针对每一次提交创建无网络、非 root、受资源限制的临时 Worker 容器。

## 快速开始（推荐）

### 前置条件

- Docker Desktop 或 Docker Engine
- Docker Compose v2
- 至少 4 GB 可用内存
- 一个未被占用的本地端口，默认是 80

Windows PowerShell 在仓库根目录执行：

~~~
powershell -ExecutionPolicy Bypass -File ./run-local.ps1
~~~

首次启动时，脚本会：

1. 检查 Docker 和 Docker Compose。
2. 从 deploy/.env.example 创建未纳入版本控制的 deploy/.env。
3. 为数据库、Redis、Neo4j 和代码沙箱生成随机本地开发密钥。
4. 构建并启动完整服务栈。
5. 等待 http://localhost/api/health/liveness 返回成功。

启动完成后访问：

- 平台首页：http://localhost/
- 健康检查：http://localhost/api/health/liveness

如果 80 端口被占用，可指定其他端口：

~~~
powershell -ExecutionPolicy Bypass -File ./run-local.ps1 -Port 8080
~~~

此时平台地址为 http://localhost:8080/。

## 本地脚本命令

~~~
# 启动并构建全部服务
./run-local.ps1

# 启动但跳过镜像构建
./run-local.ps1 -NoBuild

# 查看服务状态
./run-local.ps1 -Action status

# 查看最近日志
./run-local.ps1 -Action logs

# 持续跟踪日志
./run-local.ps1 -Action logs -Follow

# 停止容器，但保留数据库和图谱数据卷
./run-local.ps1 -Action stop

# 重启完整服务栈
./run-local.ps1 -Action restart
~~~

脚本的 stop 操作不会删除 Docker Volume。若确实需要清空本地数据，请先备份，再在 deploy 目录中手动执行 Docker 的卷清理操作。

## 手动 Docker Compose 启动

如果不使用脚本：

~~~
cd deploy
Copy-Item .env.example .env
# 编辑 .env，替换所有示例密钥
docker compose --env-file .env config
docker compose --env-file .env up -d --build
~~~

默认宿主机端口为 80。可在 deploy/.env 中设置 OSLAB_HTTP_PORT，例如 OSLAB_HTTP_PORT=8080。

## 配置说明

所有机密信息均应放在 deploy/.env 或部署平台的密钥管理器中，不能提交到仓库。

| 配置项 | 是否必填 | 说明 |
| --- | --- | --- |
| MYSQL_ROOT_PASSWORD | 是 | MySQL root 密码 |
| MYSQL_APP_PASSWORD | 是 | 应用访问 MySQL 的密码 |
| REDIS_PASSWORD | 是 | Redis 密码 |
| NEO4J_PASSWORD | 是 | Neo4j 密码 |
| SANDBOX_AUTH_SECRET | 是 | 后端与内部代码沙箱之间的共享密钥 |
| OSLAB_HTTP_PORT | 否 | 前端暴露的宿主机端口，默认 80 |
| AI_ARK_API_KEY | 否 | 配置后开启 AI 问答 |
| COS_ACCESS_KEY 等 | 否 | 配置后开启对象存储上传 |
| CORS_ALLOWED_ORIGINS | 否 | 仅为额外跨域前端填写精确 HTTPS 来源 |

完整的容器部署契约见 deploy/README.md 和 后端代码/osLab-backend/DEPLOYMENT.md。

## 数据初始化

首次启动空数据卷时：

- MySQL 自动导入 数据库文件/oslab_db.sql。
- Neo4j 自动导入 数据库文件/graph.db.dump。
- 图谱导入带有持久化标记，已有图数据不会被自动覆盖。

请不要通过删除生产 Volume 的方式重新初始化数据。

## 开发说明

### 前端

目录：前端代码/osLab-frontend

~~~
npm ci
npm run serve
~~~

开发服务器会将 /api 代理到 http://localhost:8121。生产构建使用：

~~~
npm run build
~~~

### 后端

目录：后端代码/osLab-backend

后端依赖 MySQL、Redis、Neo4j 和 Elasticsearch。建议先通过 Docker Compose 启动这些依赖，再使用 Maven Wrapper 编译或运行后端：

~~~
./mvnw -DskipTests compile
~~~

本地环境变量、Neo4j 认证和其他依赖配置请参考 application.yml 与 DEPLOYMENT.md。

## 验证

本次版本已完成以下本地验证：

- 后端编译通过。
- 历史 MD5 密码兼容与自动迁移回归测试通过。
- 前端生产构建通过。
- 注册、登录、获取当前用户、注销会话链路通过。
- 前端 API 代理、CORS 预检和知识图谱接口通过。

当前前端仍保留部分历史 ESLint warning，但生产构建不存在 error。

## 常见问题

### Docker 未启动

请启动 Docker Desktop，确认 docker version 与 docker compose version 均可执行后再运行脚本。

### 端口 80 被占用

使用 ./run-local.ps1 -Port 8080，或修改 deploy/.env 中的 OSLAB_HTTP_PORT。

### 服务长时间未就绪

执行 ./run-local.ps1 -Action logs 查看日志。首次构建会下载 JDK、Node、数据库镜像和编译器工具链，耗时会明显更长。

### Neo4j 启动被种子保护拦截

这通常意味着已有图数据但不存在初始化标记。请先备份数据，再按 deploy/README.md 中的 Neo4j seed recovery 指引处理；不要直接删除数据卷。

## 项目目录

~~~
前端代码/osLab-frontend/      Vue 3 前端
后端代码/osLab-backend/        Spring Boot 后端
代码沙箱/                      内部隔离判题服务
数据库文件/                    MySQL 与 Neo4j 初始化数据
deploy/                        Docker Compose、环境变量模板和部署文档
run-local.ps1                  本地服务控制脚本
~~~
