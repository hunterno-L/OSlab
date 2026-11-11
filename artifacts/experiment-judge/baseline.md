# 编程实验判题与映射 —— 执行基线（实测）

采集时间：2026-11-11
采集方式：直接查询本机 MySQL `oslab_db` + 本机工具链探测 + `git status`
计划：`docs/superpowers/plans/2026-11-11-编程实验北航内容自建判题与映射.md`

本文件记录**执行开始时的真实状态**，不是计划里的预估数字。原始输出见
`baseline-raw.txt`（聚合查询）与 `baseline-experiments.txt`（逐实验清单）。

## 1. 题库与课程聚合

| 项 | 实测值 |
| --- | --- |
| `question` 总行数 / 存活 | 64 / 62 |
| `learning_unit` 总数 / PUBLISHED | 105 / 105 |
| `learning_unit_activity` 分布 | READING 209、WORKED_EXAMPLE 32、PRACTICE 194、ASSESSMENT 26、SUMMARY 25（合计 486） |
| **EXPERIMENT 活动数** | **0** —— 编程实验完全未接入任何学习单元 |
| **`experiment_node` 行数** | **0** —— 编程实验完全无图谱节点映射 |
| `knowledge_node_registry` | 199 |
| `course_resource` | DEMO/DRAFT 18、EXPERIMENT/DRAFT 11（无 PUBLISHED） |

## 2. 实验清单核算

标题 `实验%` 共 **48** 行：

- 已软删除 2 行：`1918945654303125506`（4.10，content 18 B）、`1920028942673477633`（4.11，content 9 B）
- 存活垃圾重复行 5 行（均有完好孪生行）：

| 垃圾行 id | 标题 | content 字节 | 完好孪生行 id | 孪生 content 字节 |
| --- | --- | --- | --- | --- |
| `1918931948987031554` | 实验4.3 `sys_mem_alloc` | 9 | `1930156605618573314` | 1019 |
| `1918932928252157953` | 实验4.5 `sys_mem_unmap` | 9 | `1930156844614209538` | 1046 |
| `1918933052214812674` | 实验4.6 `sys_yield` | 5 | `1930156972624367618` | 993 |
| `1918934540198682625` | 实验4.7 IPC | 6 | `1930157095085461505` | 1224 |
| `1918938841746878466` | 实验4.8 `sys_env_alloc` | 14 | `1930157212584693762` | 1177 |

→ **真实北航实验 41 个**（48 − 2 − 5）。分章：第一章 5、第二章 8、第三章 13（无 3.11）、第四章 15。

## 3. 判题数据现状

- `judgeCase`：除 2.1 为 `[{"input":"123","output":"123"}]` 外，**其余 40 个全部是空占位** `[{"input":"","output":""}]`。
  → 当前 41 个实验**全部不可判题**。
- `originalCode` 非空仅 4 行：2.1（8292 B，真实 `include/queue.h`）、3.1（21130 B，真实 `mm/pmap.c` 含官方自检）、1.1（43 B 垃圾）、2.2（18 B 垃圾）。
- `answer` 有效仅 2 行：2.1（8493 B）、3.1（1432 B `mips_vm_init` 片段）；其余为 0–6 B 占位。
- `content` 长度区间：871–1926 B（存活真实实验）。

### 3.1 第一章内容来源缺口（重要）

工作区**没有** `include.mk`、顶层 Makefile、`tools/scse0_3.lds` 的 Listing 原文：

- `1917131319528452098`（1.1）：`originalCode` 43 B、`answer` 48 B，均为垃圾占位。
- `1930154079598989313`（1.3）：`originalCode` **0 B**、`answer` **0 B**。
- `artifacts/team-handoff/2026-10-17/A-开工包/reference/experiments/1930154079598989313/{原始代码.txt,参考解.txt}` 实测均 **0 字节**。

→ 第一章 harness 与说明扩充只能基于「已有北航文字 + make/ld/ELF 公开语义」，且**必须显式标注为等价示例**，不得冒充北航原文转录。

## 4. 工具链与验证边界

| 项 | 实测 |
| --- | --- |
| 本地 MySQL | 8.4.8，`oslab_db` 存在，root 免密可连 |
| 本地 gcc | `gcc.exe (x86_64-posix-seh-rev0, Built by MinGW-W64 project) 8.1.0`，路径 `/d/mingw64/bin/gcc` |
| **Docker** | **`docker: command not found`** |

→ **沙箱端到端判题无法在本机验证**。harness 只能用本地 gcc 验证；沙箱内真实判题列为未验证项，不得声称通过。

### 4.1 判题负载实测（回应「2G 服务器性能」关切）

本机 gcc 8.1，`gcc -std=c99 -O2 -pipe`，warm 缓存：

| 程序 | 源码字节 | 编译耗时 | 可执行 BSS | 20 次运行总耗时 |
| --- | --- | --- | --- | --- |
| 分支风格极简拓扑排序 | 630 | 325 ms | 67 KB | 1137 ms |
| Tier A harness（2.7 规模，含 `pgtable_pool[64][1024]`） | 5069 | 345 ms | 268 KB | 1167 ms |
| Tier A harness（3.1 真实 pmap.c 规模） | 26121 | 360 ms | — | — |
| Tier A harness（逼近 64 KB 源码上限） | 56081 | 360 ms | — | — |

结论：源码放大 89 倍，编译耗时只 +11%；运行耗时差异 2.6%（本机由 Windows 进程创建开销主导）。
成本几乎全是 gcc 启动 + libc 头解析，与学生代码量无关。2 GB 服务器的真实闸门是
`SANDBOX_MAX_CONCURRENT=2` 与 Docker 容器启动开销，对极简题和 harness 完全一样。

**→ 性能不构成删减北航说明的理由。** Tier C（1.1、1.3 轻量概念判题）的依据是**可判题性与概念同构**，不是性能。

测量局限：MinGW/Windows 环境，非沙箱 Alpine/musl；Docker 不可用故容器内耗时未测。

### 4.2 导入 API 可用性（Task 1 Step 12 实测，2026-11-11）

| 探测 | 结果 |
| --- | --- |
| `curl http://localhost:8121/api/health` | `000`（连接失败） |
| `netstat` 中 `:8121` LISTENING 条数 | **0** |
| 后端配置 `application.yml:53-57` | `server.port=${SERVER_PORT:8121}`、`context-path=/api` → 端口无误，是**服务未启动** |
| `:8080` LISTENING（PID 12164） | 返回 `Access Error: 404 -- Cannot locate document`，非本项目后端 API |
| `:8081` LISTENING（PID 6992） | 返回前端 HTML（`lang="zh-CN"`、`theme-color=#e6e9d1`），是前端静态服务，`/api/health` 返回 500 |
| 沙箱服务端口 | `代码沙箱/server.py:16` 默认 `SANDBOX_PORT=8090`，**未监听**；且 Docker 不可用 |

**结论：导入 API 不可用 → 记为阻塞项。**
按计划 Task 1 Step 12 的分支规则，先完成 Task 2–7 的内容与验证（不依赖后端），Task 8 前再解决启动与教师账号问题。
**不得**改为手写 SQL 绕过 `publishedJson` 不变量。

未验证项（不得写成通过）：后端本地启动、教师账号登录、`/teaching/experiments/import/preview|commit|{id}/review` 全链路。

### 4.3 内容包 schema 实测（Task 8 预备，2026-11-11，主 agent）

参照既有包 `artifacts/content-import/20261019-final-preview/experiments-original/experiment-maintenance.json`
（124762 B，48 个 experiments）实测顶层结构：

```
schemaVersion = "oslab-experiment-maintenance/1.0"
batchCode     = "batch.a.experiments.001"   // 本轮按章新建批次码
contributor   = "A"
note          = <字符串>
experiments[] = { questionId, resourceCode, title, sectionCode,
                  contentChanges[], nodeMappings[], unitMappings[],
                  verification{fileReview,runtime,cases[],openItems[]},
                  nodeProposals[], unitProposals[] }
```

- `resourceCode` 既有形态为 `exp.legacy.<19位questionId>`；`ExperimentMaintenanceService.mappings()`
  在 `course_resource` 无行时正是回退到这个值（:83）。
- `nodeMappings[]` 实测字段：`{nodeCode, role, weight, reason}`（该旧包**未带 `nodeName`**；
  计划 Task 7 要求 `nodeName` == registry.name，落库前须以 `validate()` 的实际字段要求为准）。
- 该旧包 48 条的 `contentChanges` 与 `unitMappings` **全为空数组**，无法从中取样其字段形状；
  须读 `ExperimentMaintenanceService.java`（15368 B，86 行高密度代码）的 `validate()`/`stage()` 确认。
- 既有 `sectionCode` 取值集合（9 个）：`sec.memory.basic`、`sec.memory.paging`、
  `sec.memory.address-translation`、`sec.overview.boot`、`sec.process.process-thread`、
  `sec.files.basics`、`sec.overview.runtime`、`sec.process.cpu-scheduling`、`sec.memory.demand-paging`。
- 入口方法：`preview(JsonNode)`（:30）、`commit(JsonNode,long)`（:35，内部先 preview，`valid!=true` 直接抛错）、
  `review(String,JsonNode,long)`（:55）、`mappings(String)`（:83）、`saveMappings(String,JsonNode,long)`（:85）。

**结论：Task 8 的包结构已有可照抄的真实样例，但 `contentChanges`/`unitMappings` 字段形状仍待读源码确认；
且 API 本身仍不可用（§4.2），Task 8 保持阻塞。**

## 5. 工作区状态（不得覆盖清单）

`git status --porcelain=v1` 实测：

- **已修改未提交**：前端 24 个文件、后端 18 个文件（A/B 两路交付成果）。
- **工作区已删除（未 staged）9 个 docs 文件**：
  `docs/ZhiCore整体AI升级-交付计划.md`、`docs/ZhiCore整体AI升级-开发约束与子代理任务书.md`、
  `docs/demand3与学生学习记录整合修正计划.md`、`docs/demand3整改-三项问题修正计划.md`、
  `docs/demand3整改-交付记录.md`、`docs/demand3整改-阶段一基线对照.md`、
  `docs/教师任务接口与数据契约.md`、`docs/教师端内容维护与任务使用指南.md`、
  `docs/教师端内容管理与学习执行计划.md`
  → 这是执行开始前既存的工作区状态，**本轮不恢复、不提交、不删除**。
- `git diff --stat`：**51 files changed, 4240 insertions(+), 3930 deletions(-)**。
- 未跟踪目录含 `artifacts/experiment-judge/`（本轮新建）等。

### 5.1 必须保留的用户改动

- `前端代码/osLab-frontend/src/components/assistant/FloatingAiAssistant.vue`：用户未提交的头像与拖动改动
  （`<picture class="ai-fab-avatar">`、`prefers-reduced-motion` 静态源、webp + img 回退、
  title「AI 助手（点击开关，拖动可上下放置）」）。**不得恢复到 Git 版本。**
- `public/ai-assistant*` 资源必须保留。
- `前端代码/osLab-frontend/src/views/question/ViewQuestionView.vue`：A/B 已重写（1238 行），
  本轮只做布局参考，禁止整文件覆盖。

## 6. 本轮写入范围

只新增：`artifacts/experiment-judge/**`、`scripts/verify-experiment-harness.mjs` 等本轮脚本、
`content/operating-systems/wangdao-2025/experiments/**`、`数据库文件/oslab_upgrade_v14_*.sql`（Task 10）。
数据库写操作只经既有导入 API，且只针对 41 个真实实验行；Task 9 的 5 行软删除需用户单独确认。