# demand2 人工反馈修正计划

更新：2026-10-20  
执行对象：L 及实施 agent  
依据：`C:/Users/one/Desktop/demand2.txt`、`17-课程学习整合与第一章交付执行计划.md`、`docs/log2-OpenDesign执行计划.md`、当前前端源码及授权算法演示资源。  
范围：节点工作台入口修复、知识图谱节点选中与高亮修复、图谱节点视觉重做、算法模拟独立页面与动画/代码联动恢复。  
边界：本计划只修改前端、模拟器适配层和必要的测试；不修改教材资源包、不重复导入 J v3 知识包、不改变题目或学习事件数据库结构，不删除数字孪生能力，不移除题目、算法模拟、编程实验三个独立入口。

## 1. 人工反馈与修正目标

### 1.1 反馈原文对应的问题

| 编号 | 人工反馈 | 修正目标 | 当前判断 |
| --- | --- | --- | --- |
| F1 | “节点工作台”无法打开 | 从图谱节点详情点击“进入学习”后，稳定进入对应的 `/knowledge/concept/:nodeCode` 或旧名称兼容路由 | 已定位一个确定根因：模板把 MouseEvent 传给了只接受节点键的函数 |
| F2 | “算法模拟”是阉割版，动画效果和代码联动消失 | 点击独立入口进入专门的模拟页面；保留 18 项算法目录、真实步骤、可播放动画、暂停/单步/重置/速度控制，并增加算法过程与伪代码行联动 | 当前有步骤播放器，但只是一次计算后递增 `revealed`，不是授权资源中的对象动画；代码行联动尚未实现 |
| F3 | 图谱节点太丑，需要更凌厉、未来感，并希望掌握度直接用节点文字颜色表达 | 保留 OpenDesign 的克制浅色基线，改成锐利矩形节点、清晰边界、状态文字色；不再使用左状态条/右圆点作为主状态表达 | 需要重做节点视觉，不得把“未来感”实现成霓虹渐变或密集装饰 |
| F4 | 点击节点后高亮异常 | 一次只保留一个明确选中节点；相邻边单独强调；教学路径提示与选中态分离；hover、click、mouseout、切换节点、筛选后均能恢复一致 | 已确认两类高风险原因：自定义 `updateVisual` 刷新路径可能残留 ECharts emphasis 状态；`highlight:true` 与单点 selected 共用同一视觉样式 |

### 1.2 完成定义

完成本计划后，学生可以：

1. 在图谱中单击节点，只看到该节点的选中描边、可读的状态文字和相邻关系强调；再次点击另一个节点后，旧节点恢复普通态，不残留蓝框、粗体或错误的邻接边。
2. 从右侧节点详情点击“进入学习”，稳定打开当前节点工作台；工作台显示正确的节点正文、掌握状态、题目、实践入口和返回上下文。
3. 从顶栏、首页、课程单元或节点工作台进入“算法模拟”时，进入同一个独立模拟页面；页面可以选择 18 项授权算法，运行、暂停、单步、重置和调速均真实生效。
4. 模拟每一步同时展示算法状态、当前事件和对应的伪代码行；没有可靠代码映射时显示“本步骤无代码行映射”，不得伪造高亮。
5. 节点视觉表达“类别”和“掌握度”两个维度：类别用低饱和底色或角标，掌握度直接作用于节点名称文字色；选中蓝框、路径提示和掌握度颜色不能相互覆盖。
6. 保留数字孪生、节点掌握证据、历史回放、题目练习、算法模拟、编程实验、实验导师和教师看板的现有入口及接口契约。

## 2. 已核对的源码与资源基线

### 2.1 节点工作台与路由

- `前端代码/osLab-frontend/src/views/knowledgeGraph/KnowledgeGraphView.vue:615`：节点详情主按钮当前使用 `@click="enterNodeWorkspace"`。
- `KnowledgeGraphView.vue:2101-2110`：`enterNodeWorkspace(keyOrName?: string)` 将 truthy 参数按节点键查找；Arco 事件对象会导致 `Map.get(MouseEvent)`，随后 `!node` 静默返回。
- `前端代码/osLab-frontend/src/router/routes.ts:133-141`：`/knowledge/node/:nodeName` 和 `/knowledge/concept/:nodeCode` 均指向 `NodeLearningWorkspaceView`，路由本身存在。
- `KnowledgeGraphView.vue:1825`：图谱双击入口显式传入节点键，因此与右侧按钮故障不同。

### 2.2 图谱选中与高亮

- `前端代码/osLab-frontend/src/components/KnowledgeGraph.vue:44-60`：注册自定义 `oslabGraphStyle`，更新类型为 `updateVisual`。
- `KnowledgeGraph.vue:321-326`：`selected` 同时由 `selectedId` 和 `node.highlight` 决定；两者会得到相同的蓝色边框与粗体文字。
- `KnowledgeGraph.vue:387-390`：选中节点的相邻边通过 `selectedId` 判断，非邻接边降透明度。
- `KnowledgeGraph.vue:510-545`：节点和关系会重新计算选中 ID 与邻居集合；选中 ID 必须与当前可见节点集合一致。
- `KnowledgeGraph.vue:622-652`：单击延迟 220ms 等待双击判定，单击发出 `node-click`，双击发出 `node-dblclick`。
- `KnowledgeGraph.vue:777-792`：只改变选中、掌握状态或关系标签时走自定义样式刷新，不重建力导向布局。
- `KnowledgeGraphView.vue:1372-1377`：单元映射节点通过 `highlight: true` 进入画布；这不是“当前选中节点”，必须与选中态拆分。
- `KnowledgeGraph.vue:339`：派生图节点当前没有完整保留 `nodeCode`；重名节点会退回名称查找并存在覆盖风险。
- `前端代码/osLab-frontend/tests/knowledge-graph-render.js`：已有 SSR 图谱渲染测试，但缺少节点点击载荷、选中态切换和 hover→click→mouseout 状态断言。

### 2.3 算法模拟与授权资源

- `前端代码/osLab-frontend/src/views/learning/SimulationView.vue:1-179`：已有 `/experiments/simulation` 独立页面、类别目录、算法列表和 `AlgorithmDemoPlayer` 入口；旧 `/learning/simulation` 在 `routes.ts:282-284` 保留重定向。
- `前端代码/osLab-frontend/src/views/learning/AlgorithmDemoPlayer.vue:787-811`：一次性调用本地算法函数生成完整步骤；`836-847` 的播放只是按定时器递增 `revealed`。
- `前端代码/osLab-frontend/src/simulation/catalog.ts`：登记 18 项算法；`types.ts` 的 `DemoStep` 尚无语义事件、动画阶段或代码行映射字段。
- `前端代码/osLab-frontend/tests/simulation-algorithms.js:204-235`：当前只验证 18 项能产生非空步骤和指标，不验证动画、代码联动、课程参数或每项算法真值。
- 授权资源根目录：`E:\web\web\demo_operatingsystem 2025.5.26\demo_operatingsystem`。
- 授权资源代表实现：`src/views/unit3/FCFS.vue:409-510`（进程调度连续动画）、`src/views/unit4/PartitionAllocation_BF.vue:494-559`（分区分配过程动画）、`src/views/unit5/FCFS.vue:447-477`（磁头移动动画）。资源使用 SpriteJS 等连续动画；源码未发现现成的逐行代码高亮面板，因此代码联动需要在 OSLab 当前播放器中新增映射数据。

### 2.4 既有设计约束

- 继续遵循 `docs/log2-OpenDesign执行计划.md` 的浅色、克制、信息密度和真实接口约束。
- 图谱仍是课程结构、个人掌握状态和下一步入口，不强迫学生第一次进入时阅读全图。
- 节点状态来自个人图谱/数字孪生接口；不能因为节点被选中、进入页面或看到正文就修改掌握状态。
- 算法演示与判题、掌握事件保持边界：仅在已有 `eventId` 且课程活动明确要求时计入学习证据。
- 不使用浏览器自动化作为验收手段；采用源码断言、Node/ECharts 渲染测试、lint/build、接口检查和用户人工截图。

## 3. 修正前的统一规则

### 3.1 节点身份规则

所有图谱节点在前端统一使用以下优先级生成身份键：

```text
nodeKey = nodeCode || stableId || String(id) || name
```

要求：

1. `nodeKey` 用于 `selectedNodeKey`、路由 query、节点点击载荷和工作台跳转。
2. ECharts 的 `data.id`、`link.source`、`link.target`、父视图的 `selectedId` 必须使用同一字符串化规则。
3. `displayName` 只用于展示，不能作为唯一身份；重名节点不得相互覆盖。
4. 派生图节点必须保留 `nodeCode`、`stableId`、`name`、`displayName`、`status` 和 `highlightReason` 等回查字段。

### 3.2 三种视觉状态必须分离

| 状态 | 表达方式 | 能否替代另一状态 |
| --- | --- | --- |
| 掌握度 | 节点名称文字色与小型状态文字，如“已掌握／待巩固／薄弱／未学习” | 不能由选中或路径提示替代 |
| 当前选中 | 单个节点的蓝色锐利描边、轻微外圈和右侧详情 | 不能把多个 `highlight` 节点都当作选中 |
| 路径/单元提示 | 细边、角标或低强度背景纹理，并在图例说明 | 不能复用选中蓝框和粗体 |

`node.highlight` 若继续保留，必须改名或转为明确的 `pathHighlighted`／`teachesCurrentUnit` 语义；禁止在 `buildNodeStyle` 中把它直接并入 `selected`。

### 3.3 算法模拟状态规则

播放器状态最少包含：

```text
runConfig      // 深拷贝后的本次输入
steps          // 算法真值步骤
activeStep     // 当前步骤索引
playState      // idle / playing / paused / finished / error
speed          // 播放速度
activeEvent    // 当前事件及对象轨迹
activeCodeLines // 当前步骤对应的伪代码行
```

输入框改变后必须标记“配置已修改，请重新运行”，不能继续播放旧结果，也不能把新输入误解释成旧结果的学习证据。

## 4. 阶段 0：复现与契约冻结

### 目标

先用源码和现有测试固定三个问题的可复现证据，再允许 agent 修改实现。

### 工作项

1. 在 `KnowledgeGraphView.vue` 复现：选中节点 → 点击“进入学习”；记录点击事件、`selectedNodeKey`、路由变化和最终 URL。
2. 在图谱组件复现：hover 节点 A → 单击 A → 移出 → 单击 B → 移出；再切换“只看学习路径”、关系标签开关和章节筛选，记录节点样式、边透明度和右侧详情是否同步。
3. 记录至少一个单元映射节点被 `highlight:true` 的页面状态，确认它不能与当前选中样式相同。
4. 在 `/experiments/simulation` 复现当前 CPU、页面置换、内存分配和磁盘算法的播放、暂停、单步、重置和参数修改；记录哪些区域只是快照，哪些动画仍存在。
5. 对授权资源只做只读盘点：算法名称、输入字段、动画对象、重置入口、播放入口和可复用算法逻辑，不直接复制 Vue 2/Element 页面外壳。

### 交付

- `artifacts/feedback-fix-20261020/reproduction.md`：每个问题的复现步骤、实际结果、预期结果、截图路径。
- `artifacts/feedback-fix-20261020/resource-map.json`：授权资源组件到 18 项当前算法 key 的映射。
- 现有失败测试的最小补充，不在本阶段改生产代码。

### 验收

- F1 的 `MouseEvent` 根因有源码证据。
- F4 至少有一个 hover/click 状态残留或多选视觉冲突的可复现断言；如果某一现象不能稳定复现，报告为风险项而不是写成已确认 bug。
- F2 的“快照播放器”和授权资源的“连续动画”差异有具体组件与代码位置。

## 5. 阶段 1：修复节点工作台入口

### 修改范围

- `前端代码/osLab-frontend/src/views/knowledgeGraph/KnowledgeGraphView.vue`
- `前端代码/osLab-frontend/src/views/learning/NodeLearningWorkspaceView.vue`（仅在入口契约需要时修改）
- `前端代码/osLab-frontend/src/router/routes.ts`（仅在回退或 query 兼容需要时修改）

### 实施要求

1. 将右侧主按钮改为显式无参调用：`@click="enterNodeWorkspace()"`，让函数使用当前 `selectedNode`。
2. 将双击入口、相关节点入口和单元映射入口统一改为传入 `nodeKey`，不传 `MouseEvent`、显示名称或整条事件对象。
3. `nodeContextQuery` 必须携带稳定 `nodeCode`；旧节点只有名称时继续支持 `/knowledge/node/:nodeName`。
4. 跳转前检查当前节点仍在可见图谱中；没有选中节点时按钮禁用或显示明确空态，不静默失败。
5. 返回图谱时保留 `returnTo`、章节、小节、单元和节点 query，恢复右侧详情和画布选中态。

### 验收

- 单击节点后主按钮一次成功进入工作台。
- 直接打开 `/knowledge/concept/:nodeCode` 与 `/knowledge/node/:nodeName` 都能加载正确内容。
- 工作台返回图谱后不丢章节/单元范围，不跳到其他节点。
- `rg` 不再发现把 `enterNodeWorkspace` 作为带隐式事件参数的模板回调。

### 禁止

- 不通过延时、全局变量或“找不到节点就跳默认节点”掩盖错误。
- 不删除节点工作台路由，不把节点详情重新改成弹窗唯一入口。

## 6. 阶段 2：修复图谱点击、高亮与身份链

### 6.1 先修选中模型

1. 在 `KnowledgeGraph.vue` 中把 `selected` 拆为 `isSelected`、`isPathHighlighted`、`isPulsing` 三个变量。
2. `isSelected` 只由规范化后的 `selectedId` 决定；`isPathHighlighted` 只表达单元/补救路径提示，不使用选中蓝框和粗体。
3. 节点名称文字颜色由 `status` 决定；选中只改描边、外圈或角标，不覆盖红/黄/绿/灰文字色。
4. 重新生成图节点时保留 `nodeCode`、`stableId` 和回查所需的业务字段，点击事件发出完整节点数据。
5. 父视图 `selectedNodeKey`、`selectedNodeId` 和组件 `selectedId` 统一从同一个 `nodeKeyOf`／`graphIdOf` 派生，去除名称回查覆盖。

### 6.2 修刷新生命周期

1. 先用现有 ECharts 版本复现 `oslabGraphStyle` 的 hover→click→mouseout 残留。
2. 如果自定义 `updateVisual` 路径确认跳过状态清理，改用标准 `setOption` 的局部数据刷新，或在自定义动作中显式完成清理和状态更新；不得为了保留布局而牺牲状态一致性。
3. 样式刷新必须保留当前 camera、力导向收敛位置和拖拽位置；拓扑变化才允许重建布局。
4. 单击延迟与双击导航继续保留，但点击空白、点击关系线和快速切换节点不得改变选中节点。
5. 筛选把当前节点隐藏时，清除选中态、详情和邻接边，不保留隐藏节点的残留高亮。

### 6.3 关系与标签

1. 关系标签开关只控制关系文本，不改变节点选中状态。
2. 选中节点只强调相邻边；非邻接边保持可读的低透明度，不能全部消失。
3. `dependent` 的学习方向、`contain`、`related`、`implements` 的线型和箭头继续沿用现有语义；本阶段不重新解释数据库关系。
4. 关系标签必须在不同缩放级别保持屏幕字号，不随图谱缩小到不可读。

### 验收

- 选择 A 后只有 A 有选中描边；教学路径节点可以有路径提示，但不再显示为多个选中节点。
- 选择 B 后 A 恢复普通态，B 的详情、文字状态色、邻接边和路由一致。
- hover→click→mouseout 后无旧 emphasis 残留；切换关系标签和筛选后仍成立。
- 使用重复 `displayName` 的合成数据时，点击 A 不会打开 B 的工作台。
- 个人图谱中 RED 节点名称仍为红色，即使节点被选中；GREEN/YELLOW/GRAY 同理。

## 7. 阶段 3：图谱节点视觉重做

### 设计方向

目标是“锐利、未来感、克制”，不是赛博霓虹。基于现有 OpenDesign 浅色工作区，采用：

- 直角或轻微 2px 圆角的矩形节点，不使用胶囊形节点。
- 1px 中性边框；选中时 2px 蓝色锐利描边和一层短距离外圈。
- 节点名称为主要视觉内容，掌握度直接使用文字色；状态文字可在名称下方以 10–11px 显示。
- 类别只使用低饱和底色或左上角小标签，不能与掌握度颜色混淆。
- 关系线保持细、清晰、可解释；重点路径使用蓝色或琥珀色短线强调，不增加发光渐变。
- 长名称最多两行，节点尺寸稳定；全名在 tooltip/详情中展示。

### 实施范围

- `前端代码/osLab-frontend/src/components/KnowledgeGraph.vue`：节点富文本、节点尺寸、状态文字色、选中描边和路径提示。
- `前端代码/osLab-frontend/src/views/knowledgeGraph/KnowledgeGraphView.vue`：图例、路径提示文案、状态图例与节点表达保持一致。
- `前端代码/osLab-frontend/src/styles/learning-theme.css`：只新增必要 token，不在组件内散落新颜色。

### 验收

- 1440px 宽度下节点名称、关系线、右侧详情不重叠。
- 390px 窄视口下图谱可以横向/缩放浏览，节点文字仍能辨认。
- 0.2、1、2.5、4 倍缩放后节点文字保持屏幕尺寸，不出现大面积模糊或全白。
- 图例能解释类别、掌握度、选中和学习路径四种表达。
- 页面不出现大面积渐变、霓虹光晕、无意义装饰节点或一整片同色节点。

## 8. 阶段 4：算法模拟替换与独立页面

### 8.1 页面和入口

1. 保留 `/experiments/simulation` 为唯一正式算法模拟页面；`/learning/simulation` 继续兼容重定向。
2. 顶栏、首页、课程单元、节点工作台均跳转到该页面；课程上下文通过 `algorithmKey`、`nodeCode`、`nodeName`、`sourceActivityCode` 和 `returnTo` 传递。
3. 页面采用独立工作区布局：左侧算法目录，中部动画/步骤画布，右侧过程说明与代码联动；窄屏改为上下顺序，不把动画压缩成不可读缩略图。
4. 从课程活动进入时默认锁定该活动算法；用户主动切换算法后，必须清除当前活动完成资格或显示“当前演示不对应本单元”，不得错误完成原活动。

### 8.2 授权资源迁移方式

1. 以 `E:\web\web\demo_operatingsystem 2025.5.26\demo_operatingsystem` 为只读参考源，逐项登记 18 项算法对应的输入、步骤和动画语义。
2. 不直接嵌入 Vue 2 根应用、旧导航、Element 全局样式或旧路由；将算法逻辑、事件序列和动画语义适配到当前 Vue 3 播放器。
3. 可复用的连续动画类型包括：进程入队/出队/上 CPU/被抢占、内存分区搜索/切分/回收合并、页面换入换出、磁头沿轨道移动。
4. 当前 OSLab 本地真值函数仍作为指标和步骤的单一来源；动画层只能消费步骤事件，不能另写一套会产生不同结果的算法。
5. 对授权资源中没有源码行面板的算法，在 OSLab 中新增维护型 `codeLines` 与 `eventCodeMap` 数据；代码面板展示伪代码或关键步骤，而不是声称复用了原项目代码高亮。

### 8.3 播放器数据契约

将 `DemoStep` 扩展为兼容旧字段的结构：

```ts
type DemoStep = {
  index: number;
  title: string;
  detail: string;
  time?: number;
  tone?: string;
  state?: unknown;
  event?: {
    kind: string;
    objectId?: string;
    from?: string;
    to?: string;
    duration?: number;
  };
  codeLines?: number[];
  activeCodeLines?: number[];
};
```

要求：

- `runConfig` 深拷贝后冻结在本次运行中；输入改变后必须重新运行。
- `play/pause/step/reset/speed` 都是真实状态机操作。
- 动画结束后 `activeStep`、指标、当前对象和代码高亮一致。
- 没有 `eventCodeMap` 的步骤不高亮任意代码行，只显示过程说明。
- `eventId` 是唯一允许写学习证据的条件；无 `eventId` 时只显示演示结果。

### 8.4 四类算法输入契约

补齐 `SimulationView.vue` 当前只解析 CPU 和页面置换参数的问题：

| 类别 | 必须支持的深链输入 | 最低展示 |
| --- | --- | --- |
| CPU 调度 | `processes`、`timeSlice`、优先级/到达/运行时间 | 甘特条、就绪队列、当前进程、调度事件 |
| 页面置换 | `frames`、`accessSequence` | 帧槽位、命中/缺页、换入换出、指针/队列变化 |
| 内存分配 | `memorySize`、操作序列、分配算法 | 空闲区搜索、分割、回收与合并 |
| 磁盘调度 | `head`、`requests`、`direction` | 磁头连续移动、服务顺序、寻道距离 |

参数缺失时使用明确的可编辑示例输入；示例数据必须只用于演示，不能冒充用户学习证据或后端结果。

### 8.5 算法真值回归

1. 复核 `src/simulation/cpu.ts` 中 RR、抢占优先级和 MLFQ 的等待时间/响应时间定义，不能把 `firstStart - arrival` 对所有算法都称为平均等待时间。
2. 为四类算法增加最小人工可算 fixture：步骤顺序、缺页数/命中率、分配结果、磁头总移动距离、CPU 完成时间和等待时间分别断言。
3. 保留现有 `tests/simulation-algorithms.js`，把“18 项非空”升级为“18 项可运行 + 关键指标真值 + 输入契约 + 重置后状态归零”。
4. 不因为动画测试通过就推断算法正确；算法真值和视觉动画分别验收。

### 验收

- 18 项算法均可从独立目录进入，至少包含配置、运行、暂停、单步、重置和速度控制。
- 页面置换、CPU、分配、磁盘四类动画展示与真实步骤一致，不再只是整块表格逐格出现。
- 当前步骤右侧说明与伪代码行同步；无映射时诚实显示无映射。
- 从课程单元进入后返回路径、节点上下文和活动完成状态正确。
- 旧入口 `/learning/simulation`、顶栏入口和节点工作台入口都落到同一新页面。

## 9. 阶段 5：联调、测试与人工验收

### 9.1 源码与接口检查

执行顺序：

1. `rg` 检查所有 `enterNodeWorkspace` 模板调用，确认没有隐式 MouseEvent 传参。
2. `rg` 检查 `node.highlight`、`selected`、`selectedId` 的使用，确认路径提示没有再次并入单点选中态。
3. 检查图谱点击 payload 是否保留 `nodeCode`，以及路由 query 是否与当前节点一致。
4. 检查算法目录 18 项与授权资源映射表一一对应，没有把旧页面作为隐藏 iframe 或第二套入口。
5. 检查模拟事件没有在没有 `eventId` 时写学习证据。

### 9.2 自动验证

在两个子项目已有脚本基础上执行：

```text
前端 lint
前端 build
node 前端代码/osLab-frontend/tests/knowledge-graph-render.js
node 前端代码/osLab-frontend/tests/simulation-algorithms.js
后端 Maven test/package（若只改前端，至少记录后端未改动）
git diff --check
```

新增测试至少覆盖：

- 工作台按钮使用当前节点成功生成 concept/node 路由。
- 节点 A→B 切换、hover→click→mouseout、路径提示与选中态分离。
- 重复名称但不同 `nodeCode` 的节点点击不串数据。
- 节点掌握度文字色在选中和缩放后仍正确。
- 18 项算法运行、暂停、单步、重置、四类输入解析和真值 fixture。

### 9.3 人工截图检查

不使用浏览器自动化；由 L 手动检查并记录截图：

1. 图谱全图：节点文字清晰、颜色代表掌握度、关系线可读。
2. 图谱选中：单击 A、单击 B、移出 hover、切换标签和筛选后的状态。
3. 节点详情：点击“进入学习”后工作台打开，返回图谱不丢上下文。
4. 算法模拟：页面置换和 CPU 各完成一次运行、暂停、单步、重置；至少检查一项分配和磁盘算法。
5. 390px 窄窗口：目录、动画、代码面板不互相遮挡；横向内容可以滚动。

## 10. Agent 执行分工与交付格式

### Agent 1：入口与图谱交互修复

修改：`KnowledgeGraphView.vue`、`KnowledgeGraph.vue`、必要的测试。  
先完成阶段 0/1/2，再交付：根因、修改点、图谱测试输出、工作台路由截图建议。  
不要重做图谱配色，不要修改数据库。

### Agent 2：图谱视觉实现

在 Agent 1 合并后修改：`KnowledgeGraph.vue`、`KnowledgeGraphView.vue`、`learning-theme.css`。  
完成阶段 3，交付 token 变化、状态色映射、桌面/窄屏人工检查清单。  
不要再引入 `highlight` 与 `selected` 共用蓝框的实现。

### Agent 3：算法模拟迁移

可与 Agent 1 并行分析资源，但写入当前播放器前先读取 Agent 1 的身份和路由改动。  
完成阶段 4，交付资源映射表、事件/代码行数据结构、四类输入契约、算法真值测试结果。  
不要复制 Vue 2 应用外壳，不要另建第二个算法模拟入口。

### L：整合与最终验收

1. 合并三个 agent 的修改，解决 `KnowledgeGraph.vue` 和 `KnowledgeGraphView.vue` 的冲突。
2. 按阶段 5 执行源码检查、lint、build、Node 测试和必要的后端检查。
3. 手动检查截图，确认管理员维护区、学生图谱、数字孪生和三个独立入口均未退化。
4. 记录已完成、未完成和环境限制；不得用静态示例数据替代失败功能。

每个 agent 必须提交：

1. 修改文件清单。
2. API、路由、数据字段变化。
3. 通过的命令及关键输出。
4. 人工检查步骤和截图路径建议。
5. 未完成项及后续建议。

## 11. 最终验收矩阵

| 验收项 | 通过标准 | 证据 |
| --- | --- | --- |
| 工作台入口 | 右侧按钮一次打开当前节点工作台，返回后上下文保留 | 路由记录/人工截图 |
| 图谱身份 | 重名节点不串，点击 payload 有 nodeCode | Node/ECharts 测试 |
| 单点选中 | A→B 切换后只有 B 选中，旧状态清除 | 渲染测试 + 截图 |
| 掌握度表达 | RED/GREEN/YELLOW/GRAY 直接体现在节点文字色 | 截图 + 状态 fixture |
| 路径提示 | 单元映射/补救路径不伪装成选中节点 | 渲染测试 |
| 图谱缩放 | 0.2/1/2.5/4 倍文字和关系线仍可读 | 渲染测试 + 截图 |
| 模拟独立入口 | 顶栏、首页、单元、旧兼容路径均进入同一页面 | 路由检查 |
| 动画 | 四类算法至少一项具备真实事件过渡，控制按钮有效 | 手动检查 |
| 代码联动 | 有映射步骤高亮正确行，无映射步骤不伪造高亮 | fixture + 截图 |
| 算法真值 | CPU、页面、分配、磁盘关键指标与 fixture 一致 | Node 测试 |
| 学习证据边界 | 无 `eventId` 不更新掌握度，活动算法错配不计完成 | 接口/日志检查 |
| 回归 | lint/build/已有图谱测试/模拟测试通过 | 命令输出 |

## 12. 不在本轮处理

- 不重做教材文本切割、J v3 节点正文、A 题目包或第一章导入。
- 不修改知识图谱数据库关系，不为解决显示问题强行补齐孤立节点关系。
- 不删除数字孪生、历史快照、掌握证据、教师看板或三个独立资源入口。
- 不把授权旧项目整站嵌入 OSLab，不引入第二套路由、登录或学习事件系统。
- 不用静态图片、固定数字或演示用假结果替代动画、算法指标和代码联动。

## 13. 执行记录（2026-10-20/21）

- F1/F4：`KnowledgeGraphView.vue` / `KnowledgeGraph.vue` 完成入口、单点选中、身份链、路径提示分离、返回上下文和刷新清理；图谱测试 26/26。
- F3：节点视觉重做为锐利矩形、类别低饱和底色、掌握度文字色+状态行、2px 选中边框、1px 路径细边、四组图例；主题 token 与组件调色板有镜像测试。
- F2：`SimulationView.vue` / `AlgorithmDemoPlayer.vue` / `simulation/*` 完成独立三栏工作区、播放状态机、四类输入解析、18 项伪代码映射、四类动画和精确真值 fixture；播放器 19/19、算法真值 18/18。
- 验证：前端 lint 0 errors/54 warnings；生产构建 `75710cc0a0958966`；后端本轮未改动。
- L 已人工查看（2026-10-21），未使用浏览器自动化；未提交、推送、部署。
- 交付摘要与回执：`artifacts/feedback-fix-20261020/delivery.md`、`reproduction.md`、`resource-map.json`、`visual-check.md`。

