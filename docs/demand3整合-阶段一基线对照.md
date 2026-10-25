# demand3 与学生学习记录整合——阶段一基线对照

日期：2026-10-22
状态：阶段一产物；后续实现以本文件为对照基线。

> 2026-10-24 后续修正：用户要求代码联动而非伪代码联动；代码面板已改为 `src/simulation/*.ts` 的真实 TypeScript 实现摘录，详见 `docs/demand3整合-交付记录.md` 第 4 节。

## 1. 图谱复现问题清单

复现对象：`前端代码/osLab-frontend/src/views/knowledgeGraph/KnowledgeGraphView.vue`、`src/components/KnowledgeGraph.vue`；数据来自课程全图 / 我的掌握快照，含筛选、关系标签、只看学习路径、历史回放四类状态。

| # | 复现步骤 | 当前表现 | 根因分类 | 修正后的期望行为 |
|---|---|---|---|---|
| G1 | 打开课程全图（150+ 节点） | 节点卡片固定 184×70/50px，名称+类型+状态三行堆叠，画布被节点占满 | 尺寸 | 常规节点 104–152×32–42px；节点只保留名称与状态圆点/短条；类型说明交给图例与详情栏 |
| G2 | 缩小到全图或反复滚轮缩放 | 卡片屏幕尺寸固定，缩放越小节点相对画布越大，标签互相覆盖、边缘节点被裁 | 缩放/尺寸 | 按缩放层级隐藏附属信息；节点 hit-area 保持可点击；重置视野完整包住边缘节点 |
| G3 | 查看默认全图 | 依赖边 1.8px、包含 1.4px、相关 1.5px，非邻接透明度压到 0.62，连线弱于节点色块 | 绘制 | 基础关系线 1.5–2px，选中邻接 2–2.5px；弱化非关联边但保留可见下限；箭头尺寸随节点档位 |
| G4 | 打开“关系标签” | 所有边同时显示文字标签，无避让；密集区标签压住节点与其它标签 | 标签/绘制顺序 | 标签水平短文本；优先选中/悬停邻边；仅对屏幕空间足够的边显示；重叠时按占用网格避让/省略 |
| G5 | 观察默认课程图布局 | 章节锚点 + 螺旋播种后再跑 ECharts force，节点呈放射散点，章节边界和先修层级不可辨 | 布局 | 确定性分组/分层布局：章节形成稳定区域，区内按前置层级/输入顺序排列；不使用随机力导向 |
| G6 | 切换筛选、只看学习路径、回放步进 | 拓扑变化时重新播种并异步收敛，同一批节点坐标跳动；筛选后视野可能裁边 | 布局/视野 | 同一拓扑坐标稳定；只有拓扑签名变化时重建；新结果重新适配且不裁边 |
| G7 | 连续切换选中节点、打开/关闭关系标签 | 选中只刷新视觉（正确），但自定义 fit action 依赖 `chartView._controllerHost`，升级 ECharts 有断裂风险 | 缩放 API | 使用公开 `graphRoam`（zoom + dx/dy）完成缩放/居中；不再直接访问 ECharts 私有字段 |
| G8 | 筛选掉当前选中节点 | 已有守卫会清空选中（`watch(filteredNodes)`），行为正确 | 状态 | 保持：筛选后不残留高亮；换选立即清除旧高亮 |
| G9 | 窄屏打开图谱 | 画布高度 clamp 尚可用，但详情栏转入下方后画布可视宽度变小，184px 节点更易拥挤 | 响应式/尺寸 | 紧凑节点与缩放层级共同保证窄屏仍可点击、可缩放；详情栏沿用既有响应式规则 |

问题归类汇总：尺寸 3 项（G1/G2/G9）、布局 2 项（G5/G6）、绘制 2 项（G3/G4）、缩放 API 1 项（G7）；G8 为已验证正确、需保持的行为。

## 2. 18 项算法功能矩阵

说明：
- 旧资源根目录：`E:\web\web\demo_operatingsystem 2025.5.26\demo_operatingsystem\src\views`。
- 旧资源 18 项均具备：流程提交/重置、增加删除输入行、解说提示、解析抽屉、暂停/继续、0.1 步长减速/加速、0.5/1/2 倍速预设；下表不再逐项重复，只记录差异。
- 当前平台 `src/simulation/*` 是唯一算法结果来源；代码面板为伪代码（`src/simulation/codeMaps.ts`），不是真实源码。

| 旧资源页面/行为 | 当前 algorithm key | 参数/输入 | 过程动画/提示 | 解析 | 播放控制 | 当前代码映射 | 补齐文件/测试 |
|---|---|---|---|---|---|---|---|
| unit3/FCFS.vue：进程行增删，到达/运行时间 | cpu.fcfs | 进程名、到达、运行；默认 3 进程 | 时间轴甘特、CPU 运行/空闲、就绪队列 | 算法思想/特点抽屉 | 平台已有完整控制 | codeMaps `cpu.fcfs` | 无需补算法；补连续速度与解析面板 |
| unit3/SJF.vue | cpu.sjf | 同上 | 同上 + burst 最短选择 | 同上 | 同上 | `cpu.sjf` | 同上 |
| unit3/HighResponse.vue | cpu.hrrn | 同上 | 同上 + 响应比选择 | 同上 | 同上 | `cpu.hrrn` | 同上 |
| unit3/HighPrio.vue：优先数 1 起 | cpu.priority_nonpreemptive | 同上 + 优先级（数字小优先） | 同上 + 优先级选择 | 同上 | 同上 | `cpu.priority_nonpreemptive` | 同上 |
| unit3/HighPrio2.vue：优先数 1 起 | cpu.priority_preemptive | 同上 + 优先级 | 同上 + 抢占标记 | 同上 | 同上 | `cpu.priority_preemptive` | 同上 |
| unit3/RR.vue：时间片 1–10，默认 2 | cpu.rr | 同上 + 时间片 | 同上 + 轮转/时间片用尽 | 同上 | 同上 | `cpu.rr` | 同上 |
| unit3/FeedbackQueue.vue：队列数 2–6，每级时间片 1–10 独立配置 | cpu.mlfq | 进程名/到达/运行；队列数；每级时间片 | 多级就绪队列、进入/降级/抢占/完成 | 多级反馈队列思想/特点 | 平台已有完整控制 | `cpu.mlfq` | 改 `types.ts`（队列时间片数组）、`cpu.ts`、`catalog.ts`、播放器输入与 `simulation-algorithms.js` |
| unit4/PartitionAllocation_FF.vue：多块初始空闲分区 + 多进程需求 | allocation.first_fit | 初始分区大小列表、进程名/需求、可追加 ALLOC/FREE 操作 | 分区条、候选区、分配/剩余/回收合并 | 首次适应思想/特点 | 平台已有完整控制 | `allocation.first_fit` | 改 `types.ts`（initialPartitions）、`allocation.ts`、`catalog.ts`、播放器输入与测试 |
| unit4/PartitionAllocation_BF.vue | allocation.best_fit | 同上 | 同上 + 最小满足区选择 | 同上 | 同上 | `allocation.best_fit` | 同上 |
| unit4/PartitionAllocation_WF.vue | allocation.worst_fit | 同上 | 同上 + 最大区选择 | 同上 | 同上 | `allocation.worst_fit` | 同上 |
| unit4/PartitionAllocation_NF.vue | allocation.next_fit | 同上 | 同上 + 上次结束位置指针 | 同上 | 同上 | `allocation.next_fit` | 同上 |
| unit4/Replacement_FF.vue：物理块 1–60，访问序列可增删（页号 0–100） | page.fifo | 物理块 1–60、访问序列 | 帧位、命中/缺页/淘汰、命中率 | FIFO 思想/特点（Belady 异常） | 平台已有完整控制 | `page.fifo` | 改 `page.ts` 上限与输入提示、播放器序列横向滚动、测试 |
| unit4/Replacement_LRU.vue | page.lru | 同上 | 同上 + 命中重排 | LRU 思想/特点 | 同上 | `page.lru` | 同上 |
| unit4/Replacement_OPT.vue | page.opt | 同上 | 同上 + 未来最久淘汰 | OPT 思想/特点（不可实现性） | 同上 | `page.opt` | 同上 |
| unit5/FCFS.vue：初始磁道 1–200，后续磁道 1–200 | disk.fcfs | 初始磁头、请求序列 | 磁道轨、移动距离、服务顺序 | 磁盘 FCFS 思想/特点 | 平台已有完整控制 | `disk.fcfs` | 改 `disk.ts` 范围为 1–200、端点常量、播放器刻度、测试 |
| unit5/SSTF.vue | disk.sstf | 同上 | 同上 + 最近优先 | SSTF 思想/特点（可能饥饿） | 同上 | `disk.sstf` | 同上 |
| unit5/SCAN.vue：按方向扫到端点再反向 | disk.scan | 同上 + 方向 | 端点移动、反向、累计寻道 | SCAN 思想/特点 | 同上 | `disk.scan` | 同上，补端点 1/200 与回扫计费测试 |
| unit5/CSCAN.vue：到端点回跳到另一端 | disk.cscan | 同上 + 方向 | 回跳（不计寻道）、同向继续 | C-SCAN 思想/特点 | 同上 | `disk.cscan` | 同上，补回跳计费测试 |

### 当前平台相对旧资源的确定差异

| 能力 | 当前平台 | 本计划处理 |
|---|---|---|
| MLFQ 队列数与每级时间片 | 固定 3 层，q/2q/4q | 支持 2–6 层、每层独立时间片，默认仍为 3 层 q/2q/4q |
| 分区分配初始状态 | 单块 `memorySize` + ALLOC/FREE | 增加多块初始空闲分区输入，保留 ALLOC/FREE 扩展操作 |
| 播放速度 | 仅 0.5/1/2 预设 | 保留预设，另加 0.1 步长减速/加速，范围 0.1–4.0 |
| 页面置换上限 | 物理块 1–10，序列最多 50 | 物理块 1–60；序列放宽并支持横向滚动/长序列布局 |
| 磁盘范围 | 0–199 | 1–200，SCAN/C-SCAN 端点与回扫计费按算法定义 |
| 运行提示/解析 | 步骤说明 + 简短 rules | 保留步骤事件提示，增加可展开的完整算法思想/特点解析 |
| 代码面板 | 已有伪代码 + 事件行映射 | 改为右侧常驻独立面板，明确“算法伪代码”，与步骤联动 |

### 预计变更文件

- 图谱：`src/components/KnowledgeGraph.vue`、`src/views/knowledgeGraph/KnowledgeGraphView.vue`、`tests/knowledge-graph-render.js`。
- 算法：`src/simulation/types.ts`、`cpu.ts`、`allocation.ts`、`page.ts`、`disk.ts`、`playback.ts`、`catalog.ts`、`codeMaps.ts`、`src/components/learning/AlgorithmDemoPlayer.vue`、`src/views/learning/SimulationView.vue`、`tests/simulation-algorithms.js`、`tests/simulation-player.js`。
- 学习记录：`src/views/learning/LearningRecordsView.vue`、`src/api/learning.ts`、`src/api/tasks.ts`、后端活动查询控制器/服务/VO/Mapper、`learning_task` 相关服务与测试、数据库增量脚本。
