# 实验 2.3 空闲内存管理

## 实验背景

本实验旨在通过编写和实现 `page_init()` 函数，学习如何在 MIPS CPU 系统中初始化物理内存，并将其加入到空闲链表中。这将帮助学生理解物理内存的分配和管理过程，以及如何在操作系统内核中实现这些功能。

知道有多少内存之后，要把「内核自己占用的」与「可以分出去的」分开。`page_init()` 遍历 `[0, npage)`，把内核映像与 `pages` 数组占用的页标为已用，其余全部挂进 `page_free_list`。这条链表是之后整个物理内存分配的唯一来源。

## 知识要点

1. **物理内存管理**：理解物理内存的基本概念，包括内存块的分配和释放。
2. **数据结构**：掌握链表的使用，特别是空闲链表在内存管理中的应用。
3. **宏函数**：理解宏函数的定义和使用，特别是在内存管理中的链表操作。
4. **C 语言编程**：熟悉 C 语言的基本语法，特别是函数的定义、变量的初始化和内存操作。

**扩写**：`freemem` 是内核映像结束（`_end`）之后的第一个可用物理地址，先 `ROUND(freemem, BY2PG)` 向上取整到页边界。`pages[i].pp_ref` 非 0 表示该页已被引用，不得进空闲链表。**真实源码用 `LIST_INSERT_HEAD`（头插）**，所以空闲链表是页号**递减**序，`page_alloc` 因此先分出高地址页（LIFO）——这个顺序被官方 `page_check` 依赖，改成尾插就错。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `mm/pmap.c` | 本实验要补全的函数 | page_init、page_free_list、freemem、pages、npage、pp_ref |
| `include/queue.h` | 链表宏（实验 2.1 已实现） | LIST_INIT、LIST_INSERT_HEAD、LIST_INSERT_TAIL |
| `tools/scse0_3.lds` | 提供 _end，决定 freemem 起点 | _end（实验 1.3） |

**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 为 0 字节、`answer` 仅 6 字节）。harness 的物理内存用 `static unsigned char phys[]` 建模、物理地址即下标，`page_init` 的头插语义按公开 MOS 源码语义构造，是**等价环境**。

## 实验任务

**实验任务**

1. **理解物理内存管理**：
   - 理解物理内存的分配和管理过程，以及如何区分已分配和未分配的内存块。
   - 理解内核可用的物理内存上限的重要性。
2. **编写 `page_init()` 函数**：
   - 根据实验要求，编写 `page_init()` 函数，该函数负责将未分配的物理内存页加入到空闲链表 `page_free_list` 中。
   - 使用 `include/queue.h` 中定义的宏函数来简化链表操作。

**每步输入/输出/验证**：步骤 1 产出「能说明已分配/未分配页如何区分、为什么内核占用上限重要」；步骤 2 产出补全的 `page_init()`，输入是 `npage` 与 `freemem`、输出是空闲链表与各类计数，验证方式是官方 `page_check` 的空闲页总数断言。

## 本平台判题说明

- 判题方式：**Tier A**：主机端等价 harness，判的就是真实目标函数 `page_init()`。
- 你需要填写：`static void page_init(void)`，在 `starter.c` 的「学生填写区：实现 mm/pmap.c 的 page_init()」内。用 `include/queue.h` 的宏把可用页挂进 `page_free_list`。
- 判题输入：首行 `npage freemem_bytes`（页框总数与内核映像结束后的第一个可用物理地址，十进制）。
- 判题输出：第一行为空闲链表页号序列（**真实源码头插，故为递减序**），第二行为 `freemem used_count free_count other_count`。
- 宿主环境适配：物理内存是 `static unsigned char phys[NPAGE_MAX*BY2PG]`，物理地址 = 数组下标；`Page` 元数据用固定宽度字段；所有地址/页号用 `uint32_t`，避免宿主 `long` 宽度差异。
- 与真实内核的关系：头插顺序、`pp_ref` 判定与 `freemem` 取整语义与真实内核一致；本地仍应跑官方 `page_check` 完整验证。
- 内容来源标注：`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 mm/pmap.c 的 page_init()。stdin 首行 npage freemem_bytes。输出第一行为空闲链表页号序列（真实源码头插，故为递减序），第二行为 freemem used_count free_count other_count。
```

## 本地完整验证

本地完整验证（选填）：恢复 `mips_init` 中的 `page_check()`，gxemul 启动后它会断言空闲页总数等于 `npage - 内核占用页数`，并逐项检查链表顺序。

## 常见错误

1. 用 `LIST_INSERT_TAIL` 代替 `LIST_INSERT_HEAD`（`wrong.c` 的做法）：空闲链表变成递增序，`page_alloc` 先分低地址页，与真实内核 LIFO 相反。
2. 忘记 `ROUND(freemem, BY2PG)`：内核占用的半页被当成整页分出去，内核被自己覆盖。
3. 把 `pp_ref != 0` 的页也挂进空闲链表：同一页被两个使用者同时持有。
4. 遍历上界写成 `freemem/BY2PG` 而不是 `npage`：高地址内存全部丢失。
