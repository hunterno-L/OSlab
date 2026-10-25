# OSLab 入口页与学习首页重设计

日期：2026-10-25
状态：设计已确认，待编写实施计划
范围：公开入口页（`/welcome`、`/user/login`、`/user/register`）与登录后首页（`/home`）
设计基线：`docs/design/2026-10-24-cb-learning-atlas.md`
内容依据：`docs/教材驱动课程重构/01-课程与知识图谱总体设计.md`
结构参考：dancingalgo.cn 的落地页结构与动效节奏；不复制其皮肤、文案、素材或代码

---

## 1. 目标与非目标

### 1.1 目标

1. 把原来的"左品牌 + 右表单"认证页改成平台落地页，先讲清 OSLab 学什么、怎么学、如何证明学会。
2. 登录与注册改为顶部按钮触发的弹层，保留深链、`redirect` 参数与现有校验逻辑。
3. 首页从"任务统计 + 入口卡片"升级为"当前单元 + 学习画像 + 单元进度 + 演算台 + 任务账本 + 目录入口"。
4. 用一套可暂停、可降级的内存分页剧场承担"炫技"动效，并保证它不是装饰背景。
5. 平面设计采用"技术图纸 + 编辑排版"，但只保留承载信息的线、字与标注。
6. 不新增运行时依赖，不改后端接口。

### 1.2 非目标

- 不重做知识图谱、题目、实验、模拟等业务页面。
- 不改变学生默认落地页：登录成功后仍进入 `ROLE_HOME`（学生 `/graph`，教师 `/teaching/overview`）。
- 不添加虚构的统计数据、用户数量、通过率或假进度。
- 不引入 WebGL、视频素材、外部字体或第三方动画库。
- 不复制参考站的深色科技风、视频演示、文案与版式。

---

## 2. 设计原则

1. **C+B 延伸，不换皮**：冷纸、索引卡、荧光笔、钴蓝、珊瑚批注继续作为唯一视觉语汇。
2. **荧光笔只标记"当前"**：当前节拍、当前区块、当前单元。其余位置不用。
3. **等宽只写真实值**：`nodeCode`、地址、页号、判题结果、计数。装饰性等宽标签一律删除。
4. **不用装饰性微文案**：不出现 `OPERATING SYSTEMS`、`AUTH ENTRY`、`01 / 06`、版本角标、页码、账号用途说明。
5. **每个动效都要有解释**：剧场演的是 LRU 的真实步骤；演示区的动画对应真实功能；不做纯装饰循环。
6. **原创边界**：只借参考站的信息架构和"实时演算 + 分节推进"的节奏，视觉与文案全部重写。

---

## 3. 路由与认证流

### 3.1 路由表

| 路径 | 组件 | 行为 |
| --- | --- | --- |
| `/welcome` | `UserLayout` | 公开落地页，无弹层 |
| `/user/login` | `UserLayout` + `UserLoginView` | 落地页 + 登录弹层 |
| `/user/register` | `UserLayout` + `UserRegisterView` | 落地页 + 注册弹层 |
| `/` | 重定向 | 有角色 → `ROLE_HOME[role]`；无角色 → `/welcome` |
| 受保护路由 | 访问守卫 | 未登录 → `/user/login?redirect=<fullPath>` |

`/welcome` 不设置 `meta.access`，视为公开页面；`hideInMenu: true`。

### 3.2 布局职责

- `UserLayout.vue` 只负责：跳过导航、`<main id="main-content">` 包裹 `LandingView`、提供认证共享状态、在认证路由下渲染 `AuthDialog`。
- `LandingView.vue` 负责落地页 header、hero、四个介绍区块和 footer，不再包含表单。
- `AuthDialog.vue` 负责遮罩、面板、焦点陷阱、`Esc` 关闭、关闭后焦点归还。
- `UserLoginView.vue` / `UserRegisterView.vue` 继续作为 `AuthDialog` 内的路由视图，保留现有表单逻辑与可访问语义。

### 3.3 认证行为

1. 落地页 header 在未登录时显示"登录 / 注册"，跳转 `/user/login`；已登录时显示"进入学习"，跳转 `ROLE_HOME[role]`。
2. 登录/注册页签切换沿用 `router.push`，保留 `redirect` 查询参数。
3. 关闭弹层 → `router.replace("/welcome")`，丢弃 `redirect`。
4. 登录成功后：刷新登录态 → 跳转 `redirect`（仅站内绝对路径）或 `ROLE_HOME`。
5. 删除 `oslabAdvanceFlow` 注入与登录成功后 160ms 的人工等待；不再用知识流动画拖延跳转。
6. 已登录用户访问 `/welcome` 或 `/user/login` 时允许停留，用于切换账号或查看介绍。

### 3.4 页面标题与全局小字

- 首页路由保持不设置 `meta.pageTitle`；`LearningLayout` 的眉题只在 `pageTitle` 存在时渲染，因此首页不会出现 `OSLab / LEARNING`。不新增额外标记。
- 落地页、弹层内删除所有装饰性小字；保留字段标签、错误提示、账号规则提示等有效信息。

---

## 4. 平面系统

### 4.1 网格与宽度

| 场景 | 最大宽度 | 栏数 | 栏距 | 外边距 |
| --- | --- | --- | --- | --- |
| 落地页 | 1200px | 12 | 24px | 桌面 48px / 平板 32px / 移动 20px |
| 首页 | 1440px（沿用壳层） | 12 | 12px | 沿用 `LearningLayout` |

- 基线 8px；常用间距 8/16/24/32/48/64/96/120。
- 落地页分区只用 1px 横线，不用通栏色块、不用整页图纸内框。
- 角标细节只保留在演示面板四角，不用于整页。

### 4.2 字体与字号

| 角色 | 规格 | 字体 |
| --- | --- | --- |
| 落地页主标题 | `clamp(44px, 5.2vw, 72px)` / 1.08 | display |
| 落地页区块标题 | `clamp(30px, 3vw, 40px)` / 1.2 | display |
| 首页单元标题 | 32px / 1.25 | display |
| 卡片标题 | 20px / 1.4 | UI 600 |
| 正文 | 16px / 1.8（落地页）· 14px / 1.6（首页） | UI |
| 有效提示 | 13px / 1.5 | UI |
| 真实技术值 | 13px / 1.4 | mono + `tabular-nums` |

- 不下载外部字体，沿用 `--oslab-font-display` / `--oslab-font-ui` / `--oslab-font-mono`。
- 中文标题用文楷/楷体回退；正文、按钮、表格一律 UI 字体。

### 4.3 色彩与形态

- 只使用 `learning-theme.css` 的 `--oslab-*` 令牌；组件内不写裸色值，Canvas 通过 `getComputedStyle` 读取。
- 荧光笔：当前节拍、当前单元、当前区块标题下缘 36% 高亮。
- 珊瑚批注：缺页、薄弱、补救入口；不做普通装饰。
- 圆角：容器 14px、卡片 12px、控件 9px、标签 999px。
- 阴影：仅浮层和剧场面板允许卡片阴影；列表行用 1px 线，不用阴影。
- 悬停只改背景或边线，不做位移；焦点环 2px 钴蓝、offset 2px。

### 4.4 动效令牌

沿用既有 `--oslab-ease: cubic-bezier(0.2, 0.7, 0.2, 1)`、`--oslab-duration-fast: 160ms`、`--oslab-duration: 220ms`；仅新增：

```css
--oslab-duration-slow: 420ms;
```

不新增颜色令牌。

---

## 5. 内存分页剧场

### 5.1 演示内容

- 算法：LRU。
- 页框：3 个。
- 访问序列：`1, 2, 3, 1, 4`（来自总体设计文档的 LRU 单元样例）。
- 正确步骤：

| 次序 | 访问页 | 结果 | 页框（访问后） | 淘汰 |
| --- | --- | --- | --- | --- |
| 1 | 1 | 缺页 | 1, -, - | 无 |
| 2 | 2 | 缺页 | 1, 2, - | 无 |
| 3 | 3 | 缺页 | 1, 2, 3 | 无 |
| 4 | 1 | 命中 | 1, 2, 3 | 无 |
| 5 | 4 | 缺页 | 1, 4, 3 | 2 |

### 5.2 六拍语义

| 拍 | 名称 | 画面 |
| --- | --- | --- |
| ① | 请求页 | CPU 框发出地址线，目标页号亮起 |
| ② | TLB 未命中 | TLB 书签条不匹配，闪一次珊瑚色边 |
| ③ | 页表遍历 | 账本行被扫过，有效位为 0 |
| ④ | 缺页中断 | 珊瑚色"缺页中断"印章落下，轻微 -4° 旋转，不弹跳 |
| ⑤ | 换入 / 置换 | 磁盘抽屉打开，卡片沿弧线进入页框；被淘汰卡片落回抽屉 |
| ⑥ | 恢复执行 | 页表有效位翻为 1，地址线闭合，页框亮起 |

- 命中访问只走 ① → ②（此时 ② 显示"TLB 命中"）→ ⑥，不出现 ③④⑤。
- 节拍指示只在剧场内部显示，用于解释当前步骤；不进入页面装饰层。

### 5.3 场景模型

新增 `src/components/theater/pagingScene.ts`，纯函数、无 DOM、无随机、无时间依赖：

```js
buildPagingScene({ sequence, frames }) => {
  sequence, frames, accesses, faults, hits, totalWeight
}
access = {
  page, hit, evicted, framesBefore, framesAfter,
  phases: [{ beat: 1..6 | "HIT" | "DONE", label, weight }]
}
```

- 同一输入必须产生完全相同输出，便于测试与回放。
- 组件只负责把权重映射为时长、把状态映射为绘制。

### 5.4 组件接口

`src/components/theater/PagingTheater.vue`

```ts
props: {
  mode: "ambient" | "guided";
  playing?: boolean;        // 默认 true
  showControls?: boolean;   // 默认 true
  label?: string;           // 默认 "页面置换 · LRU"
}
emits: { beat: [beat: string] }
```

- `ambient`：落地页使用，循环播放，整体低对比，18 秒一轮。
- `guided`：首页使用，进入视口后播放一轮，可暂停、单步、重播，不自动循环。
- 两种模式共用同一场景和渲染器，只改变时长、循环与对比度。

### 5.5 渲染与性能

- 画布桌面逻辑尺寸 1000×420；窄屏切换为 640×420 紧凑布局，隐藏页表，保留 CPU、TLB、3 个页框与磁盘。
- DPR 上限 2，窄屏 1.5；容器宽度变化时重新设置像素尺寸。
- 图层：底稿细线 → 地址线 → CPU/TLB → 页表账本 → 页框索引卡 → 磁盘抽屉 → 印章与标注。
- 只使用 Canvas 2D；不使用 `shadowBlur` 大面积模糊，不用离屏大图。
- 紧凑布局隐藏页表，三张页框仍横向排列，磁盘抽屉缩短；不把文字压到 11px 以下。
- 帧率：统一上限 30fps，`ambient` 24fps；不做 60fps 全速渲染。
- 暂停条件：`IntersectionObserver` 可见比例 < 0.15、`document.hidden`、用户暂停、`prefers-reduced-motion`。
- 只在播放时请求动画帧；暂停后停止 `requestAnimationFrame`。

### 5.6 降级与无障碍

- `prefers-reduced-motion: reduce`：渲染最终置换步骤的静态画面，并在下方用文字列出五次访问的页号、命中/缺页、淘汰页；不自动播放。
- Canvas 外层 `role="img"`，`aria-label` 描述当前演示；Canvas 本身 `aria-hidden="true"`。
- 节拍变化通过 `aria-live="polite"` 的文本节点播报，拍与拍之间至少间隔 800ms，避免刷屏。
- 控制按钮提供"播放/暂停""上一步/下一步""重播"，点击目标 ≥32px，键盘可达。

---

## 6. 落地页

### 6.1 Header

- 左：黄色 OS 品牌块 + `OSLab`。
- 中：锚点导航 `学习主线` / `知识图谱` / `三种练习` / `学习证据`。
- 右：未登录 `登录 / 注册`；已登录 `进入学习`。
- 宽度 <900px 时隐藏锚点导航，只保留品牌与右侧按钮。
- 不使用装饰性版本号、页码或副标题。

### 6.2 Hero

- 主标题：**沿着教材，把操作系统学明白**
- 说明：**OSLab 用教材式学习单元组织五个章节，用知识图谱和学习画像记录你真正掌握了什么。**
- 主按钮：`登录开始学习`（未登录 → `/user/login`；已登录 → `进入学习`）。
- 次按钮：`查看学习方式`（锚点 `#mainline`）。
- 剧场标题：`页面置换 · LRU`；宽幅 21:9，桌面最大 1200×504；移动使用 640×420 紧凑布局。
- 无眉题、无角标、无说明性小字。

### 6.3 学习主线（`#mainline`）

- 标题：**不是一张知识列表，而是一条学习主线**
- 正文：**五个章节按教材顺序编排，每章拆成有目标的学习单元。第一次学习从首个已发布单元开始；回来时接着上次的位置；遇到薄弱点，系统插入补充单元，完成后回到主线。**
- 右侧演示 `MainlineDemo`：
  - 五章目录：计算机系统概述、进程与线程、内存管理、文件管理、输入/输出（I/O）管理。
  - 第一章为当前章节；演示卡显示"当前单元""单元目标""继续学习"。
  - 提供 `主线 / 补救` 两态切换：补救态在主线中插入一张珊瑚色补充单元卡，学完后指回原位置。
  - 所有示例数据只展示结构，不展示虚构完成率。
- 桌面 5/7 分栏（文案 / 演示），移动单列，演示在上、文案在下。

### 6.4 知识图谱与学习画像（`#graph`）

- 标题：**同一张图谱，看见自己的掌握**
- 正文：**公共图谱保存概念、算法、机制与实现及其关系；每个学生在此基础上叠加概念与实践证据、最近变化和下一步建议。没有证据的节点显示为未学习，不会被误判为薄弱。**
- 右侧演示 `GraphProfileDemo`：
  - 切换 `公共图谱 / 我的画像`。
  - 节点使用真实名称：LRU、FIFO、OPT、缺页中断、局部性原理、页表。
  - 公共态显示节点类型色与关系标签；画像态叠加 `已掌握 / 待巩固 / 薄弱 / 未学习`，并展示一个"暂无实践证据"的节点，说明不按薄弱处理。
  - 状态必须带文字，不能只靠颜色。
- 演示面板右上角显示一次"示例"标记，字号 12px、非等宽；不添加其他小字。

### 6.5 三种练习（`#practice`）

- 标题：**理解之后，随时练**
- 正文：**题目练习、算法模拟和编程实验都能独立进入，也能被学习单元引用。演示覆盖 CPU 调度、内存分配、页面置换和磁盘调度。**
- 演示 `PracticeTabsDemo` 三个页签：
  1. `题目练习`：一道关于缺页中断处理者的单选题，选项可选中，展示解析区的结构，不显示虚构正确率。
  2. `编程实验`：C 语言 LRU 片段 + 判题结果区结构；不显示虚构通过数量。
  3. `算法模拟`：CPU 调度甘特图（FCFS 或 RR），进入视口后播放一次，可重播。
- 页签使用真实 ARIA tab 语义；切换即时完成，不做阻塞式过渡；移动端页签横向滚动。

### 6.6 学习证据（`#evidence`）

- 标题：**进度不是打勾，是证据**
- 正文：**活动进度、单元进度、达标情况和节点掌握分开记录；一次真实作答只计一次证据，历史变化可以回放。**
- `EvidenceLedger` 四行：

| 名称 | 含义 | 触发来源 |
| --- | --- | --- |
| 活动进度 | 是否阅读、运行、提交或完成指定活动 | 真实活动行为 |
| 单元进度 | 必需活动完成比例与当前位置 | 活动进度聚合 |
| 达标情况 | 单元检查点是否通过、建议复习什么 | 服务端测评或判题 |
| 节点掌握 | 累积的概念与实践证据 | 学习事件与 BKT |

- 四行用横线分隔；数字只在有真实值时出现；落地页为静态说明，不展示个人数据。

### 6.7 Footer

- 左：`OSLab 操作系统学习平台`。
- 中：`平台简介` / `课程入口` / `学习资源` / `登录`。
- 右：`© 2026 OSLab`。
- 未登录时 `课程入口`、`学习资源` 指向 `/user/login`；登录后指向真实路由。

---

## 7. 登录 / 注册弹层

### 7.1 结构

- 遮罩 `rgba(32, 36, 43, 0.32)`，面板白底、1px `--oslab-line`、14px 圆角、浮层阴影。
- 面板宽 420px；`role="dialog"`、`aria-modal="true"`、`aria-labelledby` 指向页签区域标题。
- 顶部：`登录 / 注册` 页签 + 关闭按钮；页签下沿用滑动下划线。
- 表单沿用现有字段、校验、记住账号、密码显隐、加载与错误提示。
- 删除：`登录 OSLab，接着探索你的知识路径。`、`从一个知识点开始，记录你的每次进步。`、`账号用于保存你的练习、实验与学习记录。`
- 保留：`账号至少 4 位，密码至少 8 位。`、字段错误提示、登录失败提示。

### 7.2 交互

- 打开：点击 header 按钮或访问 `/user/login`、`/user/register`。
- 关闭：右上角按钮、`Esc`、点击遮罩空白处；关闭后焦点回到触发按钮（深链进入时回到 header 登录按钮）。
- 焦点陷阱：`Tab` / `Shift+Tab` 在面板内循环；打开时焦点落在账号输入框或当前页签。
- 提交中禁用页签切换与关闭按钮，避免请求途中丢失状态。
- 移动端 <640px：改为底部抽屉，宽度 100%，顶部圆角 14px，最大高度 92vh，内容可滚动。

### 7.3 样式

- 输入框高 48px；移动端字号 16px 防缩放。
- 主按钮沿用墨色底、白字、两端对齐文字与箭头。
- 错误文案使用 `--oslab-weak`，不使用泛红背景。
- 弹层不出现装饰性等宽标签或版本号。

---

## 8. 登录后首页

### 8.1 结构

```
（全局 Header、AIAssistant 保持不变）
[问候 + 课程/章节]
[当前单元卡：标题、目标、进度、继续学习、查看课程图谱]   [学习画像卡]
[本单元进度：活动时间线]
[演算台 · 页面置换 LRU：说明 + 打开算法模拟 + 剧场]
[学习任务账本（仅学生）]
[目录入口：课程学习 / 学习任务 / 题目练习 / 编程实验 / 算法模拟 / 学习资源]
[加载 / 空 / 错误状态按区块独立呈现]
```

### 8.2 数据映射

| 区块 | 接口 | 说明 |
| --- | --- | --- |
| 当前课程 | `listCourses()` | 取第一门有已发布单元的课程；无课程则显示空态 |
| 续学位置 | `getCourseResume(courseCode)` | `unitCode`、`activityCode`、`reason` |
| 章节路径 | `getCourseOutline(courseCode)` | 解析 `sectionCode` 对应的章节标题 |
| 当前单元 | `getCourseUnit(unitCode)` | 标题、目标、活动、进度、`currentActivityCode` |
| 学习画像 | `personalGraph()` | `statusCounts`、`evidenceCount`、`recommendedNodeName` |
| 下一步建议 | `personalGraph()` 优先；无值时 `masteryRecommend()` | 只在有真实推荐时展示 |
| 任务摘要 | `getTaskSummary()` | 仅学生；`pending` / `inProgress` / `submitted` / `reviewed` |

### 8.3 当前单元卡

- 章节：`第 N 章 · 标题`，由 outline 解析；不写死章节名。
- 单元标题：`unit.title`；目标：`unit.objectives[0]`。
- 进度：`unit.progress.completedCount / requiredCount`，进度条使用荧光笔渐变。
- 主按钮：
  - `reason === "COURSE_COMPLETED"` → `复习课程`，跳 `/graph`。
  - 其他 → `继续学习`，跳 `unitPath(courseCode, unitCode)?activity=<activityCode>`。
  - `activityCode` 依次取 `resume.activityCode`、`unit.currentActivityCode`、第一个未完成活动。
- 次按钮：`查看课程图谱` → `/graph`。
- 无课程/无单元：显示"暂时没有可继续的学习单元"，提供 `进入课程学习` → `/graph` 和 `重试`。
- 文案不使用"补救模式"推断：续学接口没有该状态，补救入口统一放在画像卡。

### 8.4 学习画像卡

- 标题：`我的学习画像`。
- 四类计数：`已掌握 GREEN / 待巩固 YELLOW / 薄弱 RED / 未学习 GRAY`，与图谱页文案一致。
- 分布条：按四类计数比例绘制，分段使用状态色；无数据时显示"暂无学习证据"，不画空条。
- 证据：`有效证据 N 条`，N 来自 `evidenceCount`；为 0 时不显示该行。
- 下一步：`recommendedNodeName` 存在时显示"下一步：<节点名>"，链接到 `/knowledge/node/<encodeURIComponent(name)>`。
- 底部链接：`查看学习记录` → `/records`；`YELLOW` 或 `RED` 计数大于 0 时追加 `去巩固` → `/records?view=review`，否则不显示。
- 颜色不是唯一信号，每段都有文字与数值。

### 8.5 单元进度

- 活动按 `sortOrder` 排序，类型标签：`READING 阅读`、`WORKED_EXAMPLE 跟随例子`、`DEMO 互动演示`、`PRACTICE 练习`、`ASSESSMENT 测评`、`EXPERIMENT 编程实验`、`SUMMARY 小结`。
- 状态：已完成（勾）、当前（荧光笔底 + 钴蓝点）、未开始（空心）。
- 当前活动：`unit.currentActivityCode`，缺失时取第一个未完成活动。
- 点击活动 → `unitPath(...)?activity=<activityCode>`；活动超过 7 个时横向滚动。
- 移动端只显示"当前 + 前后各一个"，其余通过"查看全部活动"跳到单元页。

### 8.6 演算台

- 标题：`演算台 · 页面置换 LRU`。
- 说明：`改变访问序列，观察缺页与淘汰。`
- 链接：`打开算法模拟` → `/experiments/simulation`。
- 剧场使用 `guided` 模式，宽幅 21:9，最大高度 420px；移动使用紧凑布局。进入视口播放一轮，提供暂停、单步、重播。
- 它是一张功能演示卡，不冒充当前单元内容。

### 8.7 任务账本

- 仅 `userRole === "user"` 显示。
- 一行四格：待接收 / 进行中 / 待批阅 / 已批阅，数值来自 `getTaskSummary()`。
- 整行链接 `/tasks`；移动端 2×2。
- 无任务时显示"暂无学习任务"，保留入口。

### 8.8 目录入口

- 六行，顺序固定：课程学习、学习任务、题目练习、编程实验、算法模拟、学习资源。
- 每行：标题 + 一句真实描述 + 右箭头；1px 横线分隔；hover 背景 `--oslab-panel-soft`；键盘可达。
- 非学生隐藏"学习任务"行。
- 不使用图标卡网格，不使用装饰序号。

### 8.9 状态与错误

- 各区块独立加载：当前单元、画像、任务分别显示骨架或占位，不互相阻塞。
- 单区块失败：显示"暂时无法加载" + `重试`，不显示假数据。
- 页面级无课程：保留问候与目录入口，当前单元卡显示空态。
- 不使用 `<main>`（`LearningLayout` 已提供）；不重复渲染页面大标题。
- 首页路由不设置 `pageTitle`，因此不会出现全局眉题。

---

## 9. 响应式

| 断点 | 落地页 | 首页 |
| --- | --- | --- |
| ≥1200 | 12 栏；hero 居中文字 + 21:9 剧场；区块 5/7 分栏 | 当前单元 7 / 画像 5；剧场 21:9 |
| 768–1199 | 区块 6/6；剧场 21:9 | 当前单元与画像上下排列；剧场 21:9 |
| <768 | 单列；header 只留品牌 + 按钮；剧场紧凑布局 | 单列；任务 2×2；入口单列；剧场紧凑布局 |
| <640 | 登录弹层变底部抽屉 | 同上 |

- 所有断点检查横向溢出、文字截断与点击目标（桌面 ≥32px、移动 ≥40px）。
- 移动端输入字号 16px；剧场页表隐藏帧号列，页框 2 列。

---

## 10. 可访问性

- 落地页保留跳过导航与唯一的 `<main id="main-content">`。
- 首页不嵌套 `<main>`。
- 所有交互元素有 `:focus-visible` 2px 钴蓝环。
- 页签、切换、弹层使用正确的 `role` / `aria-selected` / `aria-controls` / `aria-modal`。
- 状态标签带文字，颜色不是唯一信号。
- 剧场有可读标签、节拍播报与静态降级。
- 正文对比度 ≥4.5:1；`--oslab-muted` 只用于非关键信息。
- `prefers-reduced-motion` 下关闭剧场循环、页面入场、进度条生长与页签过渡。

---

## 11. 性能

- 不新增 npm 依赖。
- 全站只新增一个 Canvas（剧场）；落地页其余演示使用 DOM/SVG。
- 剧场离屏、切后台、用户暂停时停止 `requestAnimationFrame`。
- 剧场统一 24/30fps 上限；颜色令牌在挂载与尺寸变化时缓存，不在每帧读取 `getComputedStyle`。
- 测试、lint、构建串行执行；验证结束后停止 dev server，不保留后台进程。
- 所有演示动画在进入视口后才初始化；`content-visibility: auto` 可用于落地页首屏以下区块。
- 不加载图片、视频或外部字体；图标使用内联 SVG。
- 构建时使用现有配置；不修改 webpack 与 Node 内存参数。

---

## 12. 文件计划

### 12.1 新增

| 文件 | 职责 |
| --- | --- |
| `src/views/landing/LandingView.vue` | 落地页 header、hero、四个区块、footer |
| `src/components/landing/MainlineDemo.vue` | 五章目录 + 主线/补救切换 |
| `src/components/landing/GraphProfileDemo.vue` | 公共图谱 / 我的画像切换 |
| `src/components/landing/PracticeTabsDemo.vue` | 题目 / 实验 / 模拟三页签 |
| `src/components/landing/EvidenceLedger.vue` | 四类证据说明 |
| `src/components/auth/AuthDialog.vue` | 认证弹层、焦点陷阱、Esc 关闭 |
| `src/components/theater/PagingTheater.vue` | Canvas 宿主、控制、播报、降级 |
| `src/components/theater/pagingScene.ts` | 纯函数场景模型与 LRU 步骤 |
| `src/components/home/ProfileCard.vue` | 学习画像卡 |
| `src/components/home/UnitTimeline.vue` | 单元活动时间线 |
| `src/styles/auth-entry.css` | 认证弹层与表单样式（`#userLayout` 命名空间） |
| `tests/paging-scene.js` | 场景模型单元测试 |
| `tests/landing-contracts.js` | 路由、弹层、微文案、动效降级契约 |
| `tests/home-contracts.js` | 首页接口、结构、无假数据契约 |

### 12.2 修改

| 文件 | 改动 |
| --- | --- |
| `src/layouts/UserLayout.vue` | 改为落地页外壳 + 弹层宿主；删除旧左右分栏与知识流 |
| `src/views/user/UserLoginView.vue` | 删除副标题、账号说明、`advanceFlow` 与 160ms 等待 |
| `src/views/user/UserRegisterView.vue` | 删除副标题与账号说明；保留规则提示 |
| `src/router/routes.ts` | 新增 `/welcome`；`/` 无角色回退 |
| `src/views/HomeView.vue` | 按第 8 节重写 |
| `src/styles/learning-theme.css` | 新增动效令牌 |
| `tests/ui-guideline-contracts.js` | 如结构断言仍适用则保留；必要时补充 `/welcome` 与弹层检查 |

### 12.3 删除

- `src/components/auth/AuthFlowCanvas.vue`：重写后无引用，删除，避免死代码。

---

## 13. 测试与验收

### 13.1 自动检查

1. `node tests/paging-scene.js`
   - 序列 `1,2,3,1,4`、3 页框：命中 1 次、缺页 4 次；第 5 次淘汰 2。
   - 相同输入两次输出深度相等。
   - 每次访问的 `framesAfter` 与 LRU 规则一致。
   - 命中访问不产生 ④⑤ 拍。
2. `node tests/landing-contracts.js`
   - 路由包含 `/welcome`；`/` 无角色回退 `/welcome`。
   - `AuthDialog` 存在且含 `aria-modal`、`Esc`、焦点归还。
   - `UserLayout` 不再包含左右分栏表单结构与 `AuthFlowCanvas`。
   - 落地页源码不包含装饰性微文案：`OPERATING SYSTEMS`、`AUTH ENTRY`、`01 / 06`、`2026.10`。
   - 剧场包含 `prefers-reduced-motion` 与 `IntersectionObserver`。
3. `node tests/home-contracts.js`
   - 首页调用 `listCourses`、`getCourseResume`、`getCourseOutline`、`getCourseUnit`、`personalGraph`、`getTaskSummary`。
   - 不包含 `<main`；不包含写死的假统计；`/home` 路由不设置 `pageTitle`。
   - 入口包含题目练习、编程实验、算法模拟三个独立链接。
   - 包含区块级错误与重试。
4. 既有测试全绿：`node tests/*.js`。
5. `npm run lint` 无新增错误。
6. `npm run build` 成功。

### 13.2 人工验收

- 1440 / 1024 / 768 / 390 四个宽度截图检查：无横向溢出、无遮挡、无不可读文字。
- 未登录访问 `/` → 落地页；点击登录 → 弹层；登录成功 → 学生 `/graph`。
- 深链 `/graph` 未登录 → `/user/login?redirect=/graph`，弹层打开；登录后回到 `/graph`。
- 关闭弹层 → `/welcome`；`Esc`、遮罩、关闭按钮三种方式均可用。
- 注册页签、记住账号、密码显隐、错误提示全部可用。
- 首页：当前单元、继续学习、画像、任务、六个入口均来自真实接口；断网时显示区块级错误与重试。
- 剧场：可暂停、单步、重播；离开视口与切后台停止；减少动态下显示静态帧与文字步骤。
- 键盘：Tab 顺序合理，弹层焦点不逃逸，所有入口可激活。

---

## 14. 风险与对策

| 风险 | 对策 |
| --- | --- |
| Canvas 场景复杂导致性能和维护成本上升 | 纯函数场景模型、单画布、固定几何、离屏暂停、DPR 上限 |
| 首页接口失败造成整页空白 | 区块独立加载与重试，不显示假数据 |
| 弹层与路由状态不一致 | 路由驱动打开状态；关闭统一回 `/welcome`；契约测试覆盖 |
| 视觉偏离 C+B | 只用令牌；新增测试检查关键令牌与禁用微文案 |
| 登录流程回归 | 保留现有表单逻辑与校验；人工走完登录/注册/深链/重定向 |
| 参考站被误认为抄袭 | 只保留结构节奏；视觉、文案、图形与代码全部原创 |
