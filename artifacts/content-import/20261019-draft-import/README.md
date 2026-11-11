# 2026-10-19 本地资源导入试验

实际执行：L 的本地离线导入操作。MySQL：`127.0.0.1:3306/oslab_db`。通过现有 Spring 事务服务执行，不提供登录后门；账本操作者使用库中已有管理员 `teacher_demo` / `2104470171261014017`，不登录、不修改账号。原贡献人仍为 J/A。

| 内容 | 实际结果 |
| --- | --- |
| J 整章知识包 | 35 节点、57 教学块、27 关系、7 图片已导入；教学块 DRAFT |
| A 整章题目包 | 103 题新增，103 修订均 DRAFT |
| A 实验维护包 | 完整 48 项预览：11 项有效，37 项阻塞；仅将原样的 11 项有效子集保存为 DRAFT 修订 |
| 重导预览 | 知识 119 项、题目 103 项、有效实验 11 项均 UNCHANGED，零 CREATE/UPDATE |
| Neo4j | 同步 SYNCED、pendingCount=0，已建立 nodeCode 唯一约束；节点 150→185，关系 171→198 |

未提交审核、未批准、未发布、未执行实验 APPLY。10 个单元仍未发布；原编程实验和正式 experiment_node 映射保持原样。11 项实验修订只保存 A 的映射、盘点和验证说明，其 `contentChanges` / `unitMappings` 全空，不代表运行通过。

37 项阻塞中，22 项没有 PRIMARY 映射（15 项仅有候选建议，7 项题面无有效任务），15 项引用尚未登记的其他章节节点：os.cpu.rr、os.cpu.scheduling、os.memory.address_translation、os.memory.page_fault、os.memory.page_table、os.memory.tlb。完整逐项路径和原因见 experiments-preview.json。没有放宽协议、补造知识节点或丢弃候选说明。有效子集保持每项全部原字段，见 experiments-valid-subset-request.json。

原件完整复制在 knowledge-original / questions-original / experiments-original，每个目录都有 SHA-256 清单 archive-manifest.json。未修改微信来源目录。首次纯预览（知识未入库，故题目节点未登记）保存在相邻的 `../20261019-preview/`。

database-before.json / database-after.json 保存数量、状态和保护表全行 SHA-256。64 个原实验、426 个学习事件、246 个作答、13 个掌握状态、45 个代码提交，以及 experiment_node 均按完整行哈希确认未变；131 个原题按 existing-question-hashes.json 逐行核对未变。数据库图片 BLOB 的 SHA-256 与存储 contentHash 全部一致。

操作证据包括各包 preview / commit / repreview JSON、有效子集三阶段 JSON、graph-before / graph-after / graph-sync JSON、operation.json。知识和题目的 importRunCode 分别为 import.2112165568641155091、import.assessment.2112165590967435269。实验原服务不写 course_import_run，其实验修订有 batchCode、贡献人和操作者，离线操作另由本目录保存。

手工工具位于后端 src/test/java/com/yupi/springbootinit/course/LocalDraftImport.java。它是显式 main 入口，不是自动测试或应用启动器。需指定 --preview-local 或 --commit-local-drafts，已有管理员 ID、新建输出目录及三个原件根目录；数据库用户名/密码必须通过专用环境变量明确配置，空密码需 OSLAB_IMPORT_DB_PASSWORD_EMPTY=true。工具仅连接固定本地库，调用现有服务，遇无效实验包只保存完整预览中已经验证的原样子集。

验证：JDK 21 Maven test-compile 通过；真实本地 Spring 事务服务预览、草稿提交和提交后无变化预览通过。新增手工工具配置不参与正常应用的组件扫描，已用独立验证库运行 CourseContractsTest、TeachingResourceIntegrationTest 共 18 项回归，全部通过；日志为后端 target/delivery-import-regression.log。未进行浏览器操作、提交、推送、部署、沙箱判题或全套构建。

## 接收审查与发布前待确认

导入通过表示系统可以保存资源，不等于教材与判分口径已通过审核。本次实际采用用户指定 J 包中的 27 条关系版本；原件及自审记录均完整留档。

J 的交接说明声称完成教材与图表核对，但自审记录明确写明未取得教材 PDF、未完成本轮教材逐页／关系语义／图表视觉核验，结论“尚未通过”，并提及 43／27 条关系版本差异。应由 J 统一交付版本与复核说明，再进入内容批准。

以下题目仍需教材依据或明确改编后再发布，本次没有擅自改题、改答案或自动发布：

| 题目代码 | 待确认原因 |
| --- | --- |
| q.wd25.s1_3.005 | 解析混同执行特权指令与使用非特权访管指令，和 J 的权限模型讲解冲突 |
| q.wd25.s1_3.016 | 对内核态可执行指令的口径与同包 006、034 题解析不一致 |
| q.ext.s1_2.001 | 选项“提高 CPU 和 I/O 速度”在解析中被改读为利用率／吞吐率，单选唯一性待核 |
| q.wd25.s1_2.009 | 优先级、非抢占与响应及时性的因果说明待核；A 也已登记疑问 |
| q.ext.s1_3.001 | 将原语称为“特殊系统调用命令”，与知识包定义不一致 |
| q.ext.s1_3.003 | “硬件管理系统”选项的广义含义有歧义，不能只依教材未列同名术语判断 |

六道外部题的 sourceCode 仍统一使用教材，实际外部出处仅写在 locator，后续应区分题目来源与教材知识依据。35 节点均被题目关联，但共享、虚拟性、异步性仅有 SECONDARY 映射，不能称为每个节点都有独立主考证据。

本轮额外检查：J 的 35 节点、57 块与关系代码无重复，REQUIRES 无环；7 张 PNG 可解码；A 的 103 道题均为 SINGLE，答案存在于选项，题号无重复，跨包节点与十个单元代码引用一致。这些检查不代替逐页教材复核。
