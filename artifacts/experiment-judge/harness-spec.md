# 编程实验 harness 规范（所有 Tier A / Tier C 实验必须遵守）

本规范由 Task 1 在实验 2.1 上实测打通后固化。后续 40 个实验一律照此编写，
每个都必须通过 `node scripts/verify-experiment-harness.mjs <实验目录>` 才算完成。

## 1. 沙箱契约（来自 `代码沙箱/server.py`、`runner.py` 源码，不可协商）

- 语言仅 `java`/`c`/`cpp`/`go`。C 走 `gcc -O2 -pipe main.c -o main` → `./main`。
- **单文件**。无多文件、无自定义头文件、无外部库（仅 musl libc）、无 MIPS 交叉编译器、无 gxemul、无网络（`--network none`）。
- 源码 ≤ 65536 字节（`MAX_SOURCE_BYTES`）。
- `inputList` 非空、≤ 20 条（`MAX_CASES`），每条 ≤ 8192 字节（`MAX_INPUT_BYTES`）。
- **`runner.py:91` 把输入同时作为 stdin 和 argv**：`run_command_base + item.split()`。
  → harness **必须忽略 argv**；输入按空白分词后不得产生歧义（ token 内不能有空格）。
- `timeLimit` 夹在 50–5000 ms，`memoryLimit` 夹在 32–512 MB。编译窗口固定 5 s。
  单请求总窗口 `min(30, 5 + timeLimit*cases/1000)` 秒。
- **进程非零退出 → `Runtime Error`，不是 `Wrong Answer`**。
- 单条 stdout+stderr ≤ 65536 字节。
- 沙箱返回 `memory` 恒为 0，内存超限实际不会触发。

## 2. 判题比较方式（`DefaultJudgeStrategy`，只读不改）

`JudgeManager` 对所有语言强制 `DefaultJudgeStrategy`。比较流程：
先校验 `outputList.size() == inputList.size()`，再逐条 `normalizeOutput` 后**逐字符相等**，最后查 time/memory。

`normalizeOutput`：CRLF/CR → LF；去掉换行前的 trailing 空格/Tab；去掉末尾换行。
→ **行内空格与中间空行敏感**。验证脚本内的 `normalize()` 与之逐字一致。

## 3. 三段结构

```
[1] 最小内核环境：MOS 真实 struct/宏定义，地址宽度改用 uint32_t
[2] 学生填写区：目标函数/宏，保留北航原始签名与语义，标注 /* finish your code here */
[3] 检查驱动 main()：从 stdin 读场景 → 调用学生实现 → 打印确定性结果 → return 0
```

填写区必须用成对标记包裹，供 `scripts/derive-harness-variants.mjs` 精确替换：

```c
/* ===== 学生填写区：<说明> ===== */
...
/* ===== 填写区结束 ===== */
```

每个实验目录交付：
`starter.c`、`fill-reference.c`、`fill-wrong.c`、`cases.json`、`judge-config.json`，
由脚本派生 `reference.c`/`wrong.c`，再由验证脚本生成 `judge-cases.json`。

**环境段复用（Task 2 起）**：同章多个实验共享内核环境，禁止手抄多份。
环境拆成片段放在 `artifacts/experiment-judge/ch<N>/env-*.c.inc`，
用 `node scripts/assemble-harness.mjs <out.c> <fragment...>` 拼装成单文件 `starter.c`。
**考查目标所在的片段必须排除**（例如 2.4 考 `page_alloc`/`page_free`，就不能内联 `env-alloc.c.inc`）。
学生填写区与驱动放在 `body-<实验id>.c`，作为最后一个片段。

## 4. stdin 场景语法约定

- 首行一个整数 `n` 表示操作数（或场景参数），随后 `n` 行每行一个**单字符操作码**与参数。
- 操作码与参数全部以空白分隔，token 内不含空格（因为输入同时是 argv）。
- 场景必须由 stdin 给出、结果由学生实现算出 —— **只有一条空输入用例时，学生直接 printf 期望文本即可 AC**。

## 5. 输出约定

- 单行空格分隔的十进制整数序列；或明确的逐项行 + 末行汇总。
- 地址一律以 `uint32_t` 承载，用 `%u`（十进制）或 `0x%08x` 配 `(unsigned)` 转换；
  **禁止 `%zu`/`%ld`/`%p` 打印指针宽度值**。同一实验内格式必须统一（第二章统一用十进制 `%u`）。
- 输出必须非空（验证脚本会拒绝空输出）。
- **同一实验各条用例的参考输出必须互不相同**，否则判为冗余用例。
  若两个场景天然同输出（如两种环都只打印 `CYCLE`），必须让驱动暴露可区分的观测量
  （如已构建的部分进度），**而不是放宽闸门**。

### 5.1 用例必须真的覆盖它声称的边界（Task 2 实测教训）

验证脚本首轮 6/6 通过，但逐条复核发现 4 处用例名不副实。规则：

- **负向用例必须前置有效状态**。纯「什么都不该发生」的用例（非法 size、空链表、未映射地址）
  对空实现天然通过，区分度为零。必须**先建立有效映射/分配，再触发守卫**，
  这样「守卫误伤已有状态」也能被检出。
- **十六进制地址逐个验算**。实测把 `0x400FFF` 当成第 2 个 4MB 区的末页，
  实际 `0x400FFF>>12 = 0x400`，`& 0x3FF = 0` → PTX 是 0 不是 1023。
  正确末页是 `0x7FFFFF`。跨页目录项要用 `0x3FF000` 起、而非 `0x400000` 起。
- **观测量必须能区分「正确的槽位」与「另一张页表的同号槽位」**。
  纯逻辑下标 `PDX*1024+PTX` 驱动自己就能算出，不构成判据；
  应输出页表物理地址 + PTX（物理地址由环境侧确定的分配器给出）。
- `verify-experiment-harness.mjs` 会 NOTE 列出「starter 与 wrong 都通过」的纯边界用例。
  NOTE 不是失败（合法边界确实存在），但**必须逐条确认它是固有边界而非设计疏漏**。

## 6. 驱动必须对学生的任意错误行为保持不崩溃（实测教训）

这是 Task 1 踩到的真实缺陷：2.1 初版驱动对未链入的元素直接调 `LIST_REMOVE`，
而 `LIST_REMOVE` 会解引用 `le_prev`；空实现下 `le_prev` 为 NULL → 访问违例（0xC0000005）。
沙箱把非零退出判为 `Runtime Error`，学生就看不到「答案错误」，判题失去诊断价值。

**规则：**
- 驱动侧维护成员判定（如 `in_list()`/`in_free_list()`），只对「确实已链入」的元素做移除/后插。
- 一切链表步行加**步数上限**（如 `MAXP`/`NPAGE_MAX`），避免错误实现造成环时死循环。
- **学生返回的每个指针都必须先过范围校验**（`page_in_range()`/`pte_in_range()`）再解引用；
  校验失败输出确定的哨兵值（如 `-1`/`-2`），不得直接使用。
- **环境侧的索引换算函数要钳位**（如 `pa2page()` 把越界物理地址钳到最后一页），
  使学生写坏页表项时判「答案错误」而非段错误。
- **驱动自有的观测量计算不得依赖学生实现**（如 `va2pa_safe()` 自己走页目录并校验页表地址范围），
  否则学生的错误会污染判据本身。
- 驱动自有的输出缓冲必须在声明处初始化（`uint32_t vma[MAXS] = {0};`），
  否则 gcc 无法证明学生函数会写满缓冲，`-Werror=maybe-uninitialized` 直接拒绝编译。
- 输入在驱动边界严格校验（范围、操作码、参数个数），越界即 `return 0`，不进入学生代码。
- 禁止裸 `assert()` 判分（非零退出 → Runtime Error）。自行判定、打印结果、`return 0`。
- `panic()` 在主机环境必须是**不终止进程**的空操作，`tlb_invalidate()` 同理（主机无 TLB）。

**机械验证（Task 2 起强制）**：

```bash
node scripts/probe-harness-robustness.mjs <实验目录> [更多目录 ...]
```

对每个实验的 3 个变体各喂 16 条畸形输入，要求**全部 exit 0**：
空输入、全零、`4294967295`、负数、操作数溢出、未知操作码、截断操作、截断首行、非数字垃圾、
越界页号、越界 va、负 `pp_ref`、双重 free、free 未分配页、perm 溢出、size 溢出。
第二章 7 个实验实测 **336 runs / 0 failures**。

## 7. 跨平台可移植性（本地 MinGW gcc 8.1 ≠ 沙箱 Alpine gcc）

- MinGW-w64 是 LLP64（`long` 32 位），Linux 是 LP64（`long` 64 位）。
  **一切地址/页号/寄存器值一律用 `uint32_t`/`int32_t`/`uintptr_t`，禁止用 `long`/`unsigned long` 承载地址。**
- 严格 C99：所有函数先声明后使用，无隐式声明。
  学生填写区内若存在跨函数调用（如 `page_insert` 调 `page_remove`），
  环境段必须提供等价于 `include/pmap.h` 的前置声明。
- **环境段所有 static 函数与变量必须带 `__attribute__((unused))`**（约定宏 `MOS_UNUSED`）。
  共享环境片段会被多个实验内联，某实验不用其中几个时，
  `-Werror=unused-function` / `-Werror=unused-variable` 会直接拒绝编译。
- **物理内存模型**：用 `static unsigned char phys[NPAGE_MAX * BY2PG]` 模拟，
  **物理地址即数组下标**，于是 `KADDR(pa)=phys+pa`、`PADDR(kva)=kva-phys`，
  与真实内核线性映射语义相同，无需真实指针算术。1 MB BSS 是 demand-zero，实测不构成内存压力。
  从 `phys` 读写多字节值用 `memcpy` 而非指针强转，避免对齐 UB。
- 不依赖 `assert.h` 的中止行为、不依赖未初始化的栈值、不依赖 `int` 溢出回绕以外的未定义行为。
- 本地验证编译命令：`gcc -std=c99 -Wall -Wextra -Werror -O2 -pipe main.c -o main`。
  **比沙箱更严，本地过则沙箱过。**

## 8. 期望输出的唯一来源

`judge-cases.json` **只能由 `verify-experiment-harness.mjs` 从参考解真实运行导出**。
禁止手写、猜测、从分支拷贝。验证脚本同时强制：
- 三方（starter/reference/wrong）均编译零警告；
- 参考输出互异且非空；
- `starter` 与 `wrong` 各自至少在一条用例上与参考不同（证明缺陷可被检出）。

## 9. 内容来源诚实性

工作区 `answer` 字段**不一定正确**。实测 2.1 的 `LIST_INSERT_TAIL` 有两处缺陷
（`le_prev` 误指向 elm 自己的 `le_next`；空链表分支未置 `le_next = NULL`），
在「移除后再尾插」用例上输出 `1 2 3 4 5` 而非正确的 `2 3 4 5`。

**规则：** 参考解必须自行验证正确性，不得因为「来自工作区 answer」就当作真值。
凡修正工作区原实现，必须在 `fill-reference.c` 注释与 `verification.md` 中写明修正点与理由。

凡工作区缺失原文（如 `include.mk`、`tools/scse0_3.lds`）而按公开语义构造的示例，
必须标注「原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例」，不得冒充北航原文转录。

## 10. 命令

```bash
# 拼装单文件 starter.c（环境片段 + 学生填写区/驱动 body）
node scripts/assemble-harness.mjs <实验目录>/starter.c <片段1> [片段2 ...]

# 派生 reference.c / wrong.c
node scripts/derive-harness-variants.mjs <实验目录>

# 验证并生成 judge-cases.json（退出码 0=通过，1=验证失败，2=用法/文件错误）
node scripts/verify-experiment-harness.mjs <实验目录>

# 崩溃安全性探测（退出码 0=全部 exit 0，1=有崩溃/非零退出）
node scripts/probe-harness-robustness.mjs <实验目录> [更多目录 ...]
```

**完成判据**：`verify` 退出码 0 **且** `probe` 退出码 0 **且** 已逐条复核用例覆盖度（§5.1）。
三者缺一不得声称该实验 harness 完成。