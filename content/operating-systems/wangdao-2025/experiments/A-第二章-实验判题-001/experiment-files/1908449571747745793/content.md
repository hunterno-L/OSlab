# 实验 2.1 内存控制块

## 实验背景

在 MIPS CPU 中，内存管理以 4KB 大小为单位，称为页。为了记录内存分配情况，需要使用 `Page` 结构体来记录一页内存的相关信息。本实验要求学生理解并实现链表操作宏函数，以便在内存管理中高效地进行页的插入和尾部添加操作。

MIPS 以 4KB 为单位管理内存，每一页用一个 `Page` 结构体记录。内核启动后要把成千上万个 `Page` 挂成空闲链表，之后 `page_alloc`/`page_free` 全在这条链上操作。链表宏写错一个指针，整个物理内存管理就会在某个随机时刻崩掉——所以本实验先把 `include/queue.h` 的两个宏写对。

## 知识要点

1. **MIPS CPU 内存管理**：理解内存以页为单位进行管理的概念。
2. **数据结构**：掌握链表的基本操作和应用。
3. **宏函数**：理解宏函数的定义和使用，特别是在链表操作中的应用。
4. **C 语言编程**：熟悉 C 语言的基本语法和结构体的使用。

**扩写**：`LIST_ENTRY(Page)` 展开成 `{ struct Page *le_next; struct Page **le_prev; }`。`le_prev` 是**指向前驱节点 next 字段的指针**（不是前驱节点指针），这个二级指针设计让「头插/删除」不需要特判头节点。`do{...}while(0)` 把多条语句包成一个语法单元，使宏在 `if(...) MACRO(); else ...` 里不会断裂。尾插必须从头遍历到最后一个节点，因此是 O(n)；头插是 O(1)。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `include/queue.h` | **工作区提供真实源码**（originalCode 8292 B） | LIST_ENTRY、LIST_HEAD、LIST_INSERT_AFTER、LIST_INSERT_TAIL、LIST_INSERT_HEAD、LIST_REMOVE |
| `mm/pmap.c` | 宏的使用者与官方自检 | struct Page、pp_link、pp_ref、page_free_list、page_check |

**内容来源**：本实验是全库仅有的两个有真实源码的实验之一——`originalCode` 就是 `include/queue.h`（8292 字节）、`answer` 是其完整实现（8493 字节），已导出为 `artifacts/experiment-judge/reference-queue.h` / `reference-answer-2_1.txt`。harness 直接驱动这两个宏，**不是构造的等价示例**。

## 实验任务

**实验任务**

1. **理解 `Page` 结构体**：
   - 理解 `Page` 结构体中 `pp_link` 和 `pp_ref` 的作用。
   - 掌握 `LIST_ENTRY(Page)` 的定义和用途。
2. **阅读和理解宏函数**：
   - 阅读 `include/queue.h` 中定义的宏函数，理解它们的原理和巧妙之处。
   - 特别注意宏函数的写法，如 `do { /* ... */ } while(0)`，理解其好处。
3. **实现链表操作宏函数**：
   - 实现 `LIST_INSERT_AFTER` 函数，该函数用于在链表中指定节点后插入新节点。
   - 实现 `LIST_INSERT_TAIL` 函数，该函数用于在链表尾部插入新节点。

**每步输入/输出/验证**：步骤 1 产出「能解释 pp_link 与 pp_ref 的分工」；步骤 2 产出对 `do{}while(0)` 与 `le_prev` 二级指针的书面解释；步骤 3 产出补全的两个宏，验证方式是跑官方 `page_check`（它依赖头插/尾插的确切顺序）。

## 本平台判题说明

- 判题方式：**Tier A**：主机端等价 harness，判的就是 `include/queue.h` 的真实宏。
- 你需要填写：`LIST_INSERT_AFTER(listelm, elm, field)` 与 `LIST_INSERT_TAIL(head, elm, field)` 两个宏，在 `starter.c` 的「学生填写区：实现 include/queue.h 中的两个宏」内。
- 判题输入：首行操作数 n（0..256），随后 n 行操作。元素用编号标识，合法范围 0..63（`MAXP=64`）；驱动令 `pp_ref = 编号`，故输出序列即元素编号序列。
- 判题输出：只有一行：遍历链表得到的 `pp_ref` 序列（空格分隔）。四个操作码都**没有独立输出行**，只改变链表；宏写错时序列的顺序或元素个数立刻不同。
- `T` x：把元素 x 尾插（`LIST_INSERT_TAIL`，**学生填写**）；x 已在链表中则忽略。
- `A` a b：在元素 a 之后插入 b（`LIST_INSERT_AFTER`，**学生填写**）；要求 a 在链表中且 b 不在，否则忽略。
- `H` x：把元素 x 头插（`LIST_INSERT_HEAD`，驱动提供的对照实现，不在填写区）。
- `R` x：移除元素 x（`LIST_REMOVE`，驱动提供的对照实现，不在填写区）。
- 宿主环境适配：`Page` 用固定宽度字段建模，物理内存是 `static unsigned char phys[]` 数组、物理地址即数组下标；链表指针是宿主指针，但**判题观测量是 pp_ref 序列而非指针值**，因此与 MIPS 32 位地址宽度无关。
- 与真实内核的关系：宏展开后的指针操作与真实内核逐字一致；本地仍应在 MIPS 环境跑 `page_check` 做完整验证。
- 内容来源标注：`include/queue.h` 为工作区提供的**真实源码**，非构造示例。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 include/queue.h 链表宏。操作码：T=尾插, A=在a后插b, H=头插, R=移除。输出为遍历 pp_ref 序列。
```

## 本地完整验证

本地完整验证（选填）：在 `mm/pmap.c` 的 `mips_init` 中恢复 `page_check()` 调用，`make` 后 gxemul 启动，官方自检会逐项断言链表顺序与 `pp_ref`；任何宏写错都会在这里 panic。

## 常见错误

1. 把尾插直接复用头插（`wrong.c` 的做法，学生最常见错误）：链表变成 LIFO，`page_alloc` 先分出高地址页，与真实内核行为相反。
2. `le_prev` 写成前驱节点指针而非「前驱 next 字段的地址」：删除节点时前驱的 next 不会被更新，链表断裂。
3. 插入后不维护被插节点的 `le_prev`：下一次 `LIST_REMOVE` 会写坏内存。
4. 宏体不加 `do{}while(0)`：在 `if/else` 中展开后 `else` 悬空，编译报错或语义错。
