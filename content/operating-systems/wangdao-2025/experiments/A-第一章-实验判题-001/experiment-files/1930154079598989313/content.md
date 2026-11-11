# 实验 1.3 使用 Linker Script 控制内核加载地址

## 实验背景

在本实验中，你将学习如何使用 Linker Script 来控制内核的加载地址。通过修改 Linker Script，你将能够调整内核各段（如 `.text`、`.data` 和 `.bss`）的加载位置，从而更好地理解内核的构建过程和内存布局。

## 知识要点

1. **Linker Script 基础**：
   - 理解 Linker Script 的作用和基本语法。
   - 学习如何通过 Linker Script 控制段的加载地址。
2. **ELF 文件结构**：
   - 理解 ELF 文件的组成部分，包括 `.text`、`.data` 和 `.bss` 段。
   - 了解各段在程序执行中的作用。
3. **GCC 和 ld 工具**：
   - 学习如何使用 GCC 编译器和 ld 链接器来构建程序。
   - 理解 `-T` 选项在 GCC 中的作用，用于指定 Linker Script。
4. **内存布局**：
   - 理解程序在内存中的布局，包括代码段、数据段和未初始化数据段的位置。

**扩写（ld 公开语义）**：`. = ALIGN(n)` 把当前地址向上抬到 n 的倍数；`.text : { *(.text) }` 收集同名输入段并把 `.` 前进该段大小。`.bss` 是 `NOBITS`，不占文件体积但占内存。`_end` 必须按页对齐：`page_init` 从 `_end` 之后挂空闲页，不对齐就会把内核占用的半页分出去，内核被自己覆盖。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `tools/scse0_3.lds` | 本实验要补全的链接脚本 | ENTRY、SECTIONS、. = 0x80000000、ALIGN(4096)、_end |
| `Makefile / include.mk` | 用 $(LD) -T 指定脚本 | LD、-T tools/scse0_3.lds |
| `mm/pmap.c` | _end 的消费者 | page_init、freemem |

**内容来源缺口**：`tools/scse0_3.lds` 的**原指导 Listing 未随工作区提供**（`originalCode`/`answer` 实测均 0 字节，原文只要求「阅读该文件」）。子任务采用 ld `SECTIONS` 的通用布局规则并在题面显式给出，是**按 ld 公开语义构造的等价示例**，不是 MOS 私有 lds 的转录。

## 实验任务

**实验任务**

1. **阅读和理解 Linker Script**：
   - 阅读 `tools/scse0_3.lds` 文件，理解其内容和结构。
   - 理解各段的定义和加载地址的设置。
2. **修改 Linker Script**：
   - 根据实验要求，修改 `tools/scse0_3.lds` 文件中的空白部分。
   - 调整内核各段的加载地址，确保内核能够正确加载和运行。
3. **编译和链接内核**：
   - 使用修改后的 Linker Script 编译和链接内核。
   - 确保内核能够正确生成并加载到指定的内存地址。
4. **验证内核加载地址**：
   - 使用 `readelf` 工具查看生成的内核文件，验证各段的加载地址。
   - 确保各段的加载地址与 Linker Script 中指定的地址一致。

**每步验证**：理解脚本，补全并构建；用 `readelf` 核对段地址与 `_end`。

## 本平台判题说明

- 判题方式：**Tier C**：概念子任务自动判分，真实链接仍需本地。**平台自动判分的是 ld SECTIONS 的段加载地址计算；AC 不等于内核链接成功。** 见下方「本地完整验证」。
- 你需要填写：`uint32_t layout_sections(uint32_t base, const struct Sec *secs, int n, uint32_t vma[])`（`starter.c` 填写区）。`struct Sec { char name[16]; uint32_t size; uint32_t align; }`；每段 VMA 写进 `vma[i]`，返回按页对齐后的 `_end`。
- 判题输入：首行「起始VMA 段数n」（n 为 1..16），随后 n 行「段名 大小 对齐」（段名 ≤15 字符，大小与对齐为十进制无符号 32 位）。
- 判题输出：每段一行「段名 0x%08x」，末行「_end 0x%08x」（8 位十六进制小写、0x 前缀）。规则：每段先向上对齐到该段对齐值（**为 0 时视为不对齐**），该地址即 VMA，再前进该段大小；全部放完后向上对齐到 4096 得 `_end`。
- 宿主环境适配：地址与大小全用 `uint32_t`（不用 `long`/`size_t`，避免 LLP64/LP64 分歧）；驱动缓冲 `uint32_t vma[MAXS] = {0}` 已在声明处初始化，避免 `-Werror=maybe-uninitialized`。
- 与真实内核的关系：**判题观测量（各段 VMA 与 `_end`）就是你用 `readelf -S`/`-s` 核对的那组数**；真实 lds 还要处理 `ENTRY`、`PHDRS`、`NOBITS`、`PROVIDE` 与输入段收集顺序，子任务不覆盖。
- 内容来源标注：`tools/scse0_3.lds` 原文**未随工作区提供**，布局规则为按 ld 公开语义构造的**等价**示例，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 SECTIONS 段布局计算。输入首行「起始VMA 段数n」（n 为 1..16），随后 n 行「段名 大小 对齐」（段名 ≤15 字符，大小与对齐为十进制无符号 32 位）。从起始VMA 起逐段放置：每段先把当前地址向上对齐到该段的对齐值（对齐为 0 时视为不对齐），该地址即段 VMA，然后当前地址前进该段大小；全部段放完后把当前地址向上对齐到 4096 得到 _end。输出每段「段名 0x%08x」，末行「_end 0x%08x」（8 位十六进制小写、0x 前缀）。
```

## 本地完整验证

完整步骤见同目录 `local-build.md`。要点：① 先完成 1.1；② 补全 lds（`.text`→`.rodata`→`.data`→`.bss`，段间 `ALIGN(4096)`，`_end = .;` 前再对齐一次，别漏 `*(COMMON)`）；③ `make clean && make`；④ `readelf -S` 核对 Addr、`readelf -s | grep _end`；⑤ gxemul 启动。报 `location counter backwards` 即段顺序或对齐写错。

## 常见错误

1. 忽略段对齐值顺序累加（`wrong.c` 的做法）：`kernel-typical` 的 `.bss` 落在 `0x800023e8` 而非 `0x80003000`。
2. `_end` 不按页取整：`single-section` 会得 `0x80000001` 而非 `0x80001000`。
3. 把 align=0 当特殊值或死循环：契约明写为 0 时不对齐，`zero-align-and-small-align-on-unaligned` 专门覆盖。
