# demand2 人工反馈修正交付摘要（2026-10-20/21）

执行：L 授权的实施 agent。范围：F1 节点工作台入口、F4 图谱选中/高亮、F3 节点视觉、F2 算法模拟动画与代码联动。遵循 `18-demand2人工反馈修正计划.md`；未提交、未推送、未部署。

## 结果

- **F1 入口**：右侧“进入节点工作台”改为显式无参调用；事件对象不再被当作节点键；双击/相关节点/单元映射入口统一使用稳定 `nodeKey`；返回上下文包含进入节点的 `nodeCode`/`nodeName`，并保留课程/小节/单元。
- **F4 图谱**：统一 `nodeKey = nodeCode || stableId || String(id) || name`；选中、路径提示、脉冲三态分离；掌握度文字色不被选中覆盖；筛选隐藏选中节点时清除详情/邻接；非拓扑刷新先 `downplay`，保留相机/布局/拖拽。
- **F3 视觉**：锐利矩形节点、固定尺寸、两行名称、类别低饱和底色、掌握度文字色+状态行、2px 蓝色选中边框+短外圈、1px 路径细边、主题 token 与四组图例。
- **F2 模拟器**：独立 `/experiments/simulation` 页面采用左目录/中动画/右说明与伪代码布局；C1 状态机支持运行/暂停/单步/重置/调速和输入变更失效；C2 为 CPU（时间轴/就绪队列/运行进程）、页面（帧槽/命中缺页/换入换出）、分配（内存块/搜索/切分/合并）、磁盘（磁轨/磁头/服务顺序）提供真实步骤过渡；18 项均有伪代码映射，无映射步骤明确显示“本步骤无代码行映射”。
- **算法真值**：修正 CPU 等待/响应/周转定义；新增 CPU、页面、分配、磁盘四类精确 fixture；18/18 可运行。
- **课程边界**：从课程活动进入时显示活动上下文；切换算法后显示“当前演示不对应本单元”，不错误完成原活动；无 `eventId` 不写学习证据。

## 2026-10-21 人工反馈收尾

- 图谱保留**力引导布局**（ECharts force，章节锚点+螺旋播种），节点卡片 184×70，类型由 `nodeType` 映射到底色，掌握度仍直接控制文字色；关系线加深加粗，选中/路径/脉冲三态分离。
- 底部图例删除“选中 2px 蓝框”和“单元节点细边”，保留类型、掌握、补救路径和关系语义。
- `/graph`、`/assessments`、`/experiments` 的大标题统一移到最左侧目录上方（全局页面标题行），删除蓝色小字 kicker；题目练习页保留范围/题数与连续测评操作，实验中心保留结果计数。
- L 已人工查看并进入清理；最终 dist 为本次复核构建 `75710cc0a0958966`，构建日志见 `frontend-build-final.log`。本轮未再改动 UI。

## 验证

| 检查 | 结果 |
| --- | --- |
| 图谱 Node/ECharts | `node tests/knowledge-graph-render.js`：26 PASS / 0 FAIL |
| 播放器契约 | `node tests/simulation-player.js`：19 PASS / 0 FAIL |
| 算法真值 | `node tests/simulation-algorithms.js`：18/18 + 四类精确 fixture |
| 前端 lint | 0 errors / 54 warnings |
| 生产构建 | hash `75710cc0a0958966`（复核版） |
| 后端 | 本轮未改动（发布阶段 211/0/0/1） |

## 手工验收

L 已人工查看力引导图谱、三个页面标题层级和图例，并确认进入清理；截图由 L 留存。仍未使用浏览器自动化作为验收手段。

## 已知限制

- ECharts 5.6 的 `rect` 实际渲染为直角（样式模型保留 2px 圆角 token）；符合“锐利矩形或轻微 2px 圆角”。
- 固定 184×70 节点 + `nodeScaleRatio=0` 保证低缩放文字可读；150 节点全图在很低缩放下会重叠，需放大查看。
- 路径提示 1px 较克制；如后续觉得过淡，可提高 `--oslab-graph-path`。
- 暂停时正在进行的 CSS 过渡最多 600ms 内自然结束，不强行冻结中途帧。
- 页面帧槽按驻留顺序展示，不是固定物理帧号。
- 浏览器 hover/emphasis 残留仍属浏览器级风险，已用 `downplay` 缓解。

## 主要文件

`KnowledgeGraph.vue`、`KnowledgeGraphView.vue`、`learning-theme.css`、`AlgorithmDemoPlayer.vue`、`SimulationView.vue`、`simulation/{types,playback,codeMaps,catalog,cpu,allocation,disk,page}.ts`、`tests/{knowledge-graph-render,simulation-player,simulation-algorithms}.js`、`artifacts/feedback-fix-20261020/{reproduction.md,resource-map.json,visual-check.md}`。
