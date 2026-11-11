# Agent B 交付报告 —— Task 5 / Task 7 / Task 6(16 篇) / Task 11

分支 `feature/ai-upgrade`。环境：Windows / MinGW-w64 gcc 8.1.0 / Node v24.15.0 / MySQL 8.4.8。
**Docker 不可用 → 沙箱内端到端判题全程未验证。**
**未做任何 git 写操作、未做任何数据库写操作。**

---

## 0. 进度台账（每完成一项追加一行，供主 agent 精确接续）

| # | 项 | 状态 | 证据 |
| --- | --- | --- | --- |
| B01 | 读 harness-spec / 计划 C1–C7、D1–D4、Task 5/6/7/11 / verification.md / canonical 2.7 模板 | 完成 | — |
| B02 | 导出 `current-content.tsv`(46 行) / `registry.txt`(199 节点) / `units.txt`(105 单元) | 完成 | 三个文件已落盘，只读 SELECT |
| B03 | 建 4 个批次目录 + 9 个实验目录（第一章 5、2.8、3.12、3.13、4.1） | 完成 | `ls` 确认 |
| B04 | Tier C 1.1 harness（`1917131319528452098`）8 文件 | 完成 | verify-tierc.log EXIT=0 |
| B05 | Tier C 1.3 harness（`1930154079598989313`）8 文件 | 完成 | verify-tierc.log EXIT=0 |
| B06 | Tier A 1.2 ELF harness（`1930153941795131394`）8 文件 | 完成 | verify-ch1.log EXIT=0 |
| B07 | Tier A 1.5 printf harness（`1930154460768948226`）8 文件 | 完成 | verify-ch1.log EXIT=0 |
| B08 | 1.5 参考解 vs 宿主 libc snprintf 交叉校验 | 完成 | ch1/xcheck-printf.log：7 MATCH / 1 SKIP / exit 0 |
| B09 | `ch1/probes.json`（38 条）+ 4 实验 × 3 变体崩溃探测 | 完成 | probe-ch1.log：456 runs / 0 failures / exit 0 |
| B10 | 逐条人工复核 4 个 harness 的用例覆盖度（§5.1） | 完成 | 见 §1.4 |
| B11 | 5 个 Tier B 脚手架（judge-mode/local-build/starter/cases） | 完成 | 5 目录各 4 文件、**无 judge-cases.json**；5 个 starter.c 全部 `gcc -fsyntax-only -Werror` SYNTAX OK |
| B12 | Tier C 1.1/1.3 的 local-build.md | 完成 | 两目录各 9 文件（8 harness + local-build.md）；均含「内容来源说明」诚实标注 |
| B13 | Task 7：41 个 nodes.json + units.json | 完成 | 82 文件落盘；123 条 nodeMappings / 77 条 unitMappings；32 个节点 / 18 个单元 |
| B14 | `scripts/check-experiment-mappings.mjs` + 真实运行 | 完成 | exit 0，0 errors；另做 9 项变异负测试全部 exit 1（ch1/check-mappings-negative.log） |
| B15 | `artifacts/experiment-judge/mapping.md` 41 行总表 | 完成 | 19365 B，41 行总表 + 节点/单元频次 + nodeProposals |
| B16 | Task 6：16 篇 content.md | 待做 | — |
| B17 | `scripts/check-content-consistency.mjs` + 对 16 篇真实运行 | 待做 | — |
| B18 | Task 11：UI 布局 + `ui-layout.md` | 待做 | — |

---

## 1. Task 5 —— 第一章 4 个 harness（三项闸门实测）

### 1.1 闸门 1：`verify-experiment-harness.mjs` 退出码

| 实验 | id | Tier | 用例数 | reference 互异 | starter 失败 | wrong 失败 | 退出码 | 日志 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 构建 Minix 内核 | `1917131319528452098` | C | 6 | 6/6 | 6/6 | 5/6 | **0** | verify-tierc.log |
| 1.2 深入理解 ELF 文件格式 | `1930153941795131394` | A | 6 | 6/6 | 6/6 | 5/6 | **0** | verify-ch1.log |
| 1.3 使用 Linker Script 控制内核加载地址 | `1930154079598989313` | C | 6 | 6/6 | 6/6 | 5/6 | **0** | verify-tierc.log |
| 1.5 实现内核中的 printf 函数 | `1930154460768948226` | A | 8 | 8/8 | 8/8 | 5/8 | **0** | verify-ch1.log |

- 12 个变体全部以 `gcc -std=c99 -Wall -Wextra -Werror -O2 -pipe` 编译，**零警告**。
- 26 条参考输出互异且非空；`judge-cases.json` 全部由脚本从参考解真实运行导出，**未手写**。
- 源码体积 2805–10349 B（远低于沙箱 64 KB）；单条输入最大 1613 B（远低于 8 KB）。
- **脚本未打印任何 `NOTE: n 个用例为纯边界用例`** —— 即不存在「starter 与 wrong 都通过」的零区分度用例。

### 1.2 闸门 2：`probe-harness-robustness.mjs --probes ch1/probes.json`

```
probed .../1917131319528452098: 3 variants x 38 inputs
probed .../1930153941795131394: 3 variants x 38 inputs
probed .../1930154079598989313: 3 variants x 38 inputs
probed .../1930154460768948226: 3 variants x 38 inputs
total runs=456 failures=0
PROBE_EXIT=0
```

38 条探针覆盖：空输入 / 全零 / `4294967295` / 负数 / 非数字垃圾 / 截断首行，
以及四种 stdin 语法各自的越界与畸形（1.1 的 n 溢出、依赖下标越界、依赖个数越界、负依赖、截断依赖、超长目标名、全自环；
1.3 的 n 溢出、size/align 溢出、截断段、负 size；
1.2 的 shnum 溢出、shentsize 过小、shoff 非对齐、非法 hex、hex 长度不足、shoff 溢出、hex 行数截断；
1.5 的空格式串、缺参数个数行、个数溢出、非法参数种类、int32 溢出、int 尾部垃圾、width/precision 超两位、
未闭合 `%`、只有 flags、250 字符超长格式、参数耗尽、`%c` 输出 NUL、缺参数值）。

### 1.3 逐条参考输出（由参考解真实运行导出，摘自 verify 日志）

**1.1（`1917131319528452098`）**

| # | 用例 | reference | starter | wrong |
| --- | --- | --- | --- | --- |
| 1 | mos-module-chain | `lib.o kernel.o modules objects vmlinuz OK` | `OK` | `vmlinuz kernel.o lib.o modules objects OK` |
| 2 | diamond-tiebreak | `d b c a OK` | `OK` | `a b c d OK` |
| 3 | three-cycle | `CYCLE` | `OK` | `a b c OK` |
| 4 | all-independent | `all boot mm lib OK` | `OK` | `all boot mm lib OK` |
| 5 | self-loop-with-buildable-prefix | `objects CYCLE` | `OK` | `vmlinuz objects OK` |
| 6 | kahn-min-index-vs-dfs-postorder | `c d b all OK` | `OK` | `all b c d OK` |

**1.2（`1930153941795131394`）**

| # | 用例 | reference | wrong |
| --- | --- | --- | --- |
| 1 | shnum3-shoff52-text-data-bss | `0:d:0x80000000` / `1:d:0x80010000` / `2:d:0x80020000` | `0:d:0x80000000` / `1:d:0x800000` / `2:d:0x10008000` |
| 2 | shnum8-nonzero-shoff256 | `0:d:0x0` / `1:d:0x80000000` / `2:d:0x80010000` / `3:d:0x80020000` / `4:d:0x80030000` / `5:d:0x0` / `6:d:0x0` / `7:d:0x0` | 8 行全 `0x0` |
| 3 | shnum16-stride48-shoff128 | 16 行 `0..f:d:0x8000?000`（第 4/9/e 项为 `0x0`） | 16 行错位值 |
| 4 | zero-addr-sections-not-skipped | `0:d:0x0` / `1:d:0x80000000` / `2:d:0x0` / `3:d:0x0` | 4 行全 `0x0` |
| 5 | single-section-minimal | `0:d:0x80000000` | `0:d:0x80000000`（同参考，固有边界） |
| 6 | max-shoff4096-stride64 | `0:d:0x80000000` / `1:d:0x80001000` / `2:d:0x80002000` | `0:d:0x80000000` / `1:d:0x800000` / `2:d:0x10008000` |

starter 在 6/6 条上输出空串（空实现不打印）。

**1.3（`1930154079598989313`）**

| # | 用例 | reference | wrong |
| --- | --- | --- | --- |
| 1 | kernel-typical | `.text 0x80000000` / `.data 0x80002000` / `.bss 0x80003000` / `_end 0x80004000` | `.bss 0x800023e8` / `_end 0x80002be8` |
| 2 | zero-length-bss | `.text 0x80010000` / `.data 0x80011000` / `.bss 0x80012000` / `_end 0x80012000` | `.bss 0x80011001` / `_end 0x80011001` |
| 3 | unaligned-base | `.text 0x80001000` / `.data 0x80001064` / `_end 0x80002000` | `.text 0x80000100` / `.data 0x80000164` / `_end 0x800001c8` |
| 4 | single-section | `.text 0x80000000` / `_end 0x80001000` | `_end 0x80000001` |
| 5 | large-align | `.text 0x80000000` / `.data 0x80010000` / `.bss 0x80011000` / `_end 0x80013000` | 同参考（固有边界：全部天然对齐） |
| 6 | zero-align-and-small-align-on-unaligned | `.text 0x80000100` / `.data 0x80000168` / `_end 0x80001000` | `.data 0x80000164` / `_end 0x800001c8` |

starter 在 6/6 条上输出全 `0x00000000`。

**1.5（`1930154460768948226`）**

| # | 用例 | reference | wrong |
| --- | --- | --- | --- |
| 1 | four-specifiers-d-x-c-s | `d=42 x=ff c=A s=MOS` | 同参考（无宽度，固有边界） |
| 2 | width-zero-pad-and-left-align | `[-0000042][      ff][7       ]` | `[-42][ff][7]` |
| 3 | string-precision-truncate-and-pad | `[abc][       abc][abc       ][ab]` | `[abc][abc][abc][ab]` |
| 4 | int-precision-and-zero-flag-ignored | `[00042][   00042][   00042][][]` | `[00042][00042][00042][][]` |
| 5 | sign-flags-plus-and-space | `[+42][ 42][-42][-0000042]` | `[+42][ 42][-42][-42]` |
| 6 | percent-literal-and-unknown-conversion | `100% done 7!%q` | 同参考（无宽度，固有边界） |
| 7 | left-align-overrides-zero-plus-char-width | `[42      ][ab      ][      ab][0000beef][    A]` | `[42][ab][ab][beef][A]` |
| 8 | negative-hex-and-int32-extremes | `ffffffff\|-2147483648\|2147483647` | 同参考（无宽度，固有边界） |

starter 在 8/8 条上输出空串。

### 1.4 闸门 3：逐条人工复核用例覆盖度（harness-spec §5.1）

**1.1**
1. `mos-module-chain`：真实 MOS 模块名链 `vmlinuz ← {modules, objects} ← kernel.o ← lib.o`，覆盖多级依赖 + modules/objects 同时就绪的 tie-break。
2. `diamond-tiebreak`：菱形依赖，b/c 同时就绪取编号最小者。
3. `three-cycle`：三目标全环，`*built=0` → 裸 `CYCLE`。
4. `all-independent`：无依赖退化场景。**同时是「编号最小」与「名字字典序」的判别用例**：参考输出 `all boot mm lib`（下标序），若按名字排序会得 `all boot lib mm`。wrong 在此条通过属固有边界（无依赖时输入序即正确序）。
5. `self-loop-with-buildable-prefix`：自环 + 可构建前缀 `objects`，输出 `objects CYCLE`，与用例 3 的裸 `CYCLE` 可区分（这正是预验证阶段踩过的坑）。
6. `kahn-min-index-vs-dfs-postorder`：**本轮新增**。原 5 条用例里 Kahn-最小就绪编号与 DFS 后序恰好同输出，无法检出「用 DFS 冒充拓扑序」的实现。本例 `all←{b,c}, b←d`：Kahn 得 `c d b all`，DFS 后序得 `d b c all`，两者可区分。

**1.2**
1. `shnum3-shoff52`：最小 shoff（紧跟 52 字节 ELF header）、shentsize=40、三个真实节。
2. `shnum8-nonzero-shoff256`：非零 shoff + 8 节 + 含 4 个地址为 0 的节（NULL/STRTAB/SYMTAB），检出「跳过地址 0 的节」错误。
3. `shnum16-stride48`：**shentsize=48≠40**，专门检出「把记录长度硬编码成 40」；shnum=16 触上限；序号 `a..f` 验证 `%x` 是十六进制而非十进制。
4. `zero-addr-sections`：shoff=64、4 节、3 个地址 0，再次锁定「0 地址必须输出」。
5. `single-section`：n=1 最小边界。wrong 在此条通过属**固有边界**（只有一个记录时步长错误不可见）。
6. `max-shoff4096-stride64`：shoff 与 shentsize 同时触上限，验证偏移算术不溢出、不越界。

**1.3**
1. `kernel-typical`：内核典型布局，段间 ALIGN 与末尾按页取整都生效。
2. `zero-length-bss`：零长段仍推进到对齐边界；`_end` 恰好落在页边界（不再多进一页）。
3. `unaligned-base`：base=0x80000100 非对齐，首段必须被抬到 0x80001000。
4. `single-section`：n=1、align=1（对齐无操作）、size=1 → `_end` 必须进位到下一页。
5. `large-align`：align=65536 > PAGE_SIZE。wrong 在此条通过属**固有边界**（所有量天然对齐）。
6. `zero-align-and-small-align-on-unaligned`：**本轮新增**。契约明写「align 为 0 时视为不对齐」，原 5 条无一覆盖 align=0；本例同时覆盖 align=0 与「小对齐值作用在非对齐地址上」（0x80000164 → align 8 → 0x80000168）。

**1.5**
1. 四种说明符 `d/x/c/s` 基本正确性（wrong 通过，固有边界：无宽度）。
2. `%08d` 负数零填充（符号在前）、`%8x` 空格右对齐、`%-8d` 左对齐。
3. `%.3s` 截断、`%10.3s` 截断+右对齐、`%-10.3s` 截断+左对齐、`%.9s` 精度大于串长（不补白）。
4. `%.5d` 前导零、`%8.5d` 精度+宽度、`%08.5d` **指定精度时 0 flag 失效**、`%.0d`/`%.0x` 值为 0 且精度 0 → 空数字串。
5. `%+d` 强制正号、`% d` 正数前空格、负数不受 plus/space 影响、`%08d` 负数。
6. `%%` 字面百分号 + **普通数字文本 `100`**（见 §1.5 缺陷 A）+ `%c` + 未识别转换 `%q` 原样输出。
7. `%-08d` 左对齐压倒 0 flag、`%-8s`/`%8s` 字符串对齐、`%08x`、`%5c` 字符宽度。
8. `%x` 负数按无符号解释（`ffffffff`）、INT32_MIN/INT32_MAX 极值（检出用 `int` 取反导致的溢出 UB）。

**独立正确性验证（§9）**：1.5 的参考解另用 `ch1/xcheck-printf.mjs` 与宿主 libc `snprintf` 在同一组 (fmt, args) 上逐用例比对：
```
MATCH  [1] four-specifiers-d-x-c-s
MATCH  [2] width-zero-pad-and-left-align
MATCH  [3] string-precision-truncate-and-pad
MATCH  [4] int-precision-and-zero-flag-ignored
MATCH  [5] sign-flags-plus-and-space
SKIP  [6] percent-literal-and-unknown-conversion: 含未识别转换字符，libc 行为未定义，契约由 harness 定义
MATCH  [7] left-align-overrides-zero-plus-char-width
MATCH  [8] negative-hex-and-int32-extremes
checked=7 skipped=1
XCHECK_EXIT=0
```
用例 6 含 `%q`，libc 对未定义转换的行为本身是 UB，无法作为真值，故如实跳过（该条契约由 harness 显式定义）。

### 1.5 过程中发现并修复的真实缺陷

**缺陷 A（本轮新发现，1.5 驱动）：数字串守卫误伤格式串里的普通数字文本。**
初版驱动为了把 width/precision 限制在 0..99（保证输出有界），对**整个格式串**扫描「任何连续数字串 ≤2 位」。
结果用例 6 的格式串 `100%% done %d%c%q` 里的字面量 `100` 被判为非法输入，驱动直接 `return 0`，
参考输出为空 → verify 报 `reference produced empty output for some case`，退出码 1。
根因：把「转换说明内部的宽度/精度」与「格式串里的普通文本」混为一谈。
修法：改成只在 `%` 之后进入转换说明扫描（跳过 flags → 数字 → `.`数字 → 转换字符），
仅对这两处数字串限长；`%` 之外的数字文本不受限制。该扫描只做输入合法性判定，不计算任何输出，
因此不会把学生的格式解析工作抢过来。修后 8/8 通过。

**缺陷 B（预防性加固，1.1 驱动）：学生返回的 `*built` 与 `order[]` 未过范围校验。**
计划原版驱动直接 `for (i=0;i<cnt;i++) printf("%s ", name[order[i]]);`。
错误实现若把 `*built` 写成大于 MAXT 的值、或把 `order[]` 填成越界下标，就会越界读 →
访问违例 → 沙箱判 Runtime Error 而非答案错误（harness-spec §6 明令禁止）。
修法：`cnt` 钳到 `[0, MAXT]`，每个 `order[i]` 先校验 `[0, n)`，越界打印哨兵 `?`。
参考解行为不变（`cnt ≤ n ≤ MAXT`、下标恒合法）。

**缺陷 C（预防性加固，1.1 驱动）：`int order[MAXT];` 未初始化。**
按 §6「驱动自有输出缓冲必须在声明处初始化」改为 `= {0}`，避免 `-Werror=maybe-uninitialized`。
1.3 的 `uint32_t vma[MAXS] = {0}` 沿用计划已修正版本。

**坑规避确认**：预验证记录的两个坑均未重犯 —— 1.1 两条环用例通过 `*built` 部分进度区分（`CYCLE` vs `objects CYCLE`）；
1.3 的 `vma[]` 已 `= {0}`。

### 1.6 相对计划 Task 5 的偏差（均为 §5.1 覆盖度驱动，契约未放宽）

1. 1.1 与 1.3 各**新增 1 条用例**（第 6 条），原 5 条输入与参考输出逐字不变，与 verification.md 的预验证记录一致。
   新增理由见 §1.4：原用例集无法区分 Kahn-最小就绪编号 vs DFS 后序（1.1）、未覆盖契约明写的 align=0（1.3）。
2. 1.1 驱动增加 `cnt`/`order[]` 范围校验与 `order[] = {0}`（§6 要求，计划原版缺失）。
3. 1.2 / 1.5 计划只给了规格要点，harness 由本轮设计实现，契约见各目录 `cases.json` 的 `description`。
4. 1.1/1.3 是独立环境（无共享内核段），直接写 `starter.c`；1.2/1.5 按 §3 拆成
   `ch1/env-elf.c.inc` + `ch1/body-1930153941795131394.c`、`ch1/env-print.c.inc` + `ch1/body-1930154460768948226.c`，
   用 `assemble-harness.mjs` 拼装。
5. `judge-config.json`：1.1/1.3 按计划用 `{1000,128,8192}`；1.2/1.5 照抄第二章 `{2000,256,8192}`。
---

## 2. Task 5 Step 6 —— 5 个 Tier B 脚手架（不伪造自动判题）

5 个目录各 **4 个文件**：`judge-mode.json`、`local-build.md`、`starter.c`、`cases.json`（`{"cases":[]}`）。
**均无 `judge-cases.json`、无 `judge-config.json`、无 reference/wrong 变体** —— 平台不会对它们发起判题。
5 个 `starter.c` 全部通过 `gcc -std=c99 -Wall -Wextra -Werror -O2 -pipe -fsyntax-only`（SYNTAX OK × 5）。

| 实验 | id | 目录 | 不可自动判题的具体原因（摘自 judge-mode.json） |
| --- | --- | --- | --- |
| 1.4 MIPS 汇编与 C 交互 | `1930154331844431873` | 第一章 | 目标是 `boot/start.S` 的 MIPS 汇编（设 `$sp` 后跳 `main`）+ 观察交叉编译器生成的栈操作/`slti`/`bnez`/`jal`/`jr`。沙箱只有 x86-64 gcc，无 MIPS 交叉编译器、无 gxemul，单文件 C 无法承载 `.S` 与特权启动时序。 |
| 2.8 TLB 管理 | `1908540698916061185` | 第二章 | 目标是在 `mm/tlb_asm.S` 的 `tlb_out` 填 `tlbp`/`tlbwi` 两条特权指令并解释其后 4 条 `nop` 的 CP0 冒险。二者直接操作 CP0 EntryHi/EntryLo0/EntryLo1/Index 与真实 TLB 硬件；沙箱是 x86-64 用户态进程，无 CP0。用 C 写「像那样」的程序**不构成概念同构**，只会变成被否决的代理题。 |
| 3.12 异常处理 | `1930155905954140162` | 第三章 | 目标是改 lds 把异常入口固定到物理 `0x80000080`，并在该入口写 MIPS 异常分发汇编（读 CP0 Cause 的 ExcCode/IP 位段）。需要 MIPS 交叉链接器 + 真实 CP0 异常时序 + gxemul 触发异常；沙箱无法把代码链接到指定物理地址。 |
| 3.13 时钟中断 | `1930156027966443521` | 第三章 | 目标是 `kclock_init` 设定时器频率、把时钟中断绑到 4 号中断线、lds 配置入口、`timer_irq` 调 `sched_yield`。需要真实定时器硬件（8253/8254 或 CP0 Count/Compare）与 `Status.IM4`/`Cause.IP4` 中断时序。 |
| 4.1 用户→内核系统调用机制 | `1930156360117571585` | 第四章 | 目标是 `user/syscall_wrap.S` 的 `syscall` 封装：按 o32 把调用号/参数搬进 `$v0`/`$a0-$a3`（第 5 个及以后走栈），执行特权 `syscall` 陷入内核态，再从 `$v0` 取返回值。触发的是真实 CP0 异常（ExcCode=8 Sys）并切换内核栈与内核页表。 |

**红线遵守**：没有为任何一个 Tier B 编造算法代理题；`cases.json` 一律 `{"cases":[]}`；
`starter.c` 顶部均显式标注「本文件不参与平台判分」，仅为本地分析/练习对象或注释形式的汇编骨架。
5 份 `local-build.md` 均按真实北航步骤给出（前置工具链 → 阅读/修改目标文件 → 构建 → objdump/readelf/gxemul 验证 → 提交物），
并在「内容来源说明」里如实写明 `originalCode`/`answer` 为 0 字节、骨架是**按公开语义构造的等价示例，不是北航原 Listing 的转录**。

**Tier C 未自行扩大**：只有 1.1、1.3 两个。本轮未发现新的、够格的概念同构候选，`tierProposals` 为空（见 §7）。

---

## 3. Task 5 —— 2 个 Tier C 的同构依据与诚实标注（逐字）

### 3.1 同构依据（不是「性能上跑得动」，而是「判的量就是学生要核对的量」）

- **1.1 make 依赖重建顺序**：make 判定「哪些目标需要重建、以什么顺序重建」的本质就是**目标依赖图的拓扑序**；
  有环时 make 报 `circular dependency` 并停止。子任务的输入是「目标名 + 依赖下标表」，输出是合法重建顺序，
  环时输出 `CYCLE` 并给出 `*built` 部分进度。**观测量 = make 自己会打印的构建顺序**，
  学生可用 `make -p` / `make --debug=b` 在本地核对同一张图。同构成立。
- **1.3 ld SECTIONS 段加载地址**：ld 的 location counter 语义是「逐段 `ALIGN` 向上对齐后顺序放置」，
  `_end` 按页对齐。子任务输入「起始 VMA + 每段 (name,size,align)」，输出每段 VMA 与 `_end`。
  **观测量 = 学生用 `readelf -S` / `readelf -s` 核对的那组地址**，一模一样。同构成立。

两者的共同点：判题观测量都是**公开工具链语义可精确定义、且学生在真实实验里本来就要人工核对的量**，
因此不是「换了个皮的算法题」。

### 3.2 诚实标注（逐字摘自交付文件，未改写）

`A-第一章-实验判题-001/experiment-files/1917131319528452098/local-build.md` 开头：

> **平台自动判分的只是「make 依赖图重建顺序」这一个概念子任务。**
> **AC 不等于内核构建成功。** 真实构建必须在本地 MIPS 交叉编译环境完成，步骤如下。
> 子任务与真实任务的关系：make 的合法重建顺序就是目标依赖图的拓扑序，
> 判的是 Makefile 语义本身；但它**不能**替代交叉编译器路径配置、链接脚本、镜像生成与 gxemul 启动验证。

同文件 §0：

> 工作区**没有** `include.mk` 与顶层 Makefile 的 Listing 原文：
> 实测 `question.id=1917131319528452098` 的 `originalCode` 仅 **43 字节垃圾**、`answer` 仅 **48 字节**，
> `content` 中只有对 `Listing 1: 顶层 Makefile`、`Listing 2: include.mk` 的**引用**，没有 Listing 正文。
> …… 是**按 make 公开语义构造的等价示例**，目标名取自工作区说明中真实出现的词，
> **不是北航原 Makefile 的转录**；

`A-第一章-实验判题-001/experiment-files/1930154079598989313/local-build.md` 开头：

> **平台自动判分的只是「ld SECTIONS 段加载地址计算」这一个概念子任务。**
> **AC 不等于内核链接成功。** 真实链接与启动验证必须在本地 MIPS 交叉编译环境完成，步骤如下。
> 子任务与真实任务的关系：判题观测量（各段 VMA 与 `_end`）**就是**你用 `readelf` 核对的那个量；
> 但它**不能**替代真实 lds 语法、段属性（`NOBITS`/`ALLOC`）、符号定义、交叉链接与 gxemul 启动验证。

同文件 §0：

> 工作区**没有** `tools/scse0_3.lds` 原文：
> 实测 `question.id=1930154079598989313` 的 `originalCode` 与 `answer` **均为 0 字节**，
> `content` 中只有「阅读 `tools/scse0_3.lds` 文件」的**要求**，没有文件正文。
> …… 规则在题面中显式给出，**不是 MOS 私有 lds 的转录**；

两份 `content.md` 顶部同样带「平台自动判分的是 <概念子任务>；AC 不等于内核构建/链接成功」声明（Task 6 交付）。

---

## 4. 第一章内容来源缺口（如实记录，未编造补齐）

| 实验 | `content` 字节 | `originalCode` 字节 | `answer` 字节 | 缺什么 | 本轮怎么处理 |
| --- | --- | --- | --- | --- | --- |
| 1.1 构建 Minix 内核 | 1800 | **43（垃圾）** | **48** | `Listing 1: 顶层 Makefile`、`Listing 2: include.mk` 正文 | 依赖图按 make 公开语义构造，目标名（`vmlinuz`/`modules`/`objects`/`kernel.o`/`lib.o`）取自 content 中真实出现的词；显式标注非原文转录 |
| 1.2 深入理解 ELF | 1877 | **0** | **0** | `readelf()` 的原始 Listing | ELF32 结构体按公开 ELF 规范定义；I/O 契约 `%x:d:0x%x\n` 逐字采用 content 中给出的输出格式 |
| 1.3 Linker Script | 1671 | **0** | **0** | `tools/scse0_3.lds` 正文 | 布局规则按 ld `SECTIONS` 公开语义构造并在题面显式给出；lds 骨架标注为等价示例 |
| 1.4 MIPS 汇编与 C | 1828 | **0** | **0** | `boot/start.S` 正文 | Tier B，不判题；`local-build.md` 给按公开 o32/启动语义构造的骨架并标注 |
| 1.5 内核 printf | 1571 | **0** | **0** | `lib/print.c` 的 `_Printf()` 正文 | 按 content 要求的功能子集（`%d %x %c %s` + 宽度 + `0` 填充 + 左对齐 + 精度）构造；参考解另与宿主 libc `snprintf` 交叉校验（7 MATCH / 1 SKIP） |

**结论**：第一章 5 个实验的 BUAA 原始 Listing **一个都没有随工作区提供**。
所有代码级内容都是「按公开语义构造的等价实现」，已在 `local-build.md` 与 `content.md` 中逐处标注，
**没有任何一处冒充北航原文转录**。`content` 字段里的任务描述/知识要点/实验任务原文全部保留（Task 6 只扩写不删改）。

对照：全库 41 个实验里只有 **2.1**（`include/queue.h`，8292 B originalCode / 8493 B answer）与
**3.1**（`mm/pmap.c`，21130 B originalCode / 1432 B answer）有真实源码，其余 39 个 `originalCode`/`answer` 为 0 或个位数字节。

---

## 5. Task 7 —— 41 个实验的图谱节点映射与学习单元映射

### 5.1 交付物

- **82 个 JSON**：41 个实验目录各一份 `nodes.json` + `units.json`（全部 41 个归 Agent B，含第三/四章 25 个 Tier A）。
- **`artifacts/experiment-judge/mapping.md`**（19365 B）：41 行总表（实验 id / 标题 / Tier / PRIMARY / RELATED+weight / 目标单元+完成规则 / 真实目标函数）
  + 节点使用频次表 + 单元使用频次表 + `nodeProposals` + `tierProposals`。
- **生成器**：`artifacts/experiment-judge/ch1/gen-all-mappings.mjs`（放在 ch1/ 是因为那是 Agent B 独占可写的 artifact 目录）。
  `nodeName` 一律**从 registry.txt 现场读取**，不手抄，从机制上保证逐字相等。
- **校验器**：`scripts/check-experiment-mappings.mjs`（新文件，归 Agent B）。**独立于生成器**，只读落盘 JSON + 导出清单，不复用生成器数据结构。

规模：123 条 nodeMappings（41 PRIMARY + 82 RELATED）、77 条 unitMappings；用到 **32 / 199** 个节点、**18 / 104** 个单元。
Tier 分布 **A=34 / B=5 / C=2**，与计划一致。

### 5.2 实测发现：已发布单元是 **104** 个，不是计划写的 105 个

计划 md 与任务书都写「105 个已发布 `learning_unit`」。只读 SELECT 实测：

```
SELECT COUNT(*) FROM learning_unit WHERE courseCode='course.operating-systems' AND isDelete=0;  → 104
SELECT COUNT(DISTINCT unitCode) ...                                                            → 104
SELECT isDelete, COUNT(*) ... GROUP BY isDelete;                                               → 0 | 104   （无软删除行）
SELECT reviewStatus, COUNT(*), SUM(publishedJson IS NULL), SUM(publishedJson IS NOT NULL) ...
                                                                                               → PUBLISHED | 104 | 0 | 104
```

即 **104 个单元全部 `reviewStatus='PUBLISHED'` 且 `publishedJson` 非 NULL**，没有第 105 个（也没有未发布/软删除的补数）。
`units.txt` 的 108 行 = 3 行表框/表头 + **104 行数据** + 1 行表框，与 DB 一致。
按任务书「以实际清单为准」，生成器与校验器都断言 **104**，`mapping.md` 里显式记录了这处计划偏差。
`knowledge_node_registry` 实测 199（`COUNT(*)` 与 `COUNT(DISTINCT nodeCode)` 均为 199），与计划一致。

### 5.3 校验器真实运行输出（`node scripts/check-experiment-mappings.mjs`）

```
=== check-experiment-mappings ===
registry nodes            : 199 (期望 199)
published units           : 104 (期望 104；计划 md 写 105，实测 DB 为 104)
experiment dirs on disk   : 41
experiments checked       : 41 / 41
tier                      : A=34 B=5 C=2
nodeMappings total        : 123
unitMappings total        : 77
distinct nodeCodes used   : 32
distinct unitCodes used   : 18
duplicate (qid,nodeCode)  : 0
duplicate (qid,unitCode)  : 0

WARN (21):
  - 1930155119123681282: Tier A 尚无 judge-cases.json（harness 可能由其他 agent 并行交付中）
  ...（共 21 条，全部是第三章 7 个 + 第四章 14 个 Tier A id）

MAPPING CHECK OK: 0 errors
MAPPING_CHECK_EXIT=0
```

完整日志：`artifacts/experiment-judge/ch1/check-mappings.log`。
**21 条 WARN 不是缺陷**：它们是 Agent A 正在并行交付的第三/四章 Tier A harness，当时还没有 `judge-cases.json`。
校验器把「Tier A/C 缺 judge-cases.json」记为 WARN 而非 ERROR，因为映射校验不该被别人的进度阻塞；
主 agent 在 Task 8 落库前应重跑一次，届时 WARN 应归零（本轮最后一次重跑已降到 20 条，说明 Agent A 在推进）。

### 5.4 校验器负测试（证明闸门真的会拦，不是摆设）

对 `1908539875976839170`（2.5）与 `1908540698916061185`（2.8，Tier B）做 9 项变异，每项**改完立即跑校验器再还原**。
完整记录：`artifacts/experiment-judge/ch1/check-mappings-negative.log`。

| 变异 | 退出码 | 校验器报出的错误（逐字） |
| --- | --- | --- |
| nodeName 改错 | **1** | `nodeName 与 registry 不等 —— os.memory.page_table: 文件="页表（改错）" registry="页表"` |
| nodeCode 不存在 | **1** | `nodeCode 不在 registry：os.memory.nonexistent` |
| 两个 PRIMARY | **1** | `PRIMARY 数 2 != 1` + `PRIMARY os.memory.multilevel_page_table weight != 1.0` |
| 重复 nodeCode | **1** | `PRIMARY 数 2 != 1` + `重复 (questionId,nodeCode)：1908539875976839170\|os.memory.page_table` |
| reason 写空话 | **1** | `os.memory.page_table reason 为空或过短（<20 字符）`（「按主题映射」6 字，先被长度闸门拦下） |
| unitCode 不存在 | **1** | `unitCode 不在已发布清单：unit.memory.nonexistent` |
| 重复 unitCode | **1** | `重复 (questionId,unitCode)：1908539875976839170\|unit.memory.paging-basics` |
| required=true | **1** | `unit.memory.paging-basics required=true，必须一律 false` |
| Tier B 用 PASS | **1** | `1908540698916061185: Tier B 应为 completionRule=SUBMIT，实为 PASS` |

9/9 全部 exit 1；全部还原后重跑 **exit 0 / 0 errors**（已确认交付文件与变异前逐字一致）。

校验器另外还检查（本轮均通过）：目录集合恰好等于 41 个真实 id、7 个垃圾/软删除 id 不得出现、
`questionId` 必须是字符串且等于目录名、RELATED 数 1..3、weight ∈ (0,1]、
Tier B 必须有 `judge-mode.json(LOCAL_BUILD)` 且**不得**有 `judge-cases.json`、`cases.json` 必须为空数组。

### 5.5 映射原则（reason 全部指向真实目标函数）

每条 `reason` 都点名该实验在 `question.content` 里写明的**真实目标函数或源文件**，例如：

- 2.5 → `boot_pgdir_walk()/pgdir_walk()` 的 `PDX(va)`/`PTX(va)` 与 create 时 `page_alloc` + `pp_ref++`；
- 3.5 → `env_alloc()` 设置 `cpo_status` 的 KUc/IEc/KUp 位（对应 `os.runtime.mode_switch`）；
- 4.10 → `duppage()` 对只读页 / `PTE_LIBRARY` 共享页 / `PTE_COW` 页的三种分类处理；
- 4.14 → `sys_set_env_status()` 校验状态后更新 `env_status`，RUNNABLE 则插入调度链表。

**没有一条写「按主题映射」这类空话**；生成器与校验器都对此做了断言。

### 5.6 nodeProposals（确认缺口，**未自行新增任何节点**，等用户确认）

199 个节点来自王道考研知识图谱，粒度偏概念/算法/机制，与 MOS 内核实验的工程实体有 4 处缺口。
本轮**没有**向 `knowledge_node_registry` 插入任何行，只用现有节点做最贴近映射：

| 建议 nodeCode | 建议 name | nodeType | 影响实验 | 目前落在哪 |
| --- | --- | --- | --- | --- |
| `os.memory.cow` | 写时复制 | MECHANISM | 4.10 / 4.13 / 4.15 | `os.memory.sharing` + `os.memory.page_fault`（COW 是二者组合，无法单独表达） |
| `os.memory.frame_free_list` | 空闲页框链表与物理页分配 | MECHANISM | 2.1 / 2.3 / 2.4 | `os.memory.paging`（偏地址变换，与物理页框链表管理粒度不齐） |
| `os.runtime.elf` | ELF 文件格式 | CONCEPT | 1.2 / 3.6 / 3.7 | `os.memory.loading_linking`（过程性 MECHANISM，缺文件格式本身的 CONCEPT） |
| `os.boot.build_system` | 内核构建与交叉编译 | MECHANISM | 1.1 | `os.memory.loading_linking` + `os.boot.boot_process`（后者讲引导时序不是构建） |

### 5.7 tierProposals

**空。** 未发现新的够格概念同构候选；Tier C 严格限定 1.1、1.3，未自行扩大。
