# ZhiCore（仓库名 OSLab）

面向操作系统课程的智能学习与实验平台：以教材目录和学习单元组织循序渐进的学习，以知识图谱连接概念、资源和个人证据，把题目作答、代码提交和模拟预测转化为可解释的学习画像。

> 当前产品品牌为 **ZhiCore**；仓库名、前端包名和部分历史文档仍沿用 `OSLab` / `oslab`。
>
> 学生端围绕“学习计划 → 单元学习 → 练习或实验 → 证据与复习”组织，共享一位带课程上下文的 AI 导师；教师端围绕班级跟进、布置练习与批阅组织。当前整合契约见 [共享契约与验收](docs/学习体验与AI对话整合/01-共享契约与验收.md)。历史 AI 升级方案保留在 [历史交付计划](docs/useless/ZhiCore整体AI升级-交付计划.md) 和 [历史开发约束](docs/useless/ZhiCore整体AI升级-开发约束与子代理任务书.md)。

公开入口为 `/welcome`（落地页 + 登录/注册弹层）；登录后学生默认进入 `/graph`（课程学习），`/home` 为学习首页，`/teaching/content` 为教师内容维护入口。教材驱动课程系统已实现，第一至第五章已完成教材内容编排；课程内容按章交付与发布，规范见 [课程与知识图谱总体设计](docs/教材驱动课程重构/01-课程与知识图谱总体设计.md)。

## 当前能力

- **平台入口**：`/welcome` 落地页介绍学习主线、知识图谱/画像、三种练习与学习证据；顶部按钮打开 420px 登录/注册弹层，未登录访问受保护路由会带 `redirect` 回到登录。
- **学习首页**：`/home` 保持当前课程，优先提供到期复习、需要补学、缺少证据待验证的行动，也可按 15 分钟安排；继续学习、画像和下一步推荐使用同一课程范围。节点显示名称，稳定编码用于定位。
- **知识图谱**：课程图谱按概念、算法、机制与实现组织，覆盖五章教材内容；四类关系语义（依赖/包含/相关/实现）与个人掌握状态双维可视化，薄弱节点一键生成补救路径并直达学习。
- **节点工作台**：知识内容 / 知识详解（静态 HTML 讲义）/ 题目练习 / 实践四页签，常驻图谱导师（GraphRAG，可降级）。
- **题目练习**：按教材章/节/单元、稳定知识节点和个人作答状态筛选的纵向多题卡流，判断/单选/多选/填空服务端判分；连续测评保留为次级入口，支持服务端固定队列、幂等作答、待巩固与历史快照回放。
- **编程实验**：Monaco 编辑器、留页异步判题（Java/C/C++/Go 隔离沙箱）、按用户/实验/语言隔离的草稿、失败后带上下文的“向导师提问”。界面区分初始代码与本地草稿；恢复初始代码须确认，并保留替换前草稿。评测边界见下方说明。
- **交互模拟**：18 项 CPU 调度、连续内存分配、页面置换和磁盘调度演示，共用 `/experiments/simulation` 与课程活动入口；另接入 legacy 模拟静态资源（`public/legacy-simulation`）兼容旧版演示。步骤由确定性纯函数生成，当前演示不写判题/掌握事件。
- **学习记录**：事件时间线（作答/判题/预测，含掌握度前后快照）与只读图谱回放。
- **教学工作台**：管理员从 `/teaching/overview` 查看所选班级、课程的到期复习、薄弱与缺少证据人数，查看证据或带目标布置练习；分类人数可能重叠。任务列表支持班级草稿、个人定向草稿与待批阅筛选，进入任务、批阅和返回时保留班级/课程上下文。旧班级分析明确标注“本班全部课程”。
- **班级与教学管理**：教师维护班级与成员（`/teaching/classes`），学生查看我的班级（`/classes`）；保留节点下钻、题目审核与导入、知识维护和用户管理。教师导师入口仅预填班级汇总，教师确认发送后才发起对话。
- **全局 AI 助手**：可拖动悬浮助手，按用户隔离会话，显式页面上下文；升级后接入统一课程会话：来源引用卡可打开定位、工具轨迹面板、下一步卡与补学返回、模型设置页（测试通过才可激活，密钥加密存储、响应脱敏）。
- **AI 导师编排**：每轮最多 3 次工具执行、2 次模型调用；工具含联合检索、本人证据读取、已核验活动选择与页面置换演算核验（`PageReplacer`）；模型或 embedding 不可用时如实标注 `mode=FALLBACK`，不伪装语义检索。
- **学习证据**：帮助等级 `NONE/HINT/ANSWER_REVEALED` 在答题事务前落库并参与证据资格；重复提交、换会话、换模型不新增首次观测；系统/编译错误不降低掌握度。
- **课程隔离**：默认仅开放操作系统课程。数据库系统内容包作为跨课程测试资料保留，默认不出现在正式学习目录中；已有内容和学习历史不删除。启用方式见下方配置。画像、图谱、推荐与路径接口支持 `courseCode`，显式课程范围为空时返回空结果，禁用课程返回“课程当前不可用”。
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
                               │
                               ▼
              模型网关（OpenAI 兼容；chat 与 embedding 可独立端点）
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

默认后端为 `http://localhost:8121/api`。前端从 8081 起寻找空闲端口，实际地址以启动结果为准。启动成功前会检查后端健康状态、前端页面与 `/api` 代理。重复启动会复用已运行的进程；后端代码修改后，先停止再启动即可重新编译。首次缺少前端依赖时自动执行 `npm ci`。

~~~powershell
# 已编译且后端源码没有变化时，快速启动
powershell -ExecutionPolicy Bypass -File ./start-oslab.ps1 -NoBuild

# 也可指定端口和 JDK 路径
powershell -ExecutionPolicy Bypass -File ./start-oslab.ps1 -FrontendPort 8081 -BackendPort 8121 -JavaHome "C:\path\to\jdk-21"

# 查看日志
Get-Content ./tmp/backend.log -Tail 50
Get-Content ./tmp/frontend.log -Tail 50
~~~

脚本不写入数据库密码，也不导入或清空数据。数据库连接、Neo4j 认证、AI 和真实代码沙箱沿用 `application.yml` 及当前环境变量；启动脚本不再强制切换到回显沙箱。后端从 `tmp/backend-runtime/` 的运行副本启动，避免 Windows 锁住 Maven 的构建产物；启停检测同时支持直接运行构建产物和该副本，也识别 npm 启动的前端。默认停止操作保留 MySQL/Redis/Neo4j；需要同时停止这三项 Windows 服务时使用 `stop-oslab.ps1 -Services`，通常需要管理员权限。原 `run-local.ps1` 继续用于 Docker Compose，和上述原生开发脚本用途不同。

验收命令（前端 `前端代码/osLab-frontend`、后端 `后端代码/osLab-backend` 下执行）见下方 [验证](#验证) 章节。

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
| AI_ARK_BASE_URL / AI_ARK_MODEL | 否 | 对话端点与模型名（OpenAI 兼容协议） |
| AI_ARK_EMBEDDING_MODEL | 否 | 联合检索 embedding 模型；留空则降级为关键词+图谱并标注 FALLBACK |
| AI_EMBEDDING_BASE_URL / AI_EMBEDDING_API_KEY | 否 | embedding 独立端点与密钥；留空复用对话端点 |
| OSLAB_AI_MASTER_KEY | 生产建议 | 个人模型密钥 AES-256-GCM 加密主密钥，不得写入仓库 |
| AI_ALLOW_PRIVATE_BASE_URL | 否 | 是否允许个人模型地址指向回环/内网（本地模型部署），默认 false |
| COS_ACCESS_KEY 等 | 否 | 配置后开启对象存储上传 |
| CORS_ALLOWED_ORIGINS | 否 | 仅为额外跨域前端填写精确 HTTPS 来源 |
| OSLAB_LEARNING_ENABLED_COURSES | 否 | 学习端启用课程，以逗号分隔；默认 `course.operating-systems`。对应 Spring 配置 `oslab.learning.enabled-courses` |

完整的容器部署契约见 deploy/README.md 和 后端代码/osLab-backend/DEPLOYMENT.md。

需要验证跨课程功能时，在启动后端前设置：

~~~powershell
$env:OSLAB_LEARNING_ENABLED_COURSES = 'course.operating-systems,course.database-systems'
~~~

恢复正式课程范围时，将该变量设为 `course.operating-systems` 并重启后端。Docker 部署须将变量传入后端容器；只修改宿主终端变量不会改变已运行的容器。管理员内容维护保留历史课程；学习端、课程导师和显式带课程的画像接口遵守启用范围。省略 `courseCode` 的旧画像接口保留兼容行为，新页面均传入已校验的当前课程。

### 实验的评测边界

| 模式 | 当前数量 | 验证范围 |
| --- | --- | --- |
| `AUTO` | 34 | 在线功能测试，只验证对应程序与测试数据 |
| `CONCEPT_HARNESS` | 2（1.1、1.3） | 在线概念子任务；通过不代表完成 Minix 内核构建 |
| `LOCAL_BUILD` | 5（1.4、2.8、3.12、3.13、4.1） | 本地 MIPS 环境构建与人工验收，禁止平台自动判分 |

语言选择与代码内容不一致时会提示，保留已有草稿；需要切回题目代码时使用“恢复初始代码”，也可用“恢复替换前草稿”找回。离开实验页时释放编辑器和模型，避免多次进入持续积累 Monaco 实例。

## 数据初始化

首次启动空数据卷时：

- MySQL 自动导入 `数据库文件/oslab_db.sql`，并按 `deploy/mysql-init/10-load-oslab-db.sh` 加载 v3–v14 升级及种子（含 v13 自测、v14 实验公共前置内容、判题映射和独立注释清理增量，加载后逐项契约校验）。
- Neo4j 自动导入 `数据库文件/graph.db.dump`。
- 图谱导入带有持久化标记，已有图数据不会被自动覆盖。

请不要通过删除生产 Volume 的方式重新初始化数据。

41 项操作系统实验的内容包位于 `content/operating-systems/wangdao-2025/experiments/`：34 项功能判题、2 项概念子任务、5 项本地 MIPS 构建。四章包可用 `node scripts/build-experiment-package.mjs --all` 重建，再用 `node scripts/import-experiment-package.mjs content/operating-systems/wangdao-2025/experiments --validate-only` 离线校验；在线预览和审核应用的参数见该脚本 `--help`。本地构建实验禁止自动评测，引用到学习任务时使用既有人工验收流程。

v14 已完成宿主 gcc、独立数据库真实 API 导入、存量与全新安装迁移的重复应用验证；真实 Docker 沙箱和 5 项本地 MIPS 运行仍待验证，后者资源及关联单元保留草稿。完整矩阵见 [验收记录](artifacts/experiment-judge/validation.md)，演示步骤见 [演示说明](artifacts/experiment-judge/demo.md)。存量库使用 v14 前须满足 SQL 中的课程基线保护；全新安装先加载公共前置 seed。不要使用 `mysql --force` 绕过冲突检查。 随后执行 `oslab_upgrade_v14_experiment_comments.sql` 同步清理后的代码注释；该增量保留审核历史，不改可执行代码和映射。

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

2026-11-11 本轮修复与性能对照见 [bug 审查验收](artifacts/bug-review/validation.md)，教师工作台的交互说明见 [教师验收](artifacts/teacher-workspace/validation.md)。核心修复包括课程范围混用、节点编码直接展示、实验语言与旧草稿混淆、编辑器释放、教师班级计数/筛选与草稿删除，以及导师证据工具的课程边界。

在同一隔离数据库、相同双课程配置下，每接口预热后连续请求 10 次，MySQL 实际 SQL 次数如下：

| 接口 | 优化前 SQL/请求 | 优化后 SQL/请求 | p50 耗时（前 → 后） |
| --- | --- | --- | --- |
| 课程列表 | 111 | 7 | 55.05 → 23.51 ms |
| 操作系统大纲 | 452 | 7 | 145.61 → 29.68 ms |
| 教师跟进 | 203 | 7 | 145.02 → 89.14 ms |
| 班级列表 | 11 | 3 | 13.20 → 11.67 ms |
| 本班任务列表 | 23 | 9 | 41.21 → 59.26 ms |

通过按请求批量读取进度、节点身份、任务统计与提交记录消除重复查询，没有新增全局缓存。小样本耗时存在波动，任务列表虽然查询减少，p50 并未改善；这些结果不代替生产压力测试。完整 p50/p95、样本与 SQL 计数方法保存在验收目录中。

前端（`前端代码/osLab-frontend`）：

~~~powershell
npx vue-cli-service lint --no-fix
npm run build
Get-ChildItem tests/*.js | ForEach-Object { node $_.FullName; if ($LASTEXITCODE -ne 0) { exit 1 } }
~~~

`tests/*.js` 覆盖入口/首页/剧场、导航与设计令牌、题目上下文、图谱渲染、模拟播放器与 18 项算法真值；最近一次本地执行全部通过。生产构建与 lint 结果以本地或 CI 输出为准。

后端（`后端代码/osLab-backend`）：

~~~powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
~~~

全链路验收（需先启动回显沙箱 `python scripts/dev/sandbox_stub.py`，后端指向它）：

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/verify-learning.ps1
~~~

失败时脚本 exit 1；报告写入本地 `artifacts/final-check/`，该目录不纳入版本控制。

AI 主线验收（需运行中的后端与真实模型配置；演示账号由准备脚本幂等创建，初始口令 `123456`，首次登录迁移为 BCrypt）：

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/prepare-ai-demo.ps1 -AdminPassword '<管理员口令>'
powershell -ExecutionPolicy Bypass -File scripts/dev/smoke-ai-demo.ps1 -RequireAiMode
powershell -ExecutionPolicy Bypass -File scripts/dev/eval-retrieval.ps1
~~~

`-RequireAiMode` 在配置真实模型后若本轮出现 FALLBACK 即失败，防止把降级当通过；报告与评价记录写入 `artifacts/ai-platform-demo/`（不纳入版本控制）。此前 AI 升级阶段记录了 smoke 22/22、检索 Top-5 命中 20/20、主线关键问句 8/8、诊断 8 例，以及数据库课程 smoke 21/21；这些是历史结果。本轮 bug 审查未重跑真实模型评价。数据库课程 smoke 需要先显式启用该课程。

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
后端代码/osLab-backend/       Spring Boot 后端
代码沙箱/                     内部隔离判题服务
数据库文件/                   MySQL/Neo4j 初始化数据与 v3–v14 升级及种子
content/                      教材内容包
docs/                         课程、设计与交付文档
scripts/                      内容导入与验收脚本
deploy/                       Docker Compose、环境变量模板和部署文档
run-local.ps1                 Docker 本地服务控制脚本
start-oslab.ps1 / stop-oslab.ps1  原生 Windows 前后端启停
artifacts/                    本地验收报告（不纳入版本控制）
~~~
