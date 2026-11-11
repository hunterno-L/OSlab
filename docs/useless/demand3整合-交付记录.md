# demand3 与学生学习记录整合——交付记录

日期：2026-10-22
状态：阶段一至五代码与自动化验证完成；未部署、未提交、未推送。

## 1. 交付范围

- 知识图谱节点、关系、标签与确定性布局改造。
- 算法演示“中部演示/输入 + 右侧常驻真实 TypeScript 实现代码”布局，补齐 MLFQ、初始分区、速度、页面 60 帧、磁盘 1–200、解析面板。
- 学生学习记录按“一次学习活动”聚合，接入学习任务接受/提交/批阅、单元完成和普通算法演示运行记录。
- 新增活动记录查询契约与独立运行记录表。

阶段一问题清单与 18 项功能矩阵见：`docs/demand3整合-阶段一基线对照.md`。

## 2. 改动文件

### 前端

- `前端代码/osLab-frontend/src/components/KnowledgeGraph.vue`
- `前端代码/osLab-frontend/src/components/learning/AlgorithmDemoPlayer.vue`
- `前端代码/osLab-frontend/src/views/learning/SimulationView.vue`
- `前端代码/osLab-frontend/src/views/learning/LearningRecordsView.vue`
- `前端代码/osLab-frontend/src/api/learning.ts`
- `前端代码/osLab-frontend/src/simulation/types.ts`
- `前端代码/osLab-frontend/src/simulation/cpu.ts`
- `前端代码/osLab-frontend/src/simulation/allocation.ts`
- `前端代码/osLab-frontend/src/simulation/page.ts`
- `前端代码/osLab-frontend/src/simulation/disk.ts`
- `前端代码/osLab-frontend/src/simulation/playback.ts`
- `前端代码/osLab-frontend/src/simulation/catalog.ts`
- `前端代码/osLab-frontend/src/simulation/codeMaps.ts`
- `前端代码/osLab-frontend/scripts/generate-real-code-maps.js`（从真实源码生成代码面板映射）
- `前端代码/osLab-frontend/tests/knowledge-graph-render.js`
- `前端代码/osLab-frontend/tests/simulation-algorithms.js`
- `前端代码/osLab-frontend/tests/simulation-player.js`

### 后端

- `后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/controller/LearningRecordController.java`
- `后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/learning/LearningActivityService.java`
- `后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/mapper/LearningActivityRecordMapper.java`
- `后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/model/entity/LearningActivityRecord.java`
- `后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/model/dto/learning/LearningSimulationRunRequest.java`
- `后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/model/vo/learning/ActivityNodeRefVO.java`
- `后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/model/vo/learning/LearningActivityVO.java`
- `后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/model/vo/learning/LearningActivityPageVO.java`
- `后端代码/osLab-backend/src/test/java/com/yupi/springbootinit/learning/LearningActivityServiceTest.java`

### 数据库与部署初始化

- `数据库文件/oslab_upgrade_v9_learning_activities.sql`（新增 `learning_activity_record` 表）
- `deploy/docker-compose.yml`（挂载 v9）
- `deploy/mysql-init/10-load-oslab-db.sh`（加载顺序 10/10 + 契约校验）

### 文档

- `docs/demand3整合-阶段一基线对照.md`
- `docs/demand3整合-三项问题修正计划.md`
- `docs/demand3整合-交付记录.md`（本文件）

## 3. 实现摘要

### 3.1 知识图谱

- 节点改为三档紧凑尺寸：104×32 / 116×34 / 128×36；名称单行省略，完整名称在 tooltip/详情栏。
- 掌握状态改为名称前彩色圆点；标题保持正文色，弱状态不再整体变淡。
- 移除 ECharts force 布局，改为确定性布局：章节分组、章节内按 sectionCode/输入顺序、章节块按画布比例行列装箱；同一拓扑坐标稳定。
- 连线：依赖 1.8px、包含 1.5px、相关/实现 1.6px；选中/悬停邻接 +0.6px；非邻接透明度 0.55。
- 关系标签：水平短标签；优先悬停/选中邻边；按缩放层级限制数量（默认最多 18 条，放大后最多 36 条）；按占用网格与节点矩形做避让；关闭开关不残留文字。
- 视野适配改用公开 `graphRoam`（zoom + dx/dy），删除对 `chartView._controllerHost` 的访问；节点与标签按 `nodeScaleRatio: 1` 同比缩放，相对间距恒定。
- 缩放低于 0.72 隐藏节点文字与关系文字，节点仍可点击；筛选后悬停/选中残留守卫保留。

### 3.2 算法演示

- 布局：中部输入 → 动画/步骤 → 过程说明；右侧固定真实实现代码面板，桌面 sticky 独立滚动；≤1180px 改为上下布局；左侧算法目录可折叠。
- CPU：进程行增删、到达/运行/优先级；RR 时间片；MLFQ 支持 2–6 级队列与每级独立时间片，默认 `[1,2,4]`，步骤 detail 输出进入/降级/抢占/完成事件。
- 分区分配：新增多块初始空闲分区编辑（最多 12 块，总大小自动求和），保留 ALLOC/FREE 操作；分配/回收/合并仍由 `allocation.ts` 单一结果源生成。
- 页面置换：物理块 1–60，访问序列最多 200 步；序列芯片横向滚动，帧位区域可滚动。
- 磁盘调度：磁道范围 1–200；SCAN 端点为 200/1，C-SCAN 回跳到 1/200 且回跳不计寻道；播放器刻度同步。
- 播放速度：保留 0.5/1/2 预设，新增 0.1 步长减速/加速，范围 0.1–4.0；播放/暂停/重置/切换算法均清理定时器。
- 解析：18 项目录均增加“算法解析：思想与特点”可展开面板；代码面板展示 `src/simulation/*.ts` 的真实 TypeScript 实现摘录，标注源文件与实现符号，并保留步骤行高亮。
- 普通演示完成一次完整运行后上报活动记录（不写掌握证据）；逐步播放到末尾只上报一次，重置未运行/运行失败不上报。

### 3.3 学习活动记录

新增接口：

- `GET /api/learning/records/activities?type&courseCode&unitCode&nodeCode&startTime&endTime&current&pageSize`
- `POST /api/learning/records/simulations`

活动 VO 主要字段：`activityId/activityType/title/status/result/occurredAt/courseCode/unitCode/activityCode/taskCode/taskItemCode/roundNo/resourceType/resourceId/algorithmKey/summary/inputSnapshot/resultSnapshot/nodeRefs/evidenceRefs/replayEventId/returnRoute`。

归并规则：

- `ATTEMPT/JUDGE/SIMULATION` 按 `eventGroupKey` 归并为一条活动；无组键的历史事件各自成条。
- 同组多节点保留全部 `nodeRefs`（before/after/result/independent/eventId）；主时间线只显示一条。
- `STATE_BASELINE` 不进入活动时间线；`SYSTEM_ERROR` 可追溯但不计独立证据。
- 学习任务：接受记录 + 每轮正式提交/批阅/退回记录；接受记录摘要包含当前任务状态与草稿块数；提交摘要包含状态、分数、逾期、批阅和最多 5 个原文件名（不暴露存储 key）。
- 单元活动：读取 `learning_activity_progress` 的已完成记录，回跳对应课程单元/活动。
- 普通演示：写入 `learning_activity_record`，与 `learning_event` 分离，不触发 BKT；记录 algorithm key、标题、输入快照、结果摘要、课程/单元/活动上下文与运行时间。
- 记录页筛选：活动类型、知识点名称/编码、课程、单元、日期；分页按活动数；节点证据默认折叠；提供资源回跳、提交详情、历史快照回放和运行输入恢复。

## 4. 2026-10-24 三项反馈修正

1. **关系标签不显示**
   - 关系标签可见性与节点文字阈值解耦（关系 0.25，节点 0.72）。
   - 移除边长度 `<170` 的硬过滤，改为缩放后的标签尺寸 + 节点/标签避让；标签上移 8px。
   - 选中/悬停邻边优先显示；无选中时默认最多 18 条，放大后最多 36 条。
   - 新增 2 项图谱测试：开启后可见、关闭后清空；150 节点默认适配可见避让标签。

2. **真实代码联动**
   - `codeMaps.ts` 的 18 项 `lines` 改为真实 TypeScript 实现摘录，新增 `sourceFile/symbol/language`。
   - 面板标题改为“算法实现”，显示源文件与实现符号；不再出现“伪代码”措辞。
   - 修复静态映射把未执行分支也高亮的问题：页面置换按具体出现位置区分 FIFO/LRU 的 `memory.push`，磁盘按 REQUEST/BOUNDARY/JUMP 分支区分 `totalSeek`，分配回收失败不再串到分配失败文案。
   - MLFQ 新增 `cpu.complete/preempt/demote/yield` 子事件，RR 区分完成与时间片轮转，避免所有运行步骤高亮同一组分支代码。
   - 步骤推进时自动滚动代码面板到当前高亮行；窄屏代码面板限制 70vh 并可滚动。
   - 新增测试逐行校验展示内容确实存在于对应 `src/simulation/*.ts`；生成器 `scripts/generate-real-code-maps.js` 可重跑。

3. **学习记录系统错误/空白**
   - 后端启动时幂等执行 `CREATE TABLE IF NOT EXISTS learning_activity_record`；DDL 失败只告警。
   - 活动记录表查询失败时只丢弃普通运行记录，其余事件/任务/单元活动继续返回。
   - 前端活动接口失败时回退到既有 `/learning/mastery/timeline`，显示警告横幅，不再整页“系统错误”。
   - 修复记录卡片模板调用未定义 `activityTypeName` 导致有数据时整页空白的问题；补充前后端测试。
   - 已在本地 8081/8121 以临时学生账号实测：记录页可渲染运行记录卡片，模拟页代码高亮随步骤滚动到可视区。

## 5. 执行的验证命令与结果

### 前端

工作目录：`前端代码/osLab-frontend`

| 命令 | 结果 |
|---|---|
| `NODE_OPTIONS=--max-old-space-size=1536 npx vue-cli-service lint --no-fix` | 0 errors，175 warnings（均为既有 `no-explicit-any` 等警告） |
| `node tests/simulation-algorithms.js` | 通过：18/18 可运行，CPU/页面/分配/磁盘 truth fixture 与边界检查通过 |
| `node tests/simulation-player.js` | 20 项 PASS |
| `node tests/knowledge-graph-render.js` | 28 项 PASS |
| `NODE_OPTIONS=--max-old-space-size=4096 npm run build` | 构建成功（约 60–140s；2GB 堆上限会 OOM，项目 Monaco 打包需要更高堆） |

新增回归覆盖：MLFQ 2/6 级与每级时间片、多初始分区、页面 60 帧/200 步、磁盘 1/200 端点与回跳计费、SCAN 端点改为 200/1 后的寻道长度、速度 0.1/4.0 边界、深链 `queueSlices`/`initialPartitions`、18 项解析内容存在性。

### 后端

工作目录：`后端代码/osLab-backend`

环境说明：本机默认 JDK 8 无法满足项目 `source/target=14`，使用 `JAVA_HOME=C:/Users/one/.jdks/ms-21.0.10` 执行。

| 命令 | 结果 |
|---|---|
| `./mvnw.cmd -q -DskipTests compile` | 通过 |
| `./mvnw.cmd -q -Dtest=LearningActivityServiceTest,LearningEventObservationTest,ActivityContextServiceTest test` | 通过：7 + 5 + 1 项，0 failures / 0 errors |

`LearningActivityServiceTest` 覆盖：eventGroupKey 多节点归并、节点过滤保留同组全部节点、任务接受/提交 + 单元完成 + 普通演示运行接入、纯演示不写学习事件、超大输入快照拒绝、用户隔离字段存在。

## 6. 未完成/未执行事项

1. **未做浏览器交互与截图验证**：本次未启动前后端服务，也未对本地数据库应用 v9 迁移（避免改动本地共享数据）；因此图谱 1440px/窄屏截图、活动时间线实际点击、任务/运行记录真实回放未在浏览器中人工验证。构建与自动化测试已通过，但不能替代交互验收。
2. **v9 迁移需在目标库执行**：存量库需手动应用 `数据库文件/oslab_upgrade_v9_learning_activities.sql`；新库通过更新后的 MySQL 初始化脚本自动加载。
3. **任务文件/内容块**：时间线展示提交快照中的原文件名与草稿块数量；逐项内容编辑、文件上传仍在任务详情页完成，未在时间线内重新实现教师任务管理。
4. **需要真实数据库的 Spring 集成测试**（如 `LearningMasteryServiceTest`）未执行；它们依赖本地演示库/Neo4j 数据前置。
5. 后端完整 `mvn test` 未执行，只执行了与本次改动直接相关的单元测试；未声称全量后端测试通过。
6. 工作区在本轮开始前已存在多项未提交改动（教师任务/学习闭环等历史工作）；本次只修改了第 2 节列出的文件，未回退或提交其他改动。

## 7. 验收对照

| 验收项 | 状态 |
|---|---|
| 图谱缩放/适配/筛选/关系标签/高亮 | 代码与 26 项渲染测试通过；未做浏览器截图 |
| 150+ 节点紧凑可读 | 确定性章节布局 + 三档节点 + 缩放层级标签；渲染测试覆盖 150 节点、筛选、窄屏 resize |
| 左侧目录可折叠、中部演示、右侧常驻真实实现代码 | 已实现；宽屏 grid、窄屏上下布局；构建通过 |
| 18 项功能逐项对照 | 矩阵与 18 项默认输入/算法 fixture 通过；旧资源差异项已补齐 |
| 动画/步骤/指标/代码同一结果源 | 播放器只消费 `simulation/*` 输出与 codeMaps 事件映射 |
| 学习记录按活动聚合、可筛选/回跳 | 新接口 + 记录页活动卡片；后端 5 项契约测试通过 |
| 多节点活动不刷屏、可展开回放 | eventGroupKey 归并 + nodeRefs 折叠 + 快照回放锚点 |
| 草稿/普通演示/系统错误不造掌握证据 | 纯演示写独立表；SYSTEM_ERROR independent=false；草稿不逐次写时间线 |
| 未部署/未提交/未推送 | 均未执行 |
