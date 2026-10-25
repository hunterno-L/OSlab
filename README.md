# OSLab

面向操作系统课程的智能学习与实验平台：以教材目录和学习单元组织循序渐进的学习，以知识图谱连接概念、资源和个人证据，把题目作答、代码提交和模拟预测转化为可解释的学习画像。

教材驱动课程系统已实现，学生统一入口 `/graph`（`/courses` 兼容跳转），L 的资源包接收入口 `/teaching/content`。第一章十个单元已于 2026-10-20 完成编排与发布：35 个知识节点、57 个教学块、103 道题和章末复习均可从学生端学习；发布回执与余项见 [第一章发布回执](artifacts/course-first-chapter/20261020/release-receipts/README.md)。

教材内容现按章交付：现有 1.1 成果不采用，J/A 重新整理第一章整章知识包与题目包，历史实验维护独立交包。成员要求和最新版开工包入口见 [按章交付与第一章重整要求](docs/教材驱动课程重构/14-按章交付与第一章重整要求.md) 与 [开工交接说明](docs/教材驱动课程重构/08-JA开工与资源包交接.md)。

## 当前能力

- **知识图谱**：185 节点课程结构（150 旧图谱 + 35 第一章），四类关系语义（依赖/包含/相关/实现）与个人掌握状态双维可视化；薄弱节点一键生成补救路径并直达学习。
- **节点工作台**：知识内容 / 知识详解（静态 HTML 讲义）/ 题目练习 / 实践四页签，常驻图谱导师（GraphRAG，可降级）。
- **题目练习**：按教材章/节/单元、稳定知识节点和个人作答状态筛选的纵向多题卡流，判断/单选/多选/填空服务端判分；连续测评保留为次级入口，支持服务端固定队列、幂等作答、待巩固与历史快照回放。
- **编程实验**：Monaco 编辑器、留页异步判题（Java/C/C++/Go 隔离沙箱）、草稿按用户保存、失败后带上下文的"向导师提问"。
- **交互模拟**：18 项 CPU 调度、连续内存分配、页面置换和磁盘调度 Vue 3 演示播放器，共用 `/experiments/simulation` 与课程活动入口；步骤由确定性纯函数生成，当前全部为运行演示，不写判题/掌握事件。
- **学习记录**：事件时间线（作答/判题/预测，含掌握度前后快照）与只读图谱回放。
- **教学管理**：班级薄弱热力图谱与列表、节点下钻（首次正确率/实验通过率/误区）、题目审核与 JSON 导入、知识维护、用户管理。
- **全局 AI 助手**：可拖动悬浮助手，按用户隔离会话，显式页面上下文。
- 支持账号注册、登录、学生批量管理和密码重置。

掌握度模型：BKT（pLearn 学习转移项对对/错观测均生效，答错后验下降再叠加转移），状态阈值 GREEN≥0.70 且需概念+实践双证据；答错首次约 0.1123（薄弱）。判分、归因、路径全部有确定性规则，AI 不可用时闭环照常运行。

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

## 本地开发与验收（原生 Windows，无 Docker）

已安装 Node.js、JDK 17+，并将 MySQL/Redis/Neo4j 配为 Windows 服务时，在仓库根目录一键启停：

~~~powershell
powershell -ExecutionPolicy Bypass -File ./start-oslab.ps1          # 编译最新后端并启动前后端
powershell -ExecutionPolicy Bypass -File ./start-oslab.ps1 -Status  # 查看进程、端口、数据库服务状态
powershell -ExecutionPolicy Bypass -File ./stop-oslab.ps1           # 只停止本仓库的前后端
~~~

默认后端为 `http://localhost:8121/api`；前端从 8080 起寻找空闲端口，本机通常为 `http://localhost:8081/`，以启动结果为准。启动成功前会检查后端健康状态、前端页面与 `/api` 代理。重复启动会复用已运行的进程；后端代码修改后，先停止再启动即可重新编译。首次缺少前端依赖时自动执行 `npm ci`。

~~~powershell
# 已编译且后端源码没有变化时，快速启动
powershell -ExecutionPolicy Bypass -File ./start-oslab.ps1 -NoBuild

# 也可指定端口和 JDK 路径
powershell -ExecutionPolicy Bypass -File ./start-oslab.ps1 -FrontendPort 8081 -BackendPort 8121 -JavaHome "C:\path\to\jdk-21"

# 查看日志
Get-Content ./tmp/backend.log -Tail 50
Get-Content ./tmp/frontend.log -Tail 50
~~~

脚本不写入数据库密码，也不导入或清空数据。数据库连接、Neo4j 认证、AI 和真实代码沙箱沿用 `application.yml` 及当前环境变量；本机 Neo4j 关闭认证时无需密码。默认停止操作保留 MySQL/Redis/Neo4j；需要同时停止这三项 Windows 服务时使用 `stop-oslab.ps1 -Services`，通常需要管理员权限。原 `run-local.ps1` 继续用于 Docker Compose，和上述原生开发脚本用途不同。

验收命令（前端 `前端代码/osLab-frontend`、后端 `后端代码/osLab-backend` 下执行）：

~~~powershell
# 后端：全量产品测试（遗留模板测试已 @Tag("legacy") 隔离；单独跑用 -Plegacy）
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests

# 前端：lint 0 error、构建、三个 Node 裸测（身份/工作台缓存/图谱渲染）
npx vue-cli-service lint --no-fix
npm run build
node tests/user-identity.js; node tests/node-workbench-cache.js; node tests/knowledge-graph-render.js

# 全链路验收（需先启动回显沙箱 python scripts/dev/sandbox_stub.py，后端指向它）
powershell -ExecutionPolicy Bypass -File scripts/dev/verify-learning.ps1
# 期望 PASS=80 FAIL=0 SKIP=7，失败 exit 1，报告在 artifacts/final-check/
~~~

演示账号（密码 `oslab123456`，可用 `scripts/dev/prepare-demo-accounts.ps1 -DemoPassword <口令>` 幂等准备）：`teacher_demo`（管理员）、`student_demo1/2`（学生，班级 os2026）。

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

- 后端全量测试 211 run / 0 failures / 0 errors / 1 skipped；后端此后未改动。
- 前端 lint 0 error / 54 warning；生产构建 hash `75710cc0a0958966`。
- 图谱 Node/ECharts 测试 26/26；算法播放器 19/19；18 项算法真值 fixture 全部通过。
- 第一章发布：57/57 教学块、103/103 第一章题目、10/10 单元 PUBLISHED，图谱同步 `SYNCED`。
- 学生账号整章走查 62 项检查通过：10 个单元完成、92 个活动完成、错题归因与 BKT 掌握度、目录/题库计数一致、复习不倒退主线。
- 发布与反馈修正回执见 `artifacts/course-first-chapter/20261020/release-receipts/` 与 `artifacts/feedback-fix-20261020/delivery.md`。

L 已人工查看图谱选中/视觉、三个页面标题层级与图例（2026-10-21）；算法动画与教师维护区可按 `artifacts/feedback-fix-20261020/visual-check.md` 复查。本轮未使用浏览器自动化，未提交、推送或部署。旧节点/旧题清理与 18 项演示资源发布尚未执行。

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
