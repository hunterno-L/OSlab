# 实验 2.7 页表项插入与移除

## 实验背景

本实验要求学生在 MIPS CPU 环境中实现页表项的插入和移除功能。通过编写 `page_insert` 函数，学生将学习如何在页表中插入新的页表项，以及如何更新 TLB（Translation Lookaside Buffer）以确保内存访问的正确性。

批量映射之后是单页的增删。`page_insert` 把一页物理内存映射到某个虚拟地址，`page_remove` 解除映射。这两个函数必须与 `pp_ref` 和 TLB 保持一致：引用计数错了会内存泄漏，TLB 不刷新会访问到已经换出的物理页。

## 知识要点

1. **页表管理**：理解页表的结构和作用，以及如何在页表中插入和移除页表项。
2. **TLB 更新**：掌握 TLB 的作用和如何通过 `tlb_invalidate` 函数更新 TLB。
3. **C 语言编程**：熟悉 C 语言的基本语法，特别是函数的定义、内存操作和条件判断。
4. **操作系统内核**：了解操作系统内核中内存管理的基本概念和实现方法。

**扩写**：`page_insert` 先 `pgdir_walk(..., create=1)` 取 PTE；若该 va 已映射，**先判断旧页是否就是 pp**——是则直接返回 0（`pp_ref` 不重复递增），否则 `page_remove` 旧页；然后写 `page2pa(pp) | perm | PTE_V`、`pp_ref++`、`tlb_invalidate`。`page_remove` 清 PTE、`pp_ref--`、`tlb_invalidate`；只有 `pp_ref` 归零页才真正可回收。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `mm/pmap.c` | 本实验要补全的两个函数 | page_insert、page_remove、pgdir_walk、pa2page、page2pa、pp_ref、tlb_invalidate |
| `mm/tlb_asm.S` | TLB 失效的真实实现（实验 2.8） | tlb_out、tlbp、tlbwi |
| `include/mmu.h` | 页表项标志 | PTE_V、perm |

**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。harness 里 `tlb_invalidate` 是主机端非终止 no-op（沙箱无 CP0/TLB），其余 `pp_ref` 与页表项语义按公开 MOS 语义构造，是**等价环境**。

## 实验任务

**实验任务**

1. **理解页表和 TLB**：
   - 理解页表在虚拟内存管理中的作用，以及 TLB 如何加速虚拟地址到物理地址的转换。
   - 理解页表项插入和移除对 TLB 的影响。
2. **编写 `page_insert` 函数**：
   - 编写 `page_insert` 函数，实现在页表中插入新的页表项的功能。
   - 确保函数能够正确地检查页表项是否已经存在，并在必要时移除旧的页表项。
3. **更新 TLB**：
   - 在 `page_insert` 函数中，使用 `tlb_invalidate` 函数更新 TLB，确保内存访问的正确性。

**每步验证**：步骤 1 能解释页表项增删对 TLB 的影响；步骤 2 补全 `page_insert`（含「已映射的就是 pp」提前返回分支）；步骤 3 确认 `tlb_invalidate` 的调用位置，用官方 `page_check` 的重复 insert 断言验证。

## 本平台判题说明

- 判题方式：**Tier A**：主机端等价 harness，判的就是真实目标函数 `page_insert()`/`page_remove()`。
- 你需要填写：`static int page_insert(Pde *pgdir, struct Page *pp, uint32_t va, uint32_t perm)` 与 `static void page_remove(Pde *pgdir, uint32_t va)`，在 `starter.c` 的「学生填写区」内。
- 判题输入：首行 `npage freemem_bytes`，次行操作数，随后每行一个操作（见下方操作码说明）。
- 判题输出：每个操作各输出一行；无映射时物理地址打印 `4294967295`（~0）。本实验**不输出**末态空闲链表。
- `I` pageidx va perm：插入映射，输出「I 返回码 物理地址 pp_ref」；页表分配失败时返回码为 `-4`。
- `R` va：解除映射，输出「R 原页号 解除后物理地址 解除后 pp_ref」；原本未映射时原页号为 `-1`。
- `Q` va：查询 va 对应物理地址，输出「Q 物理地址」。
- `P` pageidx：查询该页引用计数，输出「P 页号 pp_ref」。
- 宿主环境适配：物理地址用 `uint32_t`，`~0` 打印十进制 `4294967295`；`tlb_invalidate`/`panic` 是主机端非终止 no-op（沙箱无 CP0），因此判题观测量落在页表项与 `pp_ref` 上，而非真实 TLB 状态。
- 与真实内核的关系：`pp_ref` 与页表项的维护逻辑与真实内核逐字一致；TLB 失效的真实效果必须在本地 MIPS 环境（实验 2.8）验证。
- 内容来源标注：`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 mm/pmap.c 的 page_insert()/page_remove()。stdin 首行 npage freemem_bytes，次行操作数，随后每行：I pageidx va perm=插入映射；R va=解除映射；Q va=查询 va 对应物理地址；P pageidx=查询 pp_ref。输出：I 行为「I 返回码 物理地址 pp_ref」，R 行为「R 原页号 解除后物理地址 解除后 pp_ref」（原本未映射时页号为 -1），Q 行为「Q 物理地址」，P 行为「P 页号 pp_ref」；无映射时物理地址打印 4294967295（~0）。
```

## 本地完整验证

本地完整验证（选填）：恢复 `page_check()`，它包含「同一页重复 insert 同一 va，`pp_ref` 不得重复递增」与「remove 后 `pp_ref` 归零、页回到空闲链表」两条断言；再在 gxemul 里确认无 TLB 异常。

## 常见错误

1. 漏掉「已映射的就是 pp」提前返回分支（`wrong.c` 的做法）：重复 insert 使 `pp_ref` 重复递增，之后 remove 一次减不到 0，页永远回不到空闲链表。
2. 插入前不 `page_remove` 旧页：旧页 `pp_ref` 不减，泄漏。
3. 写 PTE 时漏 `PTE_V`：映射看起来存在但 MMU 认为无效。
4. 增删后不调 `tlb_invalidate`：快表里仍是旧翻译，访问到已换出的物理页（本地才会暴露）。
5. `page_remove` 对未映射的 va 也减 `pp_ref`：计数下溢成巨大正数。
