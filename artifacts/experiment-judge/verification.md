# 编程实验判题 —— 验证记录

本文件只记录**真实运行过的验证**及其原始输出。未运行的项目一律标注「未验证」，不得写成通过。

环境：Windows 10.0.26200 / MinGW-w64 gcc 8.1.0 / Node.js v24.15.0 / MySQL 8.4.8
**Docker 不可用（`docker: command not found`）→ 沙箱内端到端判题全程未验证。**

---

## Task 1：判题契约 spike（实验 2.1 内存控制块）

日期：2026-11-11
实验 id：`1908449571747745793`
目录：`content/operating-systems/wangdao-2025/experiments/A-第二章-实验判题-001/experiment-files/1908449571747745793`

### 1.1 基线复核

见 `baseline.md`、`baseline-raw.txt`、`baseline-experiments.txt`。关键实测：
`experiment_node` 0 行、EXPERIMENT 活动 0 个、41 个真实实验的 `judgeCase` 全为空占位。

### 1.2 官方自检依据

从 `question.id=1908900051330306049` 的 `originalCode` 导出真实 `mm/pmap.c`
（`reference-pmap.c`，21824 B），定位 `physical_memory_manage_check()`（第 459 行）：

- 第 522 行 `int answer1[]={0,1,2,3,4,5,6,7,8,9};` —— LIST_INSERT_TAIL 顺序
- 第 533 行 `int answer2[]={0,1,2,3,4,20,5,6,7,8,9};` —— LIST_INSERT_AFTER 插入后顺序

这两组官方断言即 2.1 harness 用例 1、2 的判定锚。

### 1.3 验证命令与结果（退出码 0）

```bash
node scripts/derive-harness-variants.mjs <实验目录>
node scripts/verify-experiment-harness.mjs <实验目录>
```

原始输出见 `verify-2_1.log`，摘录：

| # | 用例 | reference | starter | wrong |
| --- | --- | --- | --- | --- |
| 1 | 官方尾插顺序 answer1 | `0 1 2 3 4 5 6 7 8 9` | `` | `9 8 7 6 5 4 3 2 1 0` |
| 2 | 官方后插顺序 answer2 | `0 1 2 3 4 20 5 6 7 8 9` | `` | `9 8 7 6 5 4 20 3 2 1 0` |
| 3 | 空链表尾插 + 头插混合 | `1 3 7 9 5` | `1 3` | `5 1 9 3 7` |
| 4 | 移除后再尾插（校验 le_prev） | `2 3 4 5` | `` | `5 4 3 2` |
| 5 | 单元素反复移除与重插 | `4 9` | `` | `4 9` |

- 三方均以 `-std=c99 -Wall -Wextra -Werror -O2 -pipe` 编译，**零警告**。
- 参考输出 5 条互异且非空。
- starter 在 5/5 条上失败；wrong 在 4/5 条上失败（用例 5 恰好同输出，属预期）。
- **reference 用例 1、2 与官方 `answer1`/`answer2` 逐字一致。**
- `judge-cases.json` 由脚本从参考解真实运行导出，未手写。

### 1.4 过程中发现并修复的两个真实缺陷

**缺陷 A：工作区 `answer` 字段的 `LIST_INSERT_TAIL` 本身有错。**

逐字转录工作区版本作为第四方运行，结果：

| # | 工作区 answer 输出 | 正确期望 | 结论 |
| --- | --- | --- | --- |
| 1 | `0 1 2 3 4 5 6 7 8 9` | 同 | MATCH |
| 2 | `0 1 2 3 4 20 5 6 7 8 9` | 同 | MATCH |
| 3 | `1 3 7 9 5` | 同 | MATCH |
| 4 | **`1 2 3 4 5`** | **`2 3 4 5`** | **DIFFER** |
| 5 | `4 9` | 同 | MATCH |

根因（`reference-answer-2_1.txt` 第 156–171 行）：
1. 第 168 行 `(elm)->field.le_prev = &LIST_NEXT((elm), field);` —— `le_prev` 误指向 **elm 自己的** `le_next`，
   应指向**前驱节点的** `le_next`。后果：`LIST_REMOVE` 无法断开前驱，被移除的页仍留在链上（用例 4 的页 1）。
2. 空链表分支未设置 `LIST_NEXT(elm) = NULL`（该赋值只在 else 分支内）。

修正：`le_prev` 改为 `&LIST_NEXT(LIST_NEXT((elm), field), field)`（此时 walker 仍指向尾节点），
并把 `LIST_NEXT((elm), field) = NULL` 移到 if/else 之外。修正点已写入 `fill-reference.c` 注释。

→ **结论：工作区 `answer` 不能当作判题真值。** 已上升为 `harness-spec.md` 第 9 节规则。

**缺陷 B：初版驱动对错误实现会崩溃。**

初版驱动对未链入元素直接调 `LIST_REMOVE`，而 `LIST_REMOVE` 解引用 `le_prev`；
空实现下 `le_prev` 为 NULL → 访问违例 `0xC0000005`，验证脚本抛异常。
沙箱会把非零退出判为 `Runtime Error`，学生看不到「答案错误」。

修正：驱动增加 `in_list()` 成员判定（带 `MAXP` 步数上限），只对确实已链入的元素做移除/后插；
末尾遍历同样加步数上限防环。修正后 starter 不再崩溃，改为输出空序列 → 正确判为答案错误。

→ 已上升为 `harness-spec.md` 第 6 节规则，适用于后续全部 harness。

### 1.5 未验证项

- **沙箱内端到端判题**：Docker 不可用，未运行。本地 gcc 验证比沙箱更严（多 `-std=c99 -Wall -Wextra -Werror`），
  但不能替代沙箱实测。
- 后端 `JudgeServiceImpl` → 沙箱 → `DefaultJudgeStrategy` 全链路：未运行。
- 前端提交评测 UI：未运行（Task 11 处理）。

---

## Task 2：第二章内存管理 harness（2.2–2.7，6 个）

日期：2026-11-11
目录：`content/operating-systems/wangdao-2025/experiments/A-第二章-实验判题-001/experiment-files/<id>`

### 2.1 共享内核环境（真实 pmap.c 的主机等价物）

沙箱单文件，故环境段内联进每个 harness。为避免 6 份手抄漂移，环境拆成可复用片段，
由 `scripts/assemble-harness.mjs <out.c> <fragment...>` 拼装：

| 片段 | 内容 | 被哪些实验内联 |
| --- | --- | --- |
| `ch2/env-list.c.inc` | `include/queue.h` 全部 LIST_* 宏（2.1 已验证版）、`struct Page`、`phys[]` 物理内存 arena、`page2pa/pa2page/page2kva`、`KADDR/PADDR`、`alloc`、`panic`、`tlb_invalidate`、驱动保护函数 | 2.3–2.7 |
| `ch2/env-pageinit.c.inc` | `page_init` | 2.4–2.7 |
| `ch2/env-alloc.c.inc` | `page_alloc` / `page_free` | 2.5–2.7 |
| `ch2/env-walk.c.inc` | `boot_pgdir_walk` / `pgdir_walk` | 2.6、2.7 |
| `ch2/env-map.c.inc` | `page_lookup` / `page_decref` + pmap.h 等价前置声明 | 2.7 |
| `ch2/env-detect.c.inc` | 2.2 专用最小环境（四个全局量 + CMOS 读数，无 arena） | 2.2 |

等价适配（语义不变，已在源码注释中写明）：

1. `u_long/u_int` → `uint32_t`：MinGW LLP64 下 `long` 是 32 位、Linux LP64 下是 64 位，
   用 `uint32_t` 才能保证两个平台判题结果一致。
2. 物理内存用 `static unsigned char phys[256*4096]`（1 MB BSS，demand-zero）模拟，
   **物理地址就是数组下标**，于是 `KADDR(pa)=phys+pa`、`PADDR(kva)=kva-phys`，
   与真实内核的线性映射语义相同，且不需要真实指针算术。
3. `panic` 不终止进程、`tlb_invalidate` 空操作（主机无 TLB）。
4. `pa2page` 对越界物理地址钳到最后一页 —— 学生写坏页表项时应判「答案错误」而非段错误。
5. 所有平台提供的 static 函数/变量带 `__attribute__((unused))`（`MOS_UNUSED`），
   否则某实验不用其中几个就会被 `-Werror=unused-function` 拒绝。

### 2.2 验证命令与结果（6/6 退出码 0）

```bash
node scripts/assemble-harness.mjs <实验目录>/starter.c <片段...>
node scripts/derive-harness-variants.mjs <实验目录>
node scripts/verify-experiment-harness.mjs <实验目录>
```

原始输出见 `verify-ch2.log`（145 行，含全部 30 条用例的三方输出）。

| 实验 | id | 学生填写目标 | 用例 | reference 互异 | starter 失败 | wrong 失败 | 退出码 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2.2 初始化全局变量 | `1908532874240593922` | `mips_detect_memory` | 5 | 5/5 | 4/5 | 4/5 | 0 |
| 2.3 空闲内存管理 | `1908538371526131713` | `page_init` | 5 | 5/5 | 5/5 | 4/5 | 0 |
| 2.4 内存分配与释放 | `1908539177407119361` | `page_alloc`/`page_free` | 5 | 5/5 | 5/5 | 2/5 | 0 |
| 2.5 页表创建 | `1908539875976839170` | `boot_pgdir_walk`/`pgdir_walk` | 5 | 5/5 | 5/5 | 2/5 | 0 |
| 2.6 地址映射 | `1908540360305704962` | `boot_map_segment` | 5 | 5/5 | 5/5 | 5/5 | 0 |
| 2.7 页表项插入与移除 | `1908540572415852545` | `page_insert`/`page_remove` | 5 | 5/5 | 5/5 | 1/5 | 0 |

- 18 个变体（6 实验 × starter/reference/wrong）全部以 `-std=c99 -Wall -Wextra -Werror -O2 -pipe` 编译，**零警告**。
- 30 条参考输出互异且非空；`judge-cases.json` 全部由脚本从参考解真实运行导出，未手写。
- 源码体积 1583–13718 B，远低于沙箱 64 KB 上限；单条输入最大 105 B，远低于 8 KB 上限。
- 唯一「纯边界用例」（starter 与 wrong 都通过）是 2.2 的零内存场景，属固有边界，已由脚本 NOTE 显式报出。

### 2.3 官方判定锚对照

| 实验 | 官方锚（`reference-pmap.c`） | harness 用例 | 实测 reference 输出 |
| --- | --- | --- | --- |
| 2.2 | 第 29–32 行 `maxpa=0x4000000; basemem=0x4000000; extmem=0; npage=maxpa/4096` | 用例 1（CMOS `65536 0`） | `67108864 67108864 0 16384` |
| 2.3 | 第 193–209 行 Step 1–4，头插 | 用例 1（8 页 / 已用 8192） | `7 6 5 4 3 2` + `8192 2 6 0` |
| 2.4 | `page_check()`：三次 alloc 互不相同、steal 后 `-E_NO_MEM`、free 后重 alloc 内容清零 | 用例 1、2 | `A 7 0 / A 6 0 / A 5 0 / F 7 0 / A 7 0 / A 4 0`；耗尽后 `A -4` |
| 2.5 | 第 102–108 行 create 分支、第 306 行 `ppage->pp_ref++` | 用例 1、2 | `B 8192 0`；`W 0 61440 0` + `Q 15 1` |
| 2.6 | 第 129–137 行 `size%BY2PG` 守卫 + `pa\|perm\|PTE_V` | 用例 1、2、3 | `M 4099 12291`；非对齐 size 不映射；perm=0 仍得 `65537` |
| 2.7 | 第 345–352 行同页提前返回、第 435–438 行 decref 回收 | 用例 1、2、3 | 重复 insert `pp_ref` 恒为 1；替换后旧页 `P 5 0`；remove 后 `Q 4294967295` |

### 2.4 崩溃安全性机械验证（harness-spec §6）

新增 `scripts/probe-harness-robustness.mjs`：对每个实验的 3 个变体各喂 16 条畸形输入
（空输入、全零、`4294967295`、负数、操作数溢出、未知操作码、截断操作、截断首行、
非数字垃圾、越界页号、越界 va、负 pp_ref、双重 free、free 未分配页、perm 溢出、size 溢出），
要求**全部 exit 0**。

```bash
node scripts/probe-harness-robustness.mjs <7 个第二章实验目录>
```

结果（`probe-ch2.log`）：**total runs=336 failures=0**（含 Task 1 的 2.1）。

关键保护点：所有链表步行带 `NPAGE_MAX` 步数上限；所有返回指针先过 `page_in_range`/`pte_in_range`；
驱动自有的 `va2pa_safe` 不依赖学生实现且对越界页表项免疫；输入在驱动边界严格校验，越界即 `return 0`。

### 2.5 过程中发现并修复的用例设计缺陷

验证脚本首轮即 6/6 通过，但**逐条复核用例是否真的覆盖了它声称的边界**时发现 4 处问题。
按「修 harness，不改期望值」原则，全部改的是**输入**，期望值始终由参考解真实运行导出。

| # | 问题 | 根因 | 修正 |
| --- | --- | --- | --- |
| 1 | 2.5 用例 5 名为「PTX 上下边界」，实际 `4198399=0x400FFF` 的 PTX 是 **0** 不是 1023 | 我把 `0x400FFF` 误当成第 2 个 4MB 区的末页；`0x400FFF>>12=0x400`，`&0x3FF=0` | 改用 `0x3FFFFF/0x400000/0x7FFFFF/0x800000`，实测输出 `B 8192 1023 / B 12288 0 / B 12288 1023 / B 16384 0`，真正覆盖边界 |
| 2 | 2.6 用例 5 名为「跨页目录项」，实际 `0x400000+12288` 三页全在 PDX=1 | 同上类换算错误 | 起点改 `0x3FF000`，三页跨 PDX 0→1，实测 `Q 4194304` 得 `135175` |
| 3 | 2.4 用例 2、2.5 用例 4、2.6 用例 2/4 区分度为零（三方同输出） | 纯「什么都不该发生」的负向用例，空实现天然通过 | 改为**先建立有效状态再触发守卫**：2.4 先分配 4 页再耗尽；2.5 先建 4 张页表再耗尽；2.6 先映射再发非法 size。修正后 2.6 五条全部可区分 |
| 4 | 2.6 输出契约描述不精确 | 页表存在但槽位无效时打印的是原值 `0`，只有页表不存在才是 `~0` | `cases.json` description 改为分两种情形精确描述 |

为把 #3 变成机械闸门，`verify-experiment-harness.mjs` 增加 NOTE 输出：
列出「starter 与 wrong 都通过」的纯边界用例，使其无法被静默忽略（不作为失败，因为合法边界确实存在）。

### 2.6 相对计划 Task 2 规格表的契约细化

计划 Step 2 的规格表是设计意图，实现时按可判题性做了三处细化（均已同步回计划）：

1. **2.2 输入改为两个 CMOS 读数** `basemem_kb extmem_kb`。计划原写单个 `basemem_kb`，
   但真实 `mips_detect_memory` 只有 4 行且 `extmem` 恒 0，单输入下「忘记单位换算」之外几乎没有可判据。
   双输入让 KB→字节、base/ext 拆分、字节→页数三个换算都成为独立可观测点；
   官方 `65536 0` 场景仍逐字复现 `maxpa=0x4000000 / npage=16384`。
2. **2.5 输出改为「页表物理地址 + PTX」**，而非计划写的 `PDX*1024+PTX`。
   后者是纯逻辑下标，驱动自己就能算出来，无法区分「返回了正确槽位」与「返回了另一张页表的同号槽位」。
   物理地址来自环境侧确定的分配器，因此可唯一标识页表身份。
3. **2.7 驱动增加「调用方已持有该页」的前置处理**：`I p va perm` 前，若页 p 仍挂在空闲链表上则摘下并把 `pp_ref` 归零；
   **已在用的页不动**。否则要么 `page_free` 把已在链表上的页二次插入造成环，
   要么驱动自己把 `pp_ref` 重置从而抹掉「重复 insert 不得重复递增」这条官方判据。

### 2.7 未验证项

- **沙箱内端到端判题**：Docker 不可用，未运行。本地 gcc 比沙箱更严（多 `-std=c99 -Wall -Wextra -Werror`），
  且已额外验证 argv 与 stdin 双通道、16 条畸形输入不崩溃，但不能替代沙箱实测。
- 后端 `JudgeServiceImpl` → 沙箱 → `DefaultJudgeStrategy` 全链路：未运行（Task 8）。
- 前端提交评测 UI：未运行（Task 11）。
- 第一章 Listing 原文缺口、Tier C 预验证状态见上文，未改变。

---

## Task 5 预验证：Tier C 概念 harness（1.1、1.3）

日期：2026-11-11（计划编写阶段预先验证，正式交付在 Task 5）

两套 harness 以 `-std=c99 -Wall -Wextra -Werror -O2 -pipe` 编译零警告，
5 条参考输出互异，starter 各在 5/5 条失败、wrong 各在 4/5 条失败。

- **1.1 Makefile 依赖重建顺序**：参考输出 `lib.o kernel.o modules objects vmlinuz OK` / `d b c a OK` /
  `CYCLE` / `all boot mm lib OK` / `objects CYCLE`。
  修正记录：初版环用例只输出裸 `CYCLE`，两条环用例输出撞车被「参考输出互异」闸门拦下；
  改为暴露 `*built` 部分构建进度后可区分，且更贴近 make 真实行为。
- **1.3 Linker Script 段加载地址**：参考输出 `.text 0x80000000 / .data 0x80002000 / .bss 0x80003000 / _end 0x80004000` 等 5 组。
  修正记录：`uint32_t vma[MAXS];` 未初始化触发 `-Werror=maybe-uninitialized`，改为 `= {0}`。

**内容来源缺口（已如实标注）**：工作区无 `include.mk`、顶层 Makefile、`tools/scse0_3.lds` 原文
（1.3 的 `originalCode`/`answer` 均 0 字节）。两套 harness 按 make/ld **公开语义**构造等价示例，
不冒充北航原文转录。

---

## 判题负载实测（回应 2G 服务器性能关切）

见 `baseline.md` 第 4.1 节。源码 630 B → 56 KB（89 倍），编译 325 ms → 360 ms（+11%），
运行耗时差异 2.6%，BSS 67 KB vs 268 KB。
**性能不构成删减北航说明的理由**；Tier C 的依据是可判题性与概念同构。
测量局限：MinGW/Windows，非沙箱 Alpine/musl；容器内耗时未测。

---

## Task 9：垃圾重复行清理 —— 只读核查结论：**不删除**

日期：2026-11-11（主 agent 执行，A/B 并行开工期间）
全部为 `SELECT`，**未执行任何写操作**。

### 9.1 五条候选行与孪生行实测

| 垃圾行 id | 标题 | content 字节 | content 实际值 | isDelete | 完好孪生行 id | 孪生 content 字节 |
| --- | --- | --- | --- | --- | --- | --- |
| `1918931948987031554` | 实验4.3 `sys_mem_alloc` | 9 | `分丰富` | 0 | `1930156605618573314` | 1019 |
| `1918932928252157953` | 实验4.5 `sys_mem_unmap` | 9 | `啊啊啊` | 0 | `1930156844614209538` | 1046 |
| `1918933052214812674` | 实验4.6 `sys_yield` | 5 | `11111` | 0 | `1930156972624367618` | 993 |
| `1918934540198682625` | 实验4.7 IPC | 6 | `333333` | 0 | `1930157095085461505` | 1224 |
| `1918938841746878466` | 实验4.8 `sys_env_alloc` | 14 | ` v持续上升` | 0 | `1930157212584693762` | 1177 |

五条垃圾行均存活（`isDelete=0`），每条都有一个标题完全相同、content 完好的孪生行。

### 9.2 引用核查（决定性证据）

| 表 | 每条垃圾行的引用数 | 合计 |
| --- | --- | --- |
| `question_set_map` | 1 | 5 |
| **`question_submit`** | **3** | **15** |
| `course_resource` | 0 | 0 |

`question_submit` 的 15 条提交全部发生在 **2025-04-03 20:31–21:42**（真实历史学生提交，
每条垃圾行 3 条，时间跨度 2 秒至 66 分钟不等）。

### 9.3 结论与依据

计划 Task 9 Step 3 原文规则：**「若有任何学生提交记录引用这些行，默认不删除，改为在报告中标注。」**

→ 15 条真实学生提交引用这 5 行，**触发默认不删除规则**。本轮**不执行任何软删除**，
`question` 表这 5 行保持 `isDelete=0` 原样，`question_set_map` 的 5 条映射同样不动。
因此**无需向用户索取删除确认**，也**不产生回滚 SQL**（没有写操作）。

保留的影响：这 5 条垃圾行仍在题集中可见，属于既有内容质量问题，**不是本轮引入的回归**。
若将来要清理，正确做法是先迁移/归档那 15 条提交记录，再软删除，且需用户单独确认。
本轮 v14 迁移**不包含**任何针对这 5 行的 `UPDATE`。

### 9.4 顺带实测：`course_resource` EXPERIMENT 覆盖缺口（供 Task 8 使用）

`resourceType='EXPERIMENT'` 实测仅 **11 行，全部 DRAFT**，已绑定的 questionId 为：
`1930155905954140162`(3.12)、`1930156027966443521`(3.13)、`1930156360117571585`(4.1)、
`1930156493454495745`(4.2)、`1930156605618573314`(4.3)、`1930156844614209538`(4.5)、
`1930157095085461505`(4.7)、`1930157212584693762`(4.8)、`1930157404163723265`(4.9)、
`1930157984642813953`(4.14)、`1930158086405017602`(4.15)。

→ **41 个真实实验中 30 个尚无 `course_resource` 行**（含全部第一/二章、第三章 11 个 Tier A、
第四章 4.4/4.6/4.10/4.11/4.12/4.13）。这些行须由既有导入 API 创建，
**不得手写 SQL 插入**（`publishedJson` 不变量由 `ExperimentMaintenanceService` 维护）。

---

## 10. 第四章 harness 完成验收（Task 4，14 个实验）

### 10.1 三门齐备证据

判据来自 `harness-spec.md` §10：**`verify` 退出码 0 且 `probe` 退出码 0 且已逐条复核用例覆盖度。
三者缺一不得声称该实验 harness 完成。**

- **verify**：14 个实验全部 `VERIFY OK: judge-cases.json written from real reference runs`。
  期望输出一律由真实 gcc 运行 `reference.c` 得到，再逐条与手算页账本核对（见 §10.3）。
- **probe**：`artifacts/experiment-judge/probe-ch4.log` 由真实重跑生成，
  14 个实验 × 3 变体 × 133 条探针 = **5586 次运行，failures=0**。
- **覆盖度**：`node artifacts/experiment-judge/coverage-ch4.mjs` 解析 verify 打印的三路真实输出，
  结论 `COVERAGE ch4: 用例总数=70 不合格实验=0` ——
  **70 个用例全部能区分「空填写」与参考解**（starter 与 reference 无一相同）。

| 实验 | id | 用例 | starter.c 字节 | 距上限余量 | judge-cases.json md5 |
| --- | --- | --- | --- | --- | --- |
| 4.2 handle_sys | `1930156493454495745` | 5 | 63878 | 1658 | `3abd22b3f7aca86d19c74079e16b1136` |
| 4.3 sys_mem_alloc | `1930156605618573314` | 5 | 64012 | 1524 | `79053589469d5ab285b00d7d3b5024b5` |
| 4.4 sys_mem_map | `1930156740377366529` | 5 | 63773 | 1763 | `2db298def37306013c00964b8b4ce164` |
| 4.5 sys_mem_unmap | `1930156844614209538` | 5 | 64116 | 1420 | `cd99de2de8a053e7c1d2037ba0180dfc` |
| 4.6 sys_yield | `1930156972624367618` | 5 | 64838 | 698 | `d4ee8398f4d10b4fbaeaaf07a634d637` |
| 4.7 sys_ipc_recv / sys_ipc_can_send | `1930157095085461505` | 5 | 64500 | 1036 | `d789811ab01575de3a1e19d326032ea4` |
| 4.8 sys_env_alloc | `1930157212584693762` | 5 | 64750 | 786 | `51d465d044b4e364f9822de17ec5b54c` |
| 4.9 fork（子进程分支） | `1930157404163723265` | 5 | 65233 | 303 | `2e7dc0cd0bbf5ba5ffe7d53fc6c683e9` |
| 4.10 duppage | `1930157516164222977` | 5 | 65038 | 498 | `53eb943ae004b01af312ac20d8be48ff` |
| 4.11 page_fault_handler | `1930157629511094274` | 5 | 46869 | 18667 | `e580d5e5060818098cfa0d6fc7af311d` |
| 4.12 sys_set_pgfault_handler | `1930157740261691394` | 5 | 64619 | 917 | `e96df2163f6478d870cfc681d11c96c4` |
| 4.13 pgfault | `1930157867273605121` | 5 | 64764 | 772 | `13ba1a79c0ac254274a3905ca3d4e602` |
| 4.14 sys_set_env_status | `1930157984642813953` | 5 | 64409 | 1127 | `dcdb1abc35fca05c0a6fa7c8b938db58` |
| 4.15 fork（父进程分支） | `1930158086405017602` | 5 | 65149 | 387 | `4c24061a568ab90f15a0baf8e35c66e4` |

4.1（`1930156360117571585`）是 **Tier B 本地构建**，只有 `judge-mode.json` + `local-build.md`，
无 `judge-config.json`，`completionRule=SUBMIT`，**不参与自动判题**，故不在上表。

### 10.2 每个实验的错误变体（fill-wrong.c）与判别力

| 实验 | 注入的典型错误 | wrong 与参考相同的用例 |
| --- | --- | --- |
| 4.2 | 结果写进 `$a0`(regs[4]) 而非 `$v0`(regs[2]) | 无（5/5 判别） |
| 4.3 | `page_insert` 失败时漏掉 `page_free(p)` | 1,2,4,5（仅用例 3 判别） |
| 4.4 | `page_insert` 前多写一次 `p->pp_ref++` | 1,2,5（用例 3,4 判别） |
| 4.5 | 先查 `page_lookup` 再解映射，破坏幂等性 | 1,3,5（用例 2,4 判别） |
| 4.6 | 把自己置 `NOT_RUNNABLE` 后再 `sched_yield` | 无（5/5 判别） |
| 4.7 | 漏掉 `env_ipc_receiving` 检查 | 无（5/5 判别） |
| 4.8 | 子进程置 `ENV_RUNNABLE` 而非 `NOT_RUNNABLE` | 4（用例 1,2,3,5 判别） |
| 4.9 | 子进程分支用返回的 `envid`(=0) 而非 `syscall_getenvid()` 算 `ENVX` | 无（5/5 判别） |
| 4.10 | 可写页只标子进程 COW，漏掉父进程自身那次 `mem_map` | 无（5/5 判别） |
| 4.11 | 写回页表项时硬写 `PTE_V\|PTE_R`，丢掉原有权限位 | 无（5/5 判别） |
| 4.12 | 异常栈申请在 `xstacktop` 而非 `xstacktop-BY2PG`（差一页） | 无（5/5 判别） |
| 4.13 | 把临时页映射回原地址时沿用 `perm`，COW 位未清除 | 无（5/5 判别） |
| 4.14 | 入就绪链表前不做 `in_sched_list` 判重，链表成环 | 无（5/5 判别） |
| 4.15 | 异常栈/handler/RUNNABLE 三件事的目标写成 0（父进程）而非子进程 envid | 无（5/5 判别） |

**诚实说明**：每个实验只注入**一个**代表性错误变体。4.3/4.4/4.5/4.8 的部分用例对该变体是同形的
（纯边界用例），但这些用例仍在断言其它契约 —— 学生犯**别的**错误时会在这些用例上失败。
`verify` 的失败判据是「starter 或 wrong 通过**全部**用例」，四个实验均不触发；
`coverage-ch4.mjs` 另加严判据「starter 必须在**每个**用例上都与参考不同」，14 个实验全部满足。

### 10.3 手算复核（期望输出不是「跑出来就信」）

每个用例的物理页账本、页表项数值、`pp_ref` 变迁、`mkenvid` 序列都先手算再与真实运行比对，
全部逐字相符。要点：

- 初始空闲 = `np - 4`（`fm=8192` → bump 分配器占 2 页页表 → `freemem=16384` → `page_init` 标记页 0..3 已用）。
- 空闲链表 LIFO，头是最高号页：`envs[63].cr3=1044480`(页255)、`envs[62]=1040384`(页254)、`envs[61]=1036288`(页253)。
- `mkenvid`：`envid=(生成号<<7)|下标`，故 `ENVX(envid)` 恒等于 PCB 下标；
  64 次分配后 `envs[0].env_id=8192`、`cr3=786432`(页192)、空闲 188 页。
- `page_insert` 到**同一物理页**只改权限、**不**递增 `pp_ref`；到不同物理页先 `page_remove` 旧页。
  这条决定了 fork 里 `set_pgfault_handler` 会回收前一次 `mem_alloc` 的栈页，
  故一次空 fork 净耗 3 页而非 4 页。
- 4.13 的内容断言：`va=1073750016`(UTOP+8192) → PA 8192 = 启动期第一张页表，
  前四项 3/4099/8195/12291 → 整页字节和 **108**、前 8 字节 **3 0 0 0 3 16 0 0**；
  缺页复制后校验和与字节序列必须原样保留。**`va=UTOP` 映射到全零的 PA 0，不能做内容源**
  （首轮误用该地址导致校验和为 0，已修正）。

### 10.4 本轮修复的两个真实缺陷

1. **4.14 用例 2 输入畸形**：`K 5 191 4294967295 0 0` 只有 5 个数（`K` 要求恰好 6 个），
   驱动边界守卫正确地 `return 0`，导致参考输出被截断成 7 行并触发「纯边界用例」NOTE。
   修正为 6 个数后重跑，输出恢复完整 14 行，NOTE 消失。
   随后对**全部 ch4 用例**做了操作符元数与声明条数校验：`checked 50 cases; issues = 0`。
2. **4.6 用例 3 判别力不足**：原用例只测「就绪链表为空时不得切换」，
   空实现天然满足，`coverage-ch4.mjs` 报 `starter与参考相同的用例=[3]`。
   已在该用例尾部追加「入链后再 yield 必须真的切换」，空实现现在会被区分。
   这是**加严覆盖度**，不是放宽判据。

### 10.5 共享片段改动的输出中立性证明

本轮为把用户态实验压进 65536 字节上限，改动了共享片段（详见 `ch4/protocol.md`「源码预算裁剪」）：
`ch3/env-pcb` 拆出 `ch3/env-recorder`、`env-user`/`env-usermem`/`user-fork`/`user-duppage` 瘦身、
驱动新增 `Y` 子进程夹具与 `Z` 整页观察并把 `K`/`V`/`D`/`X` 改为按宏门控、
`env-sysno` 注释瘦身、两个 `mk.sh` 更新。

**关键认识**：沙箱限制的是**源码文件字节数**，`#ifndef` 守卫只减少编译后的代码、
**不减少文件字节**，故必须物理排除整个片段。首轮误用守卫导致体积反而上升，已纠正。

证明方式：改动前先 `md5sum` 全部 32 个既有 `judge-cases.json` 存档，
改动后 `node artifacts/experiment-judge/sweep-rebuild.mjs` 串行重构建 24 个实验
（`SWEEP ok=24 fail=0`），再比对：**32 个既有文件 md5 逐字节相同**，
唯一差异是新增的 4.9/4.10/4.13/4.15 四个文件。ch1/ch2 不含 `env-pcb`，不受影响（实测 0 处引用）。
ch3 探针另跑：`3 variants x 76 inputs / total runs=228 failures=0`。

### 10.6 仍未验证的部分（不得声称已验证）

- **沙箱端到端未验证**：本机无 Docker，`代码沙箱/**` 属只读禁改区。
  所有结论都基于宿主 gcc（`-std=c99 -Wall -Wextra -Werror -O2 -pipe`）真实编译运行，
  与沙箱契约（单文件、`gcc -O2 -pipe main.c -o main`、输入同时作 stdin 与 argv、
  非零退出即 Runtime Error、65536 字节上限）逐项对齐，但**未在真实沙箱容器里跑过**。
- **导入落库未执行**：后端未启动（`application.yml` 端口 8121），Task 8 阻塞，
  数据库全程零写操作。

## 11. 接手后的最终验收（2026-11-11，覆盖上述历史未完成状态）

本节是后续实际执行结果；此前“未导入/零写入”等结论仅描述当时阶段。

- 已创建“第三章实验说明补齐”“第四章实验说明补齐”两个对话，创建参数及会话元数据均确认 `gpt-6.1-terra`。共补齐25篇缺失说明、压缩6篇过长说明的新增解释；主对话核对41篇原任务段未被删改。
- 主验收在临时副本重跑36个自动实验、三种实现、186组用例，36/36通过，judge-cases哈希36/36不变。内容一致性检查0错误，269条去重警告保留并解释自动核对边界。
- 主对话修正组包与真实API的两处接口不兼容（请求 `package` 外层、RELATED→SECONDARY角色适配）。四章包经真实preview/commit/SUBMIT/APPROVE/APPLY，并在重复导入时全部UNCHANGED。
- 迁移首次尝试因本机已有11条旧草稿正确拒绝，未改写题目。后续保留旧记录，在含真实审计历史的独立库导入生成11项revision2、30项revision1，再重新导出和验收。
- `v14`与公共前置seed已在全新库、存量副本分别重复执行，8张表摘要一致、41项内容与源文件一致；单元编辑与题目编辑分别被冲突保护拒绝。前置seed只补18单元的公共依赖，并验证编辑过的空模板及相同业务码不同ID会在写入前拒绝。
- 已在备份后将已验证SQL应用本机 `oslab_db`：41最新APPLIED、123节点、77活动，36资源PUBLISHED/5本地资源DRAFT；12单元发布新快照/6单元新稿待验证，原publishedJson保留。其他题目、单元、非实验活动、单元节点逐项摘要不变，用户/提交/作答/事件/AI数量不变。
- 补齐LOCAL_BUILD前后端及任务复制链路：直接提交及历史副本不能误入自动判题；任务引用采用人工验收；未知或无效模式拒绝。后端12项定向测试通过并打包成功。
- 前端lint为0错误/168既有警告，五项契约测试、生产build通过。真实浏览器验证320/390/1024/1440、说明独立滚动与粘性标签、草稿恢复、语言切换、图谱画布、导师只填入、本地构建禁用按钮；主对话复查截图。详细边界见 `ui-layout.md`。
- 部署增加v13、v14公共前置及v14迁移挂载；16项顺序及22条初始化SQL断言在原生MySQL通过，Shell语法和YAML检查通过。

未验证项仍是Docker隔离沙箱/真实AC与轮询、5项MIPS完整构建、真实模型请求、浏览器原生200%缩放及更宽表格边界。5条垃圾重复行没有软删除；没有Git提交、推送或远程部署。

最终逐项矩阵与证据索引：`validation.md`；演示步骤：`demo.md`；本机应用日志：`local-apply.log`。目标行备份保存在系统TEMP，凭据未写入仓库。

## 12. 用户追加的代码注释清理（2026-11-11）

- 193个C/h/c.inc文件去除院校冠名、代理分工、内部报告引用及材料盘点；逐文件与TEMP备份比较，所有非注释字符、字符串字面量和学生填写标记完全相同。
- 共享片段同步清理；重建脚本忽略body内部已包含的共享驱动标记，防止二次拼接。重新捕获的11个第三章与14个第四章配方均与当前starter.c逐字相同。
- 原v14 SQL保持原哈希不变。新增oslab_upgrade_v14_experiment_comments.sql，在原v14之后执行，仅改41项当前originalCode/answer并追加80条实际审核后的修订，保留旧审计历史。
- 在本轮创建的全新与存量验证库回到原v14代码/修订基线后，增量应用两次摘要相同；独立冲突输入被拒绝；10张其他表及题目其他字段摘要不变。经本机目标行备份后，同一SQL已应用oslab_db。
- Compose与初始化加载增加独立注释增量，当前17项顺序、22条SQL断言通过；仍未运行Docker。当前版本矩阵和SQL哈希见validation.md。
