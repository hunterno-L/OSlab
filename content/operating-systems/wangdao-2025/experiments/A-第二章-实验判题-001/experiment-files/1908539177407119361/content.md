# 实验 2.4 内存分配与释放管理

## 实验背景

本实验要求学生在 MIPS CPU 环境中实现物理内存的分配和释放功能。学生需要完成 `page_alloc` 和 `page_free` 函数，这两个函数分别用于从空闲内存链表中分配一页物理内存和将已分配的内存页重新加入到空闲链表中。此外，学生还需要在 `mips_init` 函数中注释掉 `page_check` 函数，以确保内存管理的正确性。

## 知识要点

1. **物理内存管理**：理解物理内存的分配和释放机制。
2. **链表操作**：掌握基于链表的内存管理方法，包括链表的遍历、插入和删除操作。
3. **C 语言编程**：熟悉 C 语言的基本语法，特别是函数的定义、内存操作和链表操作。
4. **操作系统内核**：了解操作系统内核中内存管理的基本概念和实现方法。

**扩写**：`page_alloc` 从 `page_free_list` 摘第一个节点，`bzero(page2kva(pp), BY2PG)` 清零，`pp_ref` 置 0，返回 0；链表空时返回 `-E_NO_MEM`。`page_free` 要求 `pp_ref == 0`（否则 panic），把页挂回链表。`pp_ref` 是「有多少个页表项指向这一页」，由 `page_insert`/`page_remove` 增减。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `mm/pmap.c` | 本实验要补全的两个函数 | page_alloc、page_free、page_free_list、pp_ref、bzero、E_NO_MEM |
| `mm/pmap.c 的 mips_init` | 需注释掉 page_check 以便逐步验证 | page_check |
| `include/queue.h` | 链表摘取与回挂 | LIST_FIRST、LIST_REMOVE、LIST_INSERT_HEAD |

**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 为 0 字节、`answer` 仅 6 字节）。harness 用 `phys[]` 数组等价替代真实物理内存，`page2kva` 等价替代为「物理地址 → 数组下标」，是**按公开语义构造的等价环境**。

## 实验任务

**实验任务**

1. **理解物理内存管理**：
   - 理解物理内存的分配和释放过程，以及空闲链表 `page_free_list` 的作用。
   - 理解 `page_alloc` 和 `page_free` 函数在内存管理中的作用。
2. **编写 `page_alloc` 和 `page_free` 函数**：
   - 编写 `page_alloc` 函数，该函数从空闲内存链表中分配一页物理内存。
   - 编写 `page_free` 函数，该函数将已分配的内存页重新加入到空闲链表中。
3. **测试 `page_alloc` 和 `page_free` 函数**：
   - 编写测试代码，验证 `page_alloc` 和 `page_free` 函数的正确性。
   - 确保函数能够正确地分配和释放内存页，并处理各种边界情况。
4. **修改 `mips_init` 函数**：
   - 在 `mips_init` 函数中注释掉 `page_check` 函数，以确保内存管理的正确性。

**每步验证**：步骤 1–2 补全两个函数；步骤 3 自测边界（链表空时分配、`pp_ref>0` 时释放）；步骤 4 在 `mips_init` 中注释掉 `page_check`，改为逐步验证。

## 本平台判题说明

- 判题方式：**Tier A**：主机端等价 harness，判的就是真实目标函数 `page_alloc()`/`page_free()`。
- 你需要填写：`static int page_alloc(struct Page **pp)` 与 `static void page_free(struct Page *pp)`，在 `starter.c` 的「学生填写区：实现 mm/pmap.c 的 page_alloc() 与 page_free()」内。
- 判题输入：首行 `npage freemem_bytes`，次行操作数，随后每行一个操作（见下方操作码说明）。
- 判题输出：每个 `A`/`F` 各输出一行，末行额外输出空闲链表页号序列（递减序＝头插）。`S` 无输出行。
- `A`：分配一页，输出「A 页号 首字」；失败输出「A -4」（`-E_NO_MEM`，只有 2 字段）。「首字」用于验证清零：驱动每次分配后写入 `0xDEADBEEF`，故同一页被回收再分配时首字必须为 0。
- `S` k r：把第 k 次成功分配的页 `pp_ref` 设为 r。**无输出行**，只改状态，供后续 `F` 观察。
- `F` k：释放第 k 次成功分配的页，输出「F 页号 pp_ref」。`pp_ref>0` 时不得真正回收。
- 宿主环境适配：物理内存是 `static unsigned char phys[]`，`page2kva` 等价替代为下标换算；错误码 `-E_NO_MEM` 即 `-4`，与真实内核一致。
- 与真实内核的关系：摘链/回挂/清零/引用计数语义与真实内核一致；本地仍应跑官方 `page_check` 的「free 后重新 alloc，`*temp` 必须为 0」断言。
- 内容来源标注：`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 mm/pmap.c 的 page_alloc()/page_free()。stdin 首行 npage freemem_bytes，次行操作数，随后每行一个操作：A=分配一页；S k r=把第 k 次成功分配的页 pp_ref 设为 r；F k=释放第 k 次成功分配的页。输出：A 行为「A 页号 首字」（失败为「A -4」，首字用于验证清零，驱动分配后会写入 0xDEADBEEF），F 行为「F 页号 pp_ref」，末行为空闲链表页号序列。
```

## 本地完整验证

本地完整验证（选填）：恢复 `page_check()`，它会反复 alloc/free 并断言清零与 `pp_ref`；再在 gxemul 里观察长时间运行后空闲页总数是否守恒。

## 常见错误

1. 漏掉 `bzero`（`wrong.c` 的做法）：页被回收再分配时残留上一个使用者的数据，官方「`*temp` 必须为 0」断言失败。
2. 链表空时不返回 `-E_NO_MEM` 而是解引用 NULL：沙箱会判 Runtime Error 而非答案错误。
3. `page_free` 不检查 `pp_ref == 0`：共享页被提前回收，另一个使用者的映射指向已重分配的页。
4. 释放时用尾插而真实源码头插：末态空闲链表序列与参考不符。
