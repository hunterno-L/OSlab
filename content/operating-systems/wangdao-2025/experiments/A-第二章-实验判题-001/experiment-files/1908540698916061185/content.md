# 实验 2.8 TLB 管理

## 实验背景

本实验要求学生理解和实现 MIPS 架构中的 TLB（Translation Lookaside Buffer）管理功能。学生需要完成 `tlb_out` 汇编函数的编写，该函数用于处理 TLB 未命中（TLB Miss）的情况，并正确地插入 TLB 项或更新 TLB。

页表建好了，但每次访存都走两级页表太慢。MIPS 用 TLB（Translation Lookaside Buffer）缓存最近的翻译结果。TLB 未命中时硬件抛异常，软件必须把正确的翻译填进去——这就是 `mm/tlb_asm.S` 里 `tlb_out` 的职责，也是实验 2.7 的 `tlb_invalidate` 真正生效的地方。

## 知识要点

1. **MIPS 架构**：理解 MIPS 架构的基本指令集和寄存器使用。
2. **TLB 概念**：掌握 TLB 的作用、结构和工作原理。
3. **汇编语言编程**：熟悉 MIPS 汇编语言的基本语法和指令。
4. **异常处理**：理解 TLB 未命中异常的处理流程。
5. **操作系统内核**：了解操作系统内核中内存管理的基本概念和实现方法。

**扩写（MIPS CP0 公开约定）**：`tlbp` 按 `EntryHi` 的 VPN/ASID 在 TLB 中查找，命中则把项号写进 `Index`，未命中则把 `Index` 最高位置 1（软件据此跳 `NOFOUND`）。`tlbwi` 把 `EntryLo0`/`EntryLo1`/`EntryHi` 写进 `Index` 指定的项。**`tlbp` 之后的 4 条 `nop` 是 CP0 冒险间隔**（读 `Index` 前必须等 `tlbp` 完成），不是分支延迟槽——这是本实验要求书面解释的重点。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `mm/tlb_asm.S` | 本实验要补全的汇编函数 | tlb_out、NOFOUND、tlbp、tlbwi、nop × 4 |
| `include/mmu.h` | TLB 相关 CP0 寄存器编号 | CP0_ENTRYHI、CP0_ENTRYLO0/1、CP0_INDEX |
| `mm/pmap.c` | tlb_invalidate 的调用方（实验 2.7） | page_insert、page_remove、tlb_invalidate |

**内容来源**：`mm/tlb_asm.S` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。`local-build.md` 与 `starter.c` 注释里的汇编骨架是**按 MIPS CP0 公开语义构造的等价示例**，不是北航原文转录；`starter.c` **不参与平台判分**。

## 实验任务

**实验任务**

1. **理解 TLB 管理**：
   - 理解 TLB 在虚拟内存管理中的作用，以及 TLB 未命中的处理流程。
   - 理解 `tlbp` 和 `tlbwi` 指令的用途和执行效果。
2. **分析 `tlb_out` 汇编函数**：
   - 分析给定的 `tlb_out` 汇编函数，理解其跳转到 `NOFOUND` 的流程。
   - 从 MIPS 手册中查找 `tlbp` 和 `tlbwi` 指令，明确其用途，并解释为何第 10 行处指令后有 4 条 `nop` 指令。
3. **编写 `tlb_out` 函数**：
   - 完成 `mm/tlb_asm.S` 中 `tlb_out` 函数的编写，正确处理 TLB 未命中的情况。
   - 在空出的两行位置填入 `tlbp` 或 `tlbwi` 指令，确保 TLB 项的正确插入或更新。

**每步输入/输出/验证**：步骤 1 产出「能说明 TLB 未命中的处理流程」；步骤 2 产出对 `tlbp`/`tlbwi` 用途与 4 条 `nop` 原因的书面解释（须区分 CP0 冒险与分支延迟槽）；步骤 3 产出补全的 `tlb_out`，验证方式是 gxemul 里触发 TLB Miss 后能正确取到映射、不再反复异常。

## 本平台判题说明

- 判题方式：**Tier B：本地构建（`judgeMode: "LOCAL_BUILD"`），平台不自动判分**，完成规则为 `SUBMIT`。原因：`tlbp`/`tlbwi` 直接操作 CP0 与真实 TLB 硬件，沙箱是 x86-64 用户态进程、无 MIPS 交叉编译器、无 gxemul、无 CP0。用 C 写一个「像那样」的程序**不构成概念同构**，只会变成被否决的代理题。
- 你需要填写：本地在 `mm/tlb_asm.S` 的 `tlb_out` 空出的两行分别填 `tlbp` 与 `tlbwi`。平台侧 `starter.c` 只是注释形式的汇编骨架与 CP0 位段速查，**明确标注不参与判分**。
- 判题输入：无。`cases.json` 为 `{"cases":[]}`，**不生成 `judge-cases.json`**，平台不会对本实验发起判题。
- 判题输出：无平台判题输出。真实产出是补全的 `tlb_asm.S`、对 4 条 `nop` 的书面解释，以及 gxemul 的运行日志。
- 宿主环境适配：不适用于沙箱。实验 2.7 的 harness 里 `tlb_invalidate` 只是非终止 no-op，正是因为它无法在主机上等价再现 CP0 行为。
- 与真实内核的关系：完整验证途径是本地 MIPS 交叉编译 + gxemul，见下方「本地完整验证」与同目录 `local-build.md`。
- 内容来源标注：`mm/tlb_asm.S` 原文**未随工作区提供**，骨架为按公开语义构造的**等价**示例，非北航原文转录。
## 本地完整验证

完整步骤见同目录 `local-build.md`。要点：① 从 MIPS 手册查 `tlbp`/`tlbwi` 的语义与 `Index` 最高位的未命中标志；② 在 `tlb_out` 的两行空白处填 `tlbp`（查）与 `tlbwi`（写），`tlbp` 后保留 4 条 `nop` 再读 `Index`；③ 未命中分支跳 `NOFOUND`；④ `make` 后 `mips-linux-gnu-objdump -d gzemul/vmlinuz` 确认指令编码与 `nop` 数量；⑤ gxemul 启动，跑一个会触发 TLB Miss 的用户进程，确认不再反复异常。

## 常见错误

1. 把 `tlbp` 与 `tlbwi` 顺序颠倒：先写后查，写进的项号是上一次遗留的 `Index`。
2. `tlbp` 后立刻读 `Index`（删掉 4 条 `nop`）：CP0 冒险，读到旧值，未命中被误判为命中。
3. 把 4 条 `nop` 解释成分支延迟槽：MIPS 分支延迟槽只有 1 条，这里是 CP0 写后读冒险间隔。
4. 不检查 `Index` 最高位就 `tlbwi`：未命中时 `Index` 是无效项号，写坏 TLB。
5. 只填 `tlbwi` 不填 `tlbp`：命中项永远查不到，每次都当未命中处理。
