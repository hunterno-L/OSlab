# OSLab C+B 学习图册改造交付记录

日期：2026-10-24
设计规范：`docs/design/2026-10-24-cb-learning-atlas.md`
实施计划：`docs/superpowers/plans/2026-10-24-frontend-cb-redesign.md`
视觉参考：`design/directions/oslab-design-directions.html`（`C+B · 学习图册`）

## 完成范围

学生端：

- 全站壳层：浮层头部、胶囊导航、冷纸点阵画布、荧光笔页面标题
- 课程学习 `/graph`：索引卡图谱、选中荧光标签、①/②/③ 编号批注、详情批注块
- 题目练习 `/assessments`：节点/状态 chip、题卡、选项、对错反馈
- 实验中心 `/experiments`：章节栏、筛选、实验表、空态
- 算法模拟 `/experiments/simulation`：算法目录、播放器、代码行高亮、规则批注
- 实验工作区 `/experiments/code/:id`：说明/编辑器/结果三栏、状态卡
- 学习任务 `/tasks`：统计卡、任务表、任务详情
- 学习记录 `/records`：时间线、待巩固记录卡、失败归因文本
- 首页 `/home`：任务统计、课程入口
- 个人信息 `/account`：资料卡、编辑表单
- 学习资源 `/resources`：筛选、资源卡、预览抽屉
- 节点工作台 `/knowledge/concept/:nodeCode`、课程单元页

教师端：继承新 token、Arco 组件皮肤与壳层；本期未重排教师页面结构。

## 关键实现

- `src/styles/learning-theme.css`
  - 冷纸/钴蓝/荧光笔/珊瑚批注 token
  - Arco 主色/危险/成功/警告色阶、背景、文字、边线、圆角桥接
  - 按钮、输入、表格、标签、卡片、分页、空/错/载入态统一皮肤
  - 图谱 token 与 `KnowledgeGraph.vue` 的 `GRAPH_TOKENS` 逐项同值
- `src/layouts/LearningLayout.vue`、`TeachingLayout.vue`
  - 透明头部容器 + 浮层卡片；点阵画布；页面标题荧光笔与等宽眉题
- `src/components/GlobalHeader.vue`
  - 60px 浮层卡片、胶囊导航、钴蓝选中态
- `src/components/KnowledgeGraph.vue`
  - 8px 索引卡节点、类别浅底、选中荧光标签
  - 新增 `annotations: Record<string, number>` prop
  - 编号只在被批注引用的节点渲染；编号节点自动加宽，避免名称截断
  - 新增 `annotations` 深度 watcher，选中变化后标签立即更新
- `src/components/learning/AnnotationNote.vue`
  - 珊瑚编号 + 楷体批注正文；每个详情面板最多一个批注块
- 页面 scoped CSS 统一替换为 token，不再新增裸色值

## 验证结果

### 契约测试

```bash
cd 前端代码/osLab-frontend
for f in tests/*.js; do NODE_OPTIONS=--max-old-space-size=2048 node "$f"; done
```

全部通过：

- `course-semantics.js`
- `design-system-contracts.js`（新增）
- `knowledge-graph-render.js`（29 项，含新增 annotation 测试）
- `learning-integration-contracts.js`（同时修正了过期的播放器清理断言）
- `member-package-files.js`
- `node-workbench-cache.js`
- `simulation-algorithms.js`
- `simulation-player.js`（20 项）
- `user-identity.js`

### 构建与 lint

```bash
NODE_OPTIONS=--max-old-space-size=4096 npm run build
npx vue-cli-service lint --no-fix
```

- 构建：成功，0 error；191 个既有 warning（非本次新增）
- lint：0 error；175 个 warning（既有 `no-explicit-any` 等）

### 浏览器结构验收

在 `http://localhost:8081` 逐路由检查：

- 首页、题目练习、实验中心、算法模拟、实验工作区、学习任务、学习记录、个人信息、学习资源、节点工作台
- 关键结果：
  - Header 圆角 14px、卡片阴影生效
  - 页面标题荧光笔渐变生效
  - 卡片/筛选栏/表格圆角 14px
  - 激活 chip / 章节 / 算法项为 `#fff7c2`
  - 图谱画布点阵背景生效，图谱高度 480
  - 选中节点详情出现批注块，编号 ③/②/① 与节点标签一致
  - 详情面板无纵向溢出
  - `window.__verrs` 为空，控制台无 error

## 第二轮优化（2026-10-24）

基于运行截图与 C+B 设计稿的差距审计，完成以下修正：

- **浮动 AI 助手**：76px → 52px；松手吸附底部左右角，20px 安全边距；默认 94% 不透明，hover/focus 恢复；已保存的旧位置在加载时也会归位到底部角落，避免压住顶部导航、表格表头或详情操作区。
- **顶部导航**：品牌改为黄底 `OS` 方块 + 文楷 `OSLab`，不再使用旧红笔 Logo；全局 AI 保持在右下角 52px 浮球，不放入头部。
- **图谱**：小图（≤40 节点）使用 140/152/164 × 38/40/42 的宽松索引卡，大图保持 112/120/128 紧凑档；节点底色改为近白类别底（#f8faff / #fbf8ff / #f6fbf9 / #fafbf7），小图非选中节点加 6px 极浅卡片阴影；标签盒比节点窄一圈，缩放态下不再越出卡片；批注节点加宽上限 196px；图例字号 10.5/11.5；课程目录激活单元改为荧光笔底 + 钴蓝内嵌边，不再残留旧浅蓝。
- **实验中心**：实验表改用 `size="small"` 与 6px 14px 内边距，日期和“暂无数据”不换行，行高从约 100px 收到约 51px；通过率改为绿/琥珀/红胶囊（100% 绿，92/78/64 琥珀，41/12 红）。
- **布局对齐**：实验/模拟模式卡改为 `box-sizing: border-box` 的 160px（此前 160 + 内边距实际渲染为 186px），壳层列间距改为 12px；实验页断点从 880 提前到 1024，避免 881–1024 区间的两栏挤压。移除实验页 `.experiments-catalog` 的 `max-width:1280` 和模拟页 `.simulation-view` 的 `max-width:1440` + `margin:auto`——这两层内部居中容器在 1920 宽窗口下会把章节目录/播放器整体推离侧栏，造成约 150px 的假留白。用 610 / 1000 / 1340 / 1920 四档 iframe 实测：1920 为 `160 + 12 + 220 + 12 + 表格 1356`，模拟页 `160 + 12 + 240 + 12 + 播放器 1336`；1340/1000/610 均无横向溢出，窄屏按单列堆叠。
- **题目选项**：A/B/C/D 改为 24px 等宽徽标，与 Markdown 内容同一行；选项总高 48px，默认白底、hover 浅底、选中钴蓝边线 + 浅蓝底 + 蓝色字母徽标；题干行宽 72ch，筛选列与题卡最大 1120px。
- **学习记录**：页面标题改为 30px 文楷 + 荧光笔；记录卡保持 14px 圆角与卡片阴影。
- **算法模拟**：移除页面内大标题，路由增加 `pageTitle: "算法模拟"`，由 `LearningLayout` 统一渲染页标题；页面只保留说明行和工作区。
- **全局细节**：Arco 表格表头/单元格默认 8px 14px 内边距，实验表使用 small 尺寸和 6px 14px；数字列启用 tabular-nums；移除 `transition: all` 与 `outline: none`，补 focus-visible、touch-action、reduced-motion；旧品牌蓝 `#165dff / #2059c9` 统一为 `#2447d8`。

验证：

- 11 个 Node 测试文件全部通过；图谱 29 项、模拟播放器 20 项全绿。
- 生产构建成功：0 error，191 个既有 warning。
- 浏览器结构检查：头部为黄底 OS 标识且无头部 AI；AI 浮球 52×52 位于底部右角；题目选项高 48px、选中态钴蓝；图谱 10 节点为 140–152px、近白卡片 + 6px 阴影、图例 11.5px、激活单元荧光底；记录页标题荧光渐变；实验表行高 51px、日期不换行、100% 为绿色胶囊；算法模拟页无页面内 h1、布局标题显示荧光“算法模拟”；控制台无 error。

## 已知限制

1. **像素级截图**：本次验收时内置浏览器可视表面为 0×0/hidden，无法生成 1440/1024/768 截图。已通过 DOM、computed style、布局尺寸与响应式 CSS 审查验证；建议在真实浏览器中做一次人工视觉复核。
2. **教师端**：只继承 token 与壳层，未重排页面结构；如需同等设计，另开教师端专项计划。
3. **图谱编号**：只覆盖选中节点与最多两条最近关系，不给全图编号；后续可扩展为可配置批注源。
4. **既有 warning**：构建/lint 的 191/175 个 warning 为改动前已存在，本次未清理，以免扩大改动面。
5. **未提交**：工作区包含本次改动与原有未提交改动；未自动 commit，等待确认后再提交。

## 建议下一步

1. 在真实浏览器 1440 / 1024 / 768 三档做一次人工截图复核，重点看图谱详情栏与实验工作区。
2. 教师端（`/teaching/*`）按同一规范做专项重排。
3. 为图谱批注增加“可配置批注源”，把编号从“最近两条关系”扩展为教师指定的关键前置/易错点。

---

## 第三轮：工作台与图谱（2026-10-24）

基于用户确认的 C+B 基线，完成四块真实页面的落地。图谱最终规则（2026-10-24 晚）：小图（≤12 可见节点）给全部可见节点编号，选中节点固定 ③，其余按可见顺序 ①②④…；大图回退为“选中节点 ③ + 两个最近关系 ①②”。小图同时改为确定性放射布局与长度档位卡宽。

### 图谱 `/graph`

- 卡片头改为 `KNOWLEDGE GRAPH` + 知识图谱 + 工具组；搜索/状态/统计收进卡片内。
- 画布左上角新增 ③ 注记条（类型 · 范围 · 关系数）。
- 非邻接边改为灰度虚线；选中邻接/路径为钴蓝虚线；选中节点增加荧光条 `markPoint`。
- 详情改为 NODE / INSPECTOR → 高亮标题 → 定义块 → 批注 / NOTES → 掌握状态 → 掌握证据（题量/作答）→ 关联 chips → 主/次按钮。
- 小图：全部可见节点编号，选中固定 ③；放射布局以选中节点为根（直连一环右侧扇形、其余二环），带确定性正弦扰动，不做力导向抖动。
- 卡宽按标题长度分档 96/120/150/180/210，选中节点额外加宽；大图仍用固定紧凑档。
- 画布新增 `role="img"` + 节点/关系数 aria-label；搜索与状态筛选补 aria-label；节点 tooltip 保留完整名称、类型、状态与描述，作为截断的兜底。

### 学习单元 `/courses/:courseCode/units/:unitCode`

- 新增 hero、OBJECTIVES 卡、活动卡片带、右侧 STUDY LEDGER；活动带横向滚动，真实活动数驱动。
- 当前活动 ①/② 编号、荧光笔带；右侧地图按阅读/练习/实践分组，计数来自真实 `unit.activities`。
- 保留现有阅读/答题/实验入口与保存逻辑。

### 节点工作台 `/knowledge/concept/:nodeCode`

- 头部 hero 卡：NODE / CONCEPT、节点码、荧光标题、定义块。
- 教材摘要按 01/02… 编号，来源行等宽；第一块后有真实焦点批注。
- 导师卡增加 NODE MENTOR 眉题与 ③ 焦点；桌面 sticky 300px，窄屏折叠。

### 实验工作区 `/experiments/code/:id`

- 页面内容切 B：蓝图网格、方角墨线面板、红色 FIG/规格行、红色页签下划线、§ 级 Markdown 标题、页边红注。
- 编辑器浅色头/脚 + 深色代码区；提交按钮墨色方角；评测结果方角。
- 全局 Header、黄色 OS 品牌块、右下角 AI 浮球保持 C+B。

### 验证

- `node tests/*.js` 全部通过（含新增 `workbench-layout-contracts.js`；图谱 31 项）。
- `NODE_OPTIONS=--max-old-space-size=4096 npm run build` 成功：0 error，191 个既有 warning。
- 改动文件 lint：0 error，仅既有 `no-explicit-any` warning。
- 浏览器截图：`/graph` 选中“并发”可见 ③ 注记条、①②关系编号、灰度边、NODE / INSPECTOR 详情；单元页活动带/学习地图；节点页教材编号/导师 ③；实验页 B 主题。
- 桌面 1440 iframe 量测：图谱 266/723/340；单元 266/805/258；节点 1045/300（导师 sticky）；实验 491/654，结果在编辑器下方。

### 已知限制

- 内置浏览器可视表面在验收后段转为 hidden，最终桌面布局用 1440 iframe + DOM 尺寸验证；建议在真实浏览器做一次像素级复核。
- 图谱未做全量编号是用户明确决定；现有 ①②③ 仅用于选中节点与最近关系。
- 教师端未重排。

---

## 性能修复：图谱/工作台加载变慢（2026-10-24）

### 现象

- `/graph` 长时间 0 节点 spinner；`/assessments` 显示“节点加载中…”，0 题。
- 单接口耗时：`/mastery/path` 347s、`/mastery/node` 313s、`/mastery/graph` 85s，全部最终 200 返回。

### 根因

- `src/main/resources/application.yml` 开着：
  - `org.neo4j: DEBUG`
  - `org.springframework.data.neo4j: DEBUG`
  - MyBatis `log-impl: StdOutImpl`
- `KnowledgeGraphService.getGraphSnapshot()` 每次 `/mastery/graph`、`/mastery/node`、`/mastery/path` 都执行一次全图 Cypher；节点切换会同时触发 node + path。
- 逐条 Bolt 记录与 SQL 同步输出到 stdout，把原本几百毫秒的查询放大到几分钟；前端新工作台让这些既有接口被更频繁调用后集中暴露。

### 修复

- `application.yml`：
  - MyBatis `log-impl` → `org.apache.ibatis.logging.nologging.NoLoggingImpl`
  - `org.neo4j` / `org.springframework.data.neo4j` → `WARN`
- 使用 JDK 21 重新打包后重启后端；前端保留 8081，脚本误起的重复 8082 已停止。

### 验证

| 接口 | 修复前 | 修复后 |
| --- | --- | --- |
| `/mastery/graph` | 85s | 0.97s（页面 warm 113ms） |
| `/mastery/node` | 155–313s | 0.36s |
| `/mastery/path` | 152–347s | 0.46s |

- `/graph`：185 节点 / 198 关系，spinner 消失。
- `/assessments`：97 题，节点 chips 加载完成，无 pending 请求。
- 后端健康检查 `/api/health/liveness` 正常。

### 注意

- 排查图数据库问题时再临时打开上述 DEBUG/StdOut；不要在日常开发/演示环境常开。
- 若后续单接口仍超过 1s，再考虑给 `getGraphSnapshot()` 增加短 TTL 缓存，而不是继续加日志。

---

## 图谱视觉校准（2026-10-24 收尾）

- 点阵纸背景：`.12 / 20px`（此前 `.075 / 22px` 几乎不可见）。
- 画布固定 480px 高；搜索 260px、状态筛选 126px，桌面保持同一行。
- 连线：非邻接 `#b0b6bf`、1.4px、3-5 短划、圆头、曲率 0.12；选中邻接 4-4 钴蓝短划。
- 缩放可读性：小图（≤12）标签阈值降到 0.35；低于 0.72 自动切紧凑模式，只保留名称、隐藏编号/圆点，避免文字溢出；正常缩放恢复 ①–⑫ 编号与状态点。
- 验证：默认 12 标签 / 12 编号；0.4 缩放 12 个紧凑名称 / 0 编号；图 689×480；搜索/状态同一行；图谱测试、全量 Node 测试与生产构建全绿。
