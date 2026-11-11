# 《王道操作系统》内容协作目录

这是 J、A 与L共用的内容工作目录。当前优先交付第一章“计算机系统概述”整章，见 [第一章方案](../../../docs/教材驱动课程重构/06-第一章交付方案与资料清单.md)。已有目录骨架、编号草案、自编协议样例，以及根据六张教材截图整理的页面置换样板。文件尚未导入系统；成员复核、系统接入与课程发布分别推进。

## 使用顺序

1. 所有人阅读 [总体设计](../../../docs/教材驱动课程重构/01-课程与知识图谱总体设计.md) 和 [数据规范](../../../docs/教材驱动课程重构/05-协作数据规范.md)。
2. J、A 已持有教材，直接按第一章方案整理 1.1。A 同时按 [实验指南](../../../docs/教材驱动课程重构/07-成员A编程实验维护与映射指南.md) 盘点已有实验并填写新映射；两人只交资源包，由 L 导入。
3. 第一章复制 [知识样例](samples/knowledge.sample.json) 和 [题目样例](samples/questions.sample.json)，整理实际材料与出处；[页面置换交接](handoffs/memory-page-replacement-001.md) 保留作后续样板。
4. L维护目录／词表和实际单元包，按 [系统计划](../../../docs/教材驱动课程重构/04-系统设计与改造执行计划.md) 实现导入与课程页面。
5. 导入先知识、后题目，最后编排与发布单元。

## 文件用途

| 文件 | 内容 |
| --- | --- |
| [sources.json](catalog/sources.json) | 已确认 2025 年的教材来源、授权演示、自编材料及协议样例来源 |
| [sections.json](catalog/sections.json) | 五章与主要节目录，优先细化演示所用小节；不是完整逐标题转录 |
| [nodes.json](catalog/nodes.json) | 35 个协作节点代码，含首批知识与 18 种演示算法；不是旧图谱完整导出 |
| [demos.json](catalog/demos.json) | 已核查的 18 个旧项目源码路径、教材归属及待接入状态 |
| [knowledge.sample.json](samples/knowledge.sample.json) | 7 个页面置换相关节点、4 个自编教学块与候选关系 |
| [questions.sample.json](samples/questions.sample.json) | 四类知识测评各一题，新协议样例 |
| [questions.legacy.sample.json](samples/questions.legacy.sample.json) | 当前旧导入框可识别的字段形状；试导前确认节点名称存在 |
| [units.sample.json](samples/units.sample.json) | 旧五单元协议样例；页面置换实际材料使用下面的七单元包 |
| [handoff.template.md](samples/handoff.template.md) | 每批的短交接说明 |
| [首批清单](batches/memory-page-replacement-001.json) | 本批文件、数量、导入顺序与待补项；排除 samples |
| [首批知识包](knowledge/j-memory-page-replacement-001.json) | 15 个节点、15 个教学块、19 条候选关系 |
| [首批题目包](questions/a-memory-page-replacement-001.json) | 8 道教材题改编和 14 道自编巩固题 |
| [首批单元包](units/owner-memory-page-replacement-001.json) | 7 个学习单元、36 项学习活动 |
| [素材清单](assets/wd25-memory-batch-001/manifest.json) | 六张原始截图、CLOCK 指针裁剪图与页码状态 |
| [核验案例](verification/page-replacement.cases.json) | 11 个算法案例与第 50 题的输入、预期结果 |

正式教材材料放 knowledge/、questions/、units/；图片放 assets/；交接记录放 handoffs/；批次入口放 batches/。本批已按此结构整理。

A 的实验维护材料放 experiments/，协议与实际导入状态见实验指南。成员开工模板在 starter-templates/，预分配第一章编号在 catalog/overview-authoring.json；离线成员包可用 scripts/check-member-package.mjs 检查。第一章实际包由 J、A 按节提交，L 校验导入，不能用页面置换包的完成数量代表第一章已完成。

## 编号和正文

代码由L统筹，J 可以提新增／拆合建议。名称调整不换代码，题目重复使用不换题号。目录标题和教材展示节号分开保存。

正文页 220 页眉已确认教材为 2025 年版，具体印次待核对。219、220 页码可见，217、218 由连续正文推定；题目和解析页码不可见，保留原图与题号定位。sample.oslab 下的内容只作协议示例，实际首批分别记录教材改写或自编来源。

演示旧项目：`E:/web/web/demo_operatingsystem 2025.5.26/demo_operatingsystem/`。代码文件已发现，runtimeValidated=false 表示本轮没有完成运行验收。资源在系统注册并验证后才能用于已发布单元。

## 文件检查

从仓库根目录运行：

```powershell
node scripts/validate-course-content.mjs
node scripts/validate-course-content.mjs --batch batches/memory-page-replacement-001.json
node scripts/verify-page-replacement-content.mjs
```

第一条检查全部样例与正式包，第二条只检查首批实际教材包，第三条复算算法案例及第 50 题，均不写数据库。新版知识／题目包需要新导入工具；旧题目界面只接收数组，不能保存新协议的编号、出处与用途。
