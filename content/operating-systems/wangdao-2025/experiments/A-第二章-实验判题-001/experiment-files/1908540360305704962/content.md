# 实验 2.6 地址映射

## 实验背景

本实验要求学生在 MIPS CPU 环境中实现物理内存与虚拟内存之间的地址映射功能。通过编写 `boot_map_segment` 函数，学生将学习如何在内核启动时将特定的物理内存区域映射到虚拟内存空间中。

会走页表之后，就要会批量填页表。内核启动时必须把整段物理内存（内核映像、`pages` 数组、之后的 `envs` 数组）映射到 `kseg0` 的对应虚拟地址，否则 C 代码一访问全局变量就异常。`boot_map_segment` 就是这个批量映射器。

## 知识要点

1. **虚拟内存**：理解虚拟内存的概念及其在操作系统中的作用。
2. **地址映射**：掌握虚拟地址到物理地址的映射机制。
3. **页表**：理解页表在地址映射中的作用和实现方法。
4. **C 语言编程**：熟悉 C 语言的基本语法，特别是函数的定义、内存操作和数据结构。
5. **操作系统内核**：了解操作系统内核中内存管理的基本概念和实现方法。

**扩写**：`boot_map_segment(pgdir, va, size, pa, perm)` 要求 `size` 是 `BY2PG` 的整数倍（否则直接返回），然后逐页 `boot_pgdir_walk(..., create=1)` 取 PTE 并写入 `pa | perm | PTE_V`。**`PTE_V` 不能漏**：物理地址与权限都对但有效位为 0 时，MMU 认为该页不存在。`kseg0` 的映射满足 `pa = va - 0x80000000`，这是内核早期不经 TLB 的固定映射。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `mm/pmap.c` | 本实验要补全的函数 | boot_map_segment、boot_pgdir_walk、PTE_V、perm |
| `mm/pmap.c 的 mips_vm_init` | 调用者 | boot_map_segment(pgdir, KERNBASE, ...), pages, envs |
| `include/mmu.h` | 地址常量与权限位 | KERNBASE、PTE_V、PTE_R、BY2PG |

**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。harness 的页表项编码（`pa|perm|PTE_V`）与「页表不存在读作 ~0、已存在未映射读作 0」的观测口径按公开 MOS 语义构造，是**等价环境**。

## 实验任务

**实验任务**

1. **理解地址映射**：
   - 理解虚拟内存地址映射的基本概念和重要性。
   - 理解页表在地址映射中的作用。
2. **编写 `boot_map_segment` 函数**：
   - 编写 `boot_map_segment` 函数，实现将指定的物理内存与虚拟内存建立起映射的功能。
   - 确保函数能够正确地设置页表项，实现地址映射。

**每步输入/输出/验证**：步骤 1 产出「能解释为什么启动期必须建立这段映射」；步骤 2 产出补全的 `boot_map_segment`，输入是 (va, size, pa, perm)、输出是页表项内容，验证方式是官方 `page_check` 与内核能否访问全局变量。

## 本平台判题说明

- 判题方式：**Tier A**：主机端等价 harness，判的就是真实目标函数 `boot_map_segment()`。
- 你需要填写：`static void boot_map_segment(Pde *pgdir, uint32_t va, uint32_t size, uint32_t pa, int perm)`，在 `starter.c` 的「学生填写区：实现 mm/pmap.c 的 boot_map_segment()」内。
- 判题输入：首行 `npage freemem_bytes`，次行操作数，随后每行一个操作（见下方操作码说明）。
- 判题输出：每个操作各输出一行。页表项取值口径：所属页表不存在时为 `4294967295`（~0）；页表已存在时为该槽位原值（未映射即 `0`，已映射即 `pa|perm|PTE_V`）。
- `M` va size pa perm：建立区间映射，输出「M 首页页表项 末页页表项」；`size==0` 时末页字段固定为 `4294967295`。
- `Q` va：查询该虚拟地址的页表项原值，输出「Q 页表项」。
- 宿主环境适配：页表项用 `uint32_t`，`~0` 打印为十进制 `4294967295`（不用 `%p`/`%lx`）；`KADDR` 等价替代为 `phys[]` 下标换算；驱动自有输出缓冲在声明处初始化。
- 与真实内核的关系：逐页 walk + 写 `pa|perm|PTE_V` 的逻辑与真实内核一致；本地仍应在 gxemul 里确认内核能访问 `pages`/`envs` 全局数组。
- 内容来源标注：`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 mm/pmap.c 的 boot_map_segment()。stdin 首行 npage freemem_bytes，次行操作数，随后每行：M va size pa perm=建立区间映射；Q va=查询该虚拟地址的页表项原值。输出：M 行为「M 首页页表项 末页页表项」（size==0 时末页字段固定为 4294967295），Q 行为「Q 页表项」。页表项字段取值：该 va 所属页表不存在时为 4294967295（~0）；页表已存在时为该槽位原值（未映射即 0，已映射即 pa|perm|PTE_V）。
```

## 本地完整验证

本地完整验证（选填）：`make` 后 gxemul 启动，若 `boot_map_segment` 漏写 `PTE_V`，内核在第一次访问 `pages[]` 时就会 TLB 异常；能跑到 `mips_init` 末尾即说明映射有效。

## 常见错误

1. 写页表项时漏掉 `PTE_V`（`wrong.c` 的做法）：物理地址与权限都对，但 MMU 认为该页不存在，后续 walk 与 `page_lookup` 都取不到映射。
2. 不检查 `size % BY2PG != 0`：非整页区间被静默映射，末页越界。
3. 循环用 `va <= va_end` 而非 `<`：多映射一页，踩到相邻区域。
4. `pa` 不随 `va` 同步前进：整段区间映射到同一物理页。
5. `size == 0` 时仍去 walk 末页：驱动约定此时末页字段固定为 `4294967295`。
