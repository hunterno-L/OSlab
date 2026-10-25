# OSLab C+B 学习图册设计规范

日期：2026-10-24
状态：已选定基线（C 学习台 + B 编号批注）
视觉参考：`design/directions/oslab-design-directions.html` 的 `C+B · 学习图册` 模式
适用范围：学生端学习工作区全站；教师端沿用 token 与壳层，不重排页面结构

---

## 1. 方向与论点

**一句话：** 把 OSLab 做成一张带批注的学习台——索引卡承载知识点，荧光笔标记当前进度，编号只在确有批注的地方出现。

- **主体**：操作系统课程的学生（本科二、三年级为主），在课程图谱、题目、实验、任务、记录之间往返。
- **页面唯一任务**：让学生快速判断“我在哪、下一步做什么、依据是什么”。
- **签名元素**：荧光笔。只用于页面标题、当前筛选/选中、学习进度。它标记“现在”，不是装饰。
- **第二元素**：编号批注。编号只在被批注引用的节点上出现，最多三条；不给全图 185 个节点编号。
- **风险**：标题与批注使用楷体/文楷类展示字体。它带来“学习手册”的亲近感，但必须严格限制在标题和批注，不能进入表格、按钮、正文。

**明确不做的：**
- 不在每张卡片上贴胶带、画阴影、加旋转。
- 不给所有图谱节点编号。
- 不用荧光笔涂正文、按钮或整块面板。
- 不为了“丰富”增加无数据支撑的状态条、假统计或装饰性时间线。

---

## 2. 设计令牌

学习主题唯一来源：`前端代码/osLab-frontend/src/styles/learning-theme.css`。
图谱画布内的实色镜像：`src/components/KnowledgeGraph.vue` 的 `GRAPH_TOKENS`，必须逐项与 CSS 同值。

### 2.1 表面与文字

| Token | 值 | 用途 |
| --- | --- | --- |
| `--oslab-canvas` | `#eef0e9` | 页面底色（冷纸） |
| `--oslab-panel` | `#ffffff` | 主面板、卡片 |
| `--oslab-panel-soft` | `#f7f8f2` | 次级面板、表头、输入底 |
| `--oslab-panel-muted` | `#f1f3ea` | 选中行、嵌套面板 |
| `--oslab-ink` | `#20242b` | 主文字 |
| `--oslab-ink-soft` | `#3a4150` | 次主文字、图谱标签 |
| `--oslab-muted-strong` | `#6b7168` | 辅助文字（≥12px） |
| `--oslab-muted` | `#8b9187` | 极弱提示，仅非关键信息 |
| `--oslab-line` | `#dddfd3` | 默认边线 |
| `--oslab-line-soft` | `#eceee6` | 分隔线 |
| `--oslab-line-strong` | `#c9ccbe` | 控件/卡片强调边线 |

### 2.2 品牌、荧光笔与批注

| Token | 值 | 用途 |
| --- | --- | --- |
| `--oslab-primary` | `#2447d8` | 主按钮、链接、选中边线 |
| `--oslab-primary-hover` | `#1a35a8` | hover |
| `--oslab-primary-active` | `#142a87` | active |
| `--oslab-primary-soft` | `#e9edff` | 浅底、focus ring 底 |
| `--oslab-on-primary` | `#ffffff` | 主色上的文字 |
| `--oslab-highlight` | `#f2d64b` | 荧光笔主色 |
| `--oslab-highlight-soft` | `#fff7c2` | 选中/定义浅底 |
| `--oslab-highlight-strong` | `#ffe98a` | 进度渐变终点 |
| `--oslab-annotation` | `#d7614a` | 批注编号、页边注记 |
| `--oslab-annotation-soft` | `#f7ddd6` | 批注浅底 |

**荧光笔规则：**
1. 页面标题：下缘 36% 高度的高亮条。
2. 当前筛选 chip / 选中行 / 选中节点标签：`--oslab-highlight-soft`。
3. 学习进度条：`linear-gradient(90deg, --oslab-highlight, --oslab-highlight-strong)`。
4. 其余标题、按钮、正文一律不用。

### 2.3 学习状态

| Token | 值 | soft |
| --- | --- | --- |
| `--oslab-mastered` | `#3f8f6e` | `#e6f2ec` |
| `--oslab-review` | `#b8892e` | `#f6edd9` |
| `--oslab-weak` | `#b8543f` | `#f6e3de` |
| `--oslab-unseen` | `#6b7168` | `#eeefe9` |

状态标签必须包含文字（已掌握/待巩固/薄弱/未学习），颜色不能是唯一信号。

### 2.4 图谱

| Token | 值 |
| --- | --- |
| `--oslab-graph-surface` | `transparent` |
| `--oslab-graph-node-border` | `#c9ccbe` |
| `--oslab-graph-node-radius` | `8px` |
| `--oslab-graph-category-concept` | `#e8eefc` |
| `--oslab-graph-category-algorithm` | `#f1e9fa` |
| `--oslab-graph-category-function` | `#e4f2ed` |
| `--oslab-graph-category-default` | `#eeefe7` |
| `--oslab-graph-selection` | `#2447d8` |
| `--oslab-graph-selection-ring` | `rgba(36, 71, 216, 0.18)` |
| `--oslab-graph-path` | `#8fa6c4` |
| `--oslab-graph-path-strong` | `#6b809f` |
| `--oslab-graph-path-edge` | `#3f66a5` |
| `--oslab-graph-path-ring` | `rgba(107, 128, 159, 0.28)` |
| `--oslab-graph-review-ring` | `rgba(184, 137, 46, 0.26)` |
| `--oslab-graph-text-soft` | `#6b7168` |
| `--oslab-graph-label-surface` | `#ffffff` |
| `--oslab-graph-highlight-soft` | `#fff7c2` |
| `--oslab-graph-relation-dependent` | `#4f72b8` |
| `--oslab-graph-relation-contain` | `#8b96a6` |
| `--oslab-graph-relation-related` | `#9a948b` |
| `--oslab-graph-relation-implements` | `#b8892e` |
| `--oslab-graph-relation-fallback` | `#b0b6bf` |

图谱节点是“索引卡”，不是纯矩形：
- 小图（≤40 节点）使用 140/152/164 × 38/40/42 的宽松档；大图保持 112/120/128 的紧凑档，避免 150+ 节点互相挤压。
- 白/近白类别底、8px 圆角、1px `#c9ccbe` 边线；小图非选中使用 6px 极浅卡片投影，大图不加投影（150 节点下的性能纪律）。
- 节点底色使用更浅的 `--oslab-graph-node-fill-*`（#f8faff / #fbf8ff / #f6fbf9 / #fafbf7）；图例仍使用类别色，保证图例可辨识。
- 选中：2px 钴蓝边线 + 低透明外圈/浅投影；标签底色 `--oslab-highlight-soft`。
- 状态只用一个 5px 圆点，不改变标题颜色。
- 编号徽标：16px 钴蓝圆形、白色等宽数字，只在 `annotations` 映射存在的节点上渲染。
- 画布背景透明，页面容器用点阵纸背景（22px 点距，透明度约 7.5%）。

### 2.5 字体与字号

```css
--oslab-font-ui: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans SC",
  "PingFang SC", "Microsoft YaHei", sans-serif;
--oslab-font-display: "LXGW WenKai", "Kaiti SC", "KaiTi", "STKaiti",
  "Noto Serif SC", serif;
--oslab-font-mono: "Cascadia Code", "JetBrains Mono", ui-monospace,
  SFMono-Regular, Menlo, Consolas, monospace;
```

| 角色 | 字号/行高 | 字体 |
| --- | --- | --- |
| 页面标题 | 34/1.2（≤1024：28；≤768：24） | display |
| 区块标题 | 18/1.4 | UI 600 |
| 卡片标题 | 15/1.4 | UI 600 |
| 正文 | 14/1.6 | UI |
| 辅助 | 12/1.5 | UI |
| 微型标签 | 10.5/1.4，字距 .14em，大写 | mono |
| 数字/ID/日期 | 等宽 | mono |
| 批注正文 | 12/1.55 | display |

不下载外部字体；使用系统字体栈。中文标题在无文楷环境回退到楷体，再回退到 Noto Serif SC。

### 2.6 间距、圆角、阴影、动效

- 间距：4px 基准，4/8/12/16/20/24/32/40/48。
- 圆角：容器 14、卡片 12、控件 9、标签 999、图谱节点 8。
- 阴影：卡片 `0 8px 24px rgba(32,36,43,.06)`；浮层 `0 16px 40px rgba(32,36,43,.14)`。页面主体不靠阴影分区。
- 动效：160–220ms，`cubic-bezier(.2,.7,.2,1)`；无弹跳；`prefers-reduced-motion` 下关闭。
- 焦点：2px `#2447d8`，offset 2px。

---

## 3. Arco 桥接

`learning-theme.css` 必须在 `arco.css` 之后加载（现有 `main.ts` 顺序已保证）。

### 3.1 色阶三元组

```css
--arcoblue-1: 233, 237, 255;
--arcoblue-2: 214, 221, 255;
--arcoblue-3: 166, 180, 246;
--arcoblue-4: 106, 128, 235;
--arcoblue-5: 26, 53, 168;
--arcoblue-6: 36, 71, 216;
--arcoblue-7: 20, 42, 135;
--arcoblue-8: 16, 34, 108;
--arcoblue-9: 12, 25, 80;
--arcoblue-10: 7, 15, 48;

--red-1: 246, 227, 222;
--red-2: 240, 208, 200;
--red-3: 226, 174, 162;
--red-4: 209, 132, 116;
--red-5: 198, 107, 88;
--red-6: 184, 84, 63;
--red-7: 154, 66, 48;
--red-8: 120, 49, 35;
--red-9: 85, 33, 23;
--red-10: 51, 19, 13;

--green-1: 230, 242, 236;
--green-2: 208, 230, 220;
--green-3: 179, 211, 197;
--green-4: 136, 184, 164;
--green-5: 84, 157, 128;
--green-6: 63, 143, 110;
--green-7: 50, 120, 92;
--green-8: 39, 95, 73;
--green-9: 27, 69, 53;
--green-10: 15, 42, 32;

--orange-1: 246, 237, 217;
--orange-2: 240, 226, 194;
--orange-3: 226, 206, 160;
--orange-4: 210, 182, 120;
--orange-5: 196, 157, 74;
--orange-6: 184, 137, 46;
--orange-7: 154, 112, 36;
--orange-8: 120, 86, 27;
--orange-9: 85, 60, 18;
--orange-10: 51, 36, 10;
```

### 3.2 背景、文字、边线、圆角

```css
body {
  --color-bg-1: #ffffff;
  --color-bg-2: #f7f8f2;
  --color-bg-3: #eef0e9;
  --color-bg-4: #e6e8dd;
  --color-bg-5: #dddfd3;
  --color-text-1: #20242b;
  --color-text-2: #3a4150;
  --color-text-3: #6b7168;
  --color-text-4: #8b9187;
  --color-border-1: #eceee6;
  --color-border-2: #dddfd3;
  --color-border-3: #c9ccbe;
  --color-fill-1: #f7f8f2;
  --color-fill-2: #eef0e9;
  --color-fill-3: #e6e8dd;
  --border-radius-small: 9px;
  --border-radius-medium: 12px;
  --border-radius-large: 14px;
}
```

### 3.3 组件约定

- **Button**：主按钮钴蓝、白字、9px 圆角、36px 高；次按钮白底、`line-strong` 边线；文本按钮钴蓝；禁用态用 `unseen-soft` + `muted`。hover 只加深背景，不位移。
- **Input/Select**：32–36px 高，`panel-soft` 底，默认无边框或 `line-soft`；focus 用 2px 钴蓝环。
- **Table**：表头等宽大写 10.5px、`panel-soft` 底；行高 44px；hover `panel-soft`；选中行 `highlight-soft`；单元格正文 12–13px。
- **Tag**：999px 胶囊，soft 底 + 状态文字色；普通标签用 `panel-muted` 底、`ink-soft` 字，不加边框。
- **Card**：白底、1px `line`、14px 圆角、卡片阴影；嵌套卡片用 `panel-soft`、无阴影。
- **Progress**：轨迹 8px、圆角 999；学习进度用荧光笔渐变；正确率/得分用钴蓝；掌握度用状态色。
- **Modal/Drawer**：14px 圆角、`shadow-overlay`；标题用 UI 600，不用 display。

---

## 4. 壳层

- **Header**：浮起的白色卡片，外边距 12px，高 60px，14px 圆角，1px 边线 + 卡片阴影。导航项为胶囊；当前项钴蓝底白字。品牌标识改为黄底 `OS` 方块 + 文楷 `OSLab`，不再使用旧红笔 Logo。
- **Canvas**：`--oslab-canvas` 冷纸底 + 22px 点阵；点阵透明度约 7.5%，不能影响文字对比。
- **页面标题**：由 `LearningLayout` 的 `meta.pageTitle` 渲染；标题下缘加荧光笔高亮；上方加一行等宽眉题 `OSLab / LEARNING`。
- **内容宽度**：wide 1760px，普通 1440px；桌面页边距 32px，1024 以下 16/12px；侧栏与主区列间距 12px。页面内部不再套 1280/1440 的居中容器，宽度由 LearningLayout 的 wide/normal 容器统一控制。
- **侧栏**：白卡片，目录式；当前项荧光笔底 + 钴蓝点；数字用等宽。
- **全局底栏**：本期不添加。视觉稿里的状态条是示意，正式产品不塞假数据。
- **AI 助手**：52px 浮球，保持在右下角（松手吸附底部左右角，20px 安全边距），默认 94% 不透明，hover/focus 恢复；不放入头部，不作为视觉焦点。
- **算法模拟**：页面标题由 `LearningLayout` 的 `meta.pageTitle` 统一渲染，页面内不再重复大标题，只保留一行说明与工作区。

---

## 5. 组件与页面蓝图

### 5.1 组件模式

1. **SectionCard**：沿用 `.section-card` / `.graph-card` / `.detail-card` 类名，全局重定义为白底、14px 圆角、1px 线、卡片阴影；内部区块用 `panel-soft`。
2. **StatCard**：大号等宽数字 + 12px 标签 + 可选状态点。当前筛选的卡片用荧光笔底，不用边框加粗。
3. **DataTable**：保留现有 Arco 表格语义；只改 token、表头、行高、hover/选中；不换组件。表头/单元格默认 8px 14px 内边距，数字列使用等宽 + tabular-nums。
- **题目选项**：24px 等宽字母徽标 + 题干内容同一行；默认白底，选中钴蓝边线 + 浅蓝底，选项总高 48px；题干行宽 72ch，题卡与筛选列最大 1120px。
4. **Empty / Loading / Error**：沿用 `.oslab-empty` / `.oslab-loading` / `.oslab-error`；改成一个“空白索引卡”视觉：1px 虚线内框、display 字体标题、12px 提示、最多一个主操作。错误态用 `--oslab-weak` 标题，不道歉、不模糊。
5. **AnnotationNote**：新组件 `src/components/learning/AnnotationNote.vue`。
   - props：`index: number | string`、`title?: string`。
   - 结构：珊瑚色编号圆 + 标题 + 楷体正文。
   - 规则：每个详情面板最多 1 个批注块；编号必须能在图谱或列表中对应到具体对象。
6. **AnnotationBadge**：16px 钴蓝圆、白色等宽数字；在图谱节点标签和图例中使用。只给 `annotations` 映射中的节点。
7. **Highlighter**：只按 §2.2 的四处使用；不抽象成组件，用 CSS 类 `.oslab-highlight-text` / `.oslab-highlight-block`。

### 5.2 页面蓝图

| 页面 | 路由/文件 | 布局 | 主操作 | 荧光笔落点 | 批注落点 |
| --- | --- | --- | --- | --- | --- |
| 首页 | `/home` `views/HomeView.vue` | 标题 + 任务 4 卡 + 课程入口 6 卡 | 进入课程学习 | 页面标题 | 无 |
| 课程学习 | `/graph` `views/knowledgeGraph/KnowledgeGraphView.vue` | 目录 264 / 图谱 1fr / 详情 320 | 进入节点工作台 | 页面标题、选中节点标签、路径进度 | 详情面板 ③ 当前节点；节点 ③ |
| 题目练习 | `/assessments` `views/learning/LearningQuestionsView.vue` | 目录 264 + 题卡流 | 连续测评 / 作答 | 页面标题、当前节点 chip、当前状态 chip | 错因/复习提示（有数据时） |
| 实验中心 | `/experiments` `views/question/QuestionsView.vue` | 模式 160 + 章节 220 + 实验表 | 进入实验 | 页面标题、当前章节 | 无 |
| 算法模拟 | `/experiments/simulation` `views/learning/SimulationView.vue` | 统一页标题 + 模式 160 + 算法目录 240 + 播放器 | 运行演示 / 下一步 | 页面标题、当前步骤 | 规则与边界（AnnotationNote） |
| 学习任务 | `/tasks` `views/tasks/MyTasksView.vue` | 4 统计卡 + 任务表 | 打开任务 | 页面标题、当前筛选卡 | 无 |
| 学习记录 | `/records` `views/learning/LearningRecordsView.vue` | 时间线/待巩固 + 记录卡 | 去学习 / 题目练习 | 页面标题、当前 tab | 失败归因批注 |
| 个人信息 | `/account` `views/user/StudentProfileView.vue` | 标题 + 资料卡 | 编辑资料 | 页面标题、头像环 | 无 |
| 学习资源 | `/resources` `views/ResourcesView.vue` | 筛选 + 资源卡网格 | 预览资料 | 页面标题、当前类型 | 无 |
| 实验工作区 | `/experiments/code/:id` `views/question/ViewQuestionView.vue` | 说明/编辑器/结果三栏 | 提交评测 | 当前步骤 | 评测失败原因 |
| 节点工作台 | `/knowledge/concept/:nodeCode` `views/learning/NodeLearningWorkspaceView.vue` | 节点资料 + 活动 | 进入活动 | 当前节点 | 学习建议 |
| 任务详情 | `/tasks/:taskCode` `views/tasks/TaskDetailView.vue` | 任务 + 提交 | 提交/保存 | 当前状态 | 反馈 |

**教师端**（`/teaching/*`）：本期只继承 token、壳层、表格与按钮样式；不重排页面结构，不新增视觉组件。若需要同等设计，另开计划。

---

## 6. 可访问性与响应式

- 正文对比度 ≥ 4.5:1；`--oslab-muted` 只用于非关键信息。
- 状态不能只靠颜色：保留文字标签。
- 所有交互元素有 `:focus-visible` 2px 钴蓝环；键盘可达。
- 点击目标桌面 ≥ 32px，移动 ≥ 40px。
- 断点：1440（默认）、1024（侧栏收窄/标题 28）、768（单列、表格横向滚动、图谱最小高 420）。
- `prefers-reduced-motion` 下关闭高亮渐变过渡、图谱脉冲和悬浮动效。
- 表格在窄屏允许横向滚动；不隐藏列，不用卡片化替代数据表。

---

## 7. 验收清单

- [ ] 所有 token 只在 `learning-theme.css` 定义；页面不新增裸色值（图谱 `GRAPH_TOKENS` 例外且必须镜像）。
- [ ] Arco 主色、危险/成功/警告、背景、边线、圆角全部桥接。
- [ ] Header、页面标题、侧栏、卡片、表格、标签、按钮、输入、分页、空/错/载入态统一。
- [ ] 图谱节点为 8px 索引卡；选中态与路径态、脉冲态互不伪装。
- [ ] 编号批注只在有映射的节点出现；详情面板最多一个批注块。
- [ ] 荧光笔不超过规定落点。
- [ ] 六个核心路由在 1440/1024/768 下无横向溢出、无遮挡、无不可读文字。
- [ ] `node tests/*.js` 全绿；`npm run build` 成功；lint 无新增错误。
- [ ] 键盘可完成：切页、筛选、选中图谱节点、提交表单、打开任务。
