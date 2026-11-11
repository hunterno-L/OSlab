# 实验 2.5 页表创建

## 实验背景

本实验要求学生实现 MIPS CPU 中虚拟地址到物理地址的转换功能，并创建页表。学生需要完成 `boot_pgdir_walk` 和 `pgdir_walk` 函数，这两个函数分别用于内核启动时和用户程序内存申请时的地址转换和页表创建。

物理页有了，接下来要把虚拟地址翻译成物理地址。MIPS 用两级页表：页目录（PDE）指向一张页表，页表项（PTE）指向一页物理内存。`pgdir_walk` 就是沿这两级走下去、必要时新建一张页表的函数——它是 `page_insert`、`boot_map_segment`、`load_icode_mapper` 的共同底座。

## 知识要点

1. **虚拟内存管理**：理解虚拟内存的概念及其在操作系统中的作用。
2. **页表**：掌握页表的结构和作用，以及如何通过页表实现虚拟地址到物理地址的转换。
3. **C 语言编程**：熟悉 C 语言的基本语法，特别是函数的定义、内存操作和数据结构。
4. **操作系统内核**：了解操作系统内核中内存管理的基本概念和实现方法。

**扩写**：`PDX(va)` 取 va 的高 10 位作页目录下标，`PTX(va)` 取中间 10 位作页表下标，低 12 位是页内偏移。`boot_pgdir_walk` 用于启动期（此时 `page_alloc` 还不可用，改用 `alloc`），`pgdir_walk` 用于运行期。`create == 1` 且页目录项无效时要新建一页作页表、清零、置 `PTE_V`、并令新页表的 `pp_ref++`；**忘记置 `PTE_V` 是最典型的缺陷**。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `mm/pmap.c` | 本实验要补全的两个函数 | boot_pgdir_walk、pgdir_walk、PDX、PTX、PTE_ADDR、PTE_V、KADDR |
| `include/mmu.h` | 两级索引与页表项标志 | PDX、PTX、PTE_V、PTE_ADDR、BY2PG |
| `mm/pmap.c 的 page_alloc` | 运行期新建页表的页来源 | page_alloc、pp_ref |

**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 为 0 字节、`answer` 仅 6 字节）。harness 的两级页表、`PDX`/`PTX` 位段与 `PTE_V` 语义按公开 MOS/ MIPS 语义构造，是**等价环境**。

## 实验任务

**实验任务**

1. **理解虚拟内存管理**：
   - 理解虚拟内存的基本概念，包括虚拟地址、物理地址和页表。
   - 理解页表在虚拟内存管理中的作用。
2. **编写 `boot_pgdir_walk` 和 `pgdir_walk` 函数**：
   - 编写 `boot_pgdir_walk` 函数，用于内核启动时的地址转换和页表创建。
   - 编写 `pgdir_walk` 函数，用于用户程序内存申请时的地址转换和页表创建。

**每步输入/输出/验证**：步骤 1 产出「能画出 va → PDX → PDE → PTX → PTE → pa 的翻译链」；步骤 2 产出补全的两个函数，输入是 (va, create)、输出是页表项指针或返回码，验证方式是官方 `page_check` 的 walk 断言。

## 本平台判题说明

- 判题方式：**Tier A**：主机端等价 harness，判的就是真实目标函数 `boot_pgdir_walk()`/`pgdir_walk()`。
- 你需要填写：`static Pte *boot_pgdir_walk(Pde *pgdir, uint32_t va, int create)` 与 `static int pgdir_walk(Pde *pgdir, uint32_t va, int create, Pte **ppte)`，在 `starter.c` 的「学生填写区」内。
- 判题输入：首行 `npage freemem_bytes`，次行操作数，随后每行一个操作（见下方操作码说明）。
- 判题输出：每个操作各输出一行，末行为空闲链表页号序列。
- `B` va create：启动期步行 `boot_pgdir_walk`，输出「B 页表物理地址 PTX」；取不到页表项时两个字段均为 `-1`。
- `W` va create：运行期步行 `pgdir_walk`，输出「W 返回码 页表物理地址 PTX」；取不到页表项时后两个字段均为 `-1`（页表分配失败时返回码为 `-4`，否则为 `0`）。
- `Q` pageidx：查询该页引用计数，输出「Q 页号 pp_ref」。新建页表必须使该页 `pp_ref` 加一。
- 宿主环境适配：`Pde`/`Pte` 用 `uint32_t`（MIPS 32 位页表项），不用 `uintptr_t` 或 `long`；`KADDR` 等价替代为「物理地址 → `phys[]` 下标」；页表物理地址是数组下标而非宿主指针，故与宿主地址宽度无关。
- 与真实内核的关系：两级索引、create 语义与 `pp_ref` 维护与真实内核一致；本地仍应跑官方 `page_check` 完整验证。
- 内容来源标注：`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 mm/pmap.c 的 boot_pgdir_walk()/pgdir_walk()。stdin 首行 npage freemem_bytes，次行操作数，随后每行：B va create=启动期步行；W va create=运行期步行；Q pageidx=查询该页 pp_ref。输出：B 行为「B 页表物理地址 PTX」，W 行为「W 返回码 页表物理地址 PTX」，取不到页表项时两个字段均为 -1；Q 行为「Q 页号 pp_ref」；末行为空闲链表页号序列。
```

## 本地完整验证

本地完整验证（选填）：恢复 `page_check()`，它会 walk 一系列 va 并断言返回的 PTE 指针与 `pp_ref`；再在 gxemul 里确认内核能正常访问 `UENVS`/`UPAGES` 区域。

## 常见错误

1. 新建页表后忘记置 `PTE_V`（`wrong.c` 的做法）：下次 `create==0` 的步行返回 NULL，`create==1` 的步行再分配一张页表，造成泄漏与映射丢失。
2. 新建页表不清零：残留字节被当成有效 PTE，随机映射到错误物理页。
3. 新页表的 `pp_ref` 不递增：它可能被 `page_free` 提前回收。
4. `create==0` 时也去分配页表：查询操作产生副作用，空闲页计数不符。
5. 用 `PDX` 取低位、`PTX` 取高位：两级索引颠倒，所有映射错位。
