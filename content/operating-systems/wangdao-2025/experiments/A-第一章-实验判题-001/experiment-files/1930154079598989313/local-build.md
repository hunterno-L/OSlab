# 实验 1.3 本地完整验证（使用 Linker Script 控制内核加载地址）

> **平台自动判分的只是「ld SECTIONS 段加载地址计算」这一个概念子任务。**
> **AC 不等于内核链接成功。** 真实链接与启动验证必须在本地 MIPS 交叉编译环境完成，步骤如下。
> 子任务与真实任务的关系：判题观测量（各段 VMA 与 `_end`）**就是**你用 `readelf` 核对的那个量；
> 但它**不能**替代真实 lds 语法、段属性（`NOBITS`/`ALLOC`）、符号定义、交叉链接与 gxemul 启动验证。

## 0. 内容来源缺口（如实记录）

工作区**没有** `tools/scse0_3.lds` 原文：
实测 `question.id=1930154079598989313` 的 `originalCode` 与 `answer` **均为 0 字节**，
`content` 中只有「阅读 `tools/scse0_3.lds` 文件」的**要求**，没有文件正文。

因此：
- 平台判分子任务采用的是 **ld `SECTIONS` 的通用布局规则**（逐段 `ALIGN` 向上对齐后顺序放置，
  末尾 `_end` 按页对齐），规则在题面中显式给出，**不是 MOS 私有 lds 的转录**；
- 真实 `scse0_3.lds` 可能还包含 `ENTRY()`、`PHDRS`、`NOBITS` 段属性、`PROVIDE()` 符号、
  按页对齐的 `.bss` 与栈区预留等，这些**不在子任务范围内**，必须按下文在本地完成。

## 1. 前置条件

- 已完成实验 1.1（`include.mk` 的 `CROSS_COMPILE`/`LD` 已正确，`make` 能跑通到链接阶段）。
- MIPS 交叉工具链（含 `mips-linux-gnu-ld`、`mips-linux-gnu-readelf`、`mips-linux-gnu-objdump`）。
- MOS 源码树含 `tools/scse0_3.lds`。

## 2. 阅读和理解 Linker Script（实验任务 1）

打开 `tools/scse0_3.lds`，逐块弄清：

| 结构 | 作用 |
| --- | --- |
| `ENTRY(symbol)` | 指定 ELF header 的 `e_entry`，gxemul 从这里开始执行 |
| `SECTIONS { ... }` | 输出段布局：location counter `.` 决定每段放在哪 |
| `. = 0x80000000;` | 把 location counter 设到 kseg0 起始（MIPS 内核不经 TLB 的固定映射区） |
| `.text : { *(.text) }` | 收集所有输入文件的 `.text` |
| `. = ALIGN(4096);` | 把 location counter 向上对齐到页边界 |
| `.data : { *(.data) }` | 已初始化数据 |
| `.bss : { *(.bss) *(COMMON) }` | 未初始化数据，ELF 里是 `NOBITS`（不占文件体积） |
| `_end = .;` / `PROVIDE(_end = .);` | 定义内核结束符号，`page_init` 用它算可用物理内存起点 |

**为什么必须是 `0x80000000` 一带：** MIPS 的 `kseg0`（`0x80000000..0x9FFFFFFF`）
是**不经 TLB 的固定映射**，物理地址 = 虚拟地址 − `0x80000000`。
内核启动早期页表还没建好，只能跑在 kseg0；把内核链接到别处会一启动就异常。

## 3. 修改 Linker Script（实验任务 2，核心交付物）

按实验要求填补 `tools/scse0_3.lds` 中的空白部分。通用骨架
（**按 ld 公开语义构造的等价示例，非北航原文转录**）：

```ld
ENTRY(_start)

SECTIONS
{
    . = 0x80000000;

    .text : {
        *(.text.boot)      /* 启动代码必须最先，落在 0x80000000 */
        *(.text)
    }

    . = ALIGN(4096);
    .rodata : { *(.rodata) *(.rodata.*) }

    . = ALIGN(4096);
    .data : { *(.data) }

    . = ALIGN(4096);
    .bss : {
        _bss_start = .;
        *(.bss) *(COMMON)
        _bss_end = .;
    }

    . = ALIGN(4096);
    _end = .;
}
```

修改时的关键点：
1. **顺序不能乱**：`.text` → `.rodata` → `.data` → `.bss`。`.bss` 放最后，
   因为它是 `NOBITS`，其后不再有需要从文件加载的内容；
2. **每段之间 `ALIGN(4096)`**：内核按页管理内存，段边界不对齐会让「代码页可执行、数据页可写」
   的权限设置互相污染（同一页里既有代码又有数据）；
3. **`_end` 必须按页对齐**：`page_init` 从 `_end` 之后开始把物理页挂进空闲链表，
   不对齐会把内核自身占用的半页当成空闲页分配出去 → 内核被自己覆盖；
4. **不要漏 `*(COMMON)`**：漏了会让未初始化全局变量落在段外，`_end` 算小；
5. **异常入口 `0x80000080`**（实验 3.12）需要在本脚本里额外定位，本实验阶段可先不涉及。

## 4. 编译和链接内核（实验任务 3）

```bash
make clean
make            # 顶层 Makefile 会用 $(LD) -T tools/scse0_3.lds 链接
```

若只想单独验证链接脚本：
```bash
mips-linux-gnu-ld -T tools/scse0_3.lds -o /tmp/kernel.elf <所有 .o>
```

常见报错：
- `location counter backwards` → `. = 0x...` 设到了当前地址之前，段顺序或对齐写错；
- `section .bss will not fit in region` → 定义了 `MEMORY` 区域但段超界；
- `undefined symbol '_end'` → 脚本里没定义该符号，而 C 代码引用了它。

## 5. 用 `readelf` 验证各段加载地址（实验任务 4，核心验证）

```bash
mips-linux-gnu-readelf -h gzemul/vmlinuz    # Entry point address
mips-linux-gnu-readelf -S gzemul/vmlinuz    # 各 Section 的 Addr / Off / Size / Align
mips-linux-gnu-readelf -l gzemul/vmlinuz    # 各 Segment 的 VirtAddr / PhysAddr / FileSiz / MemSiz
mips-linux-gnu-readelf -s gzemul/vmlinuz | grep -E '_end|_bss_start|_bss_end|_start'
```

逐项核对：
1. `.text` 的 `Addr` = `0x80000000`（或你在脚本里设的基址）；
2. `.rodata` / `.data` / `.bss` 的 `Addr` 都**按 4096 对齐**，且依次递增；
3. `.bss` 的 `Type` 是 `NOBITS`，`readelf -l` 里对应 segment 的 `MemSiz > FileSiz`
   （差值就是 `.bss` 大小，加载时清零）；
4. `_end` 符号值 = `.bss` 末尾向上对齐到 4096；
5. `e_entry` 等于启动代码第一条指令的地址。

**这一步核对的量，就是平台子任务的判题观测量。** 子任务输入「起始 VMA + 每段大小/对齐」，
输出每段 VMA 与 `_end`；你在本地用 `readelf -S` 读到的是同一组数。
差别在于：真实 lds 还要处理段属性、符号、`PHDRS`、输入段收集顺序，这些子任务不覆盖。

## 6. 启动验证

```bash
gxemul -E oldtestmips -d gzemul/vmlinuz    # 具体命令以实验指导为准
```

- 有内核串口输出 → 加载地址正确；
- 立刻异常/复位 → 优先怀疑 `.text` 基址不在 kseg0、启动代码没排在最前、`_end` 不对齐导致
  `page_init` 把内核自身当空闲页分配出去。

## 7. 提交物

- 修改后的 `tools/scse0_3.lds`；
- `readelf -h` / `-S` / `-l` / `-s` 的输出，以及逐项核对结论；
- gxemul 启动记录。

平台侧子任务按 `PASS` 记完成；
**真实链接成功与否由上述本地证据判定，不由平台 AC 判定。**