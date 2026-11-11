# 实验 2.2 初始化全局变量

## 实验背景

在 MIPS CPU 系统中，物理内存的管理是操作系统内存管理的核心部分。本实验要求学生编写 `mips_detect_memory()` 函数，该函数用于初始化与内存相关的全局变量，并确定内核可用的物理内存的大小和范围。

内核要先知道自己有多少内存，才能谈分配。MIPS 机器上这份信息来自 CMOS 的两个读数（1MB 以下与 1MB 以上各多少 KB）。`mips_detect_memory()` 把它们换算成字节并算出页框总数，之后 `page_init`、`page_alloc`、`boot_map_segment` 全都以这四个全局变量为边界。

## 知识要点

1. **物理内存管理**：理解物理内存的基本概念和在操作系统中的作用。
2. **全局变量**：掌握全局变量的定义和初始化方法。
3. **C 语言编程**：熟悉 C 语言的基本语法，特别是函数的定义和变量的初始化。
4. **内存检测技术**：了解如何检测和计算可用的物理内存。

**扩写**：`basemem` 是 1MB 以下的常规内存字节数，`extmem` 是 1MB 以上的扩展内存字节数，`maxpa = basemem + extmem` 是物理地址上限，`npage = maxpa / BY2PG` 是页框总数（`BY2PG = 4096`）。**CMOS 读数的单位是 KB，必须乘 1024**；漏乘会让整个内存管理系统缩小 1024 倍，而且不会立刻报错——这是最难查的一类缺陷。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `mm/pmap.c` | 本实验要补全的函数与全局变量 | mips_detect_memory、maxpa、npage、basemem、extmem、BY2PG |
| `include/mmu.h` | 页大小与地址常量 | BY2PG、KERNBASE |
| `mm/pmap.c 的 page_init` | npage 的第一个消费者 | for (i = 0; i < npage; i++) |

**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 仅 18 字节、`answer` 仅 6 字节，均无效）。harness 用两个 stdin 读数等价替代 CMOS 端口读取，其余全局变量名与语义按北航原文与公开 MOS 语义构造，是**等价环境**。

## 实验任务

**实验任务**

1. **理解全局变量**：
   - 理解 `maxpa`、`npage`、`basemem` 和 `extmem` 变量的含义和用途。
   - 掌握这些变量在物理内存管理中的作用。
2. **编写 `mips_detect_memory()` 函数**：
   - 根据代码注释中的提示，完成 `mips_detect_memory()` 函数的编写。
   - 该函数应初始化 `maxpa`、`npage`、`basemem` 和 `extmem` 变量，以确定内核可用的物理内存的大小和范围。

**每步输入/输出/验证**：步骤 1 产出「能说出四个变量各自的单位与含义」；步骤 2 产出补全的 `mips_detect_memory()`，输入是 CMOS 的 KB 读数、输出是四个全局变量，验证方式是官方 `page_check` 与后续 `page_init` 的空闲页计数。

## 本平台判题说明

- 判题方式：**Tier A**：主机端等价 harness，判的就是真实目标函数 `mips_detect_memory()`。
- 你需要填写：`void mips_detect_memory(void)`，在 `starter.c` 的「学生填写区：实现 mm/pmap.c 的 mips_detect_memory()」内。从驱动提供的 `cmos_basemem_kb`/`cmos_extmem_kb` 读数算出 `basemem`、`extmem`、`maxpa`、`npage`。
- 判题输入：两个 KB 读数模拟 CMOS：`basemem_kb extmem_kb`（一行，十进制）。
- 判题输出：一行四个十进制数：`maxpa basemem extmem npage`（前三者单位字节，npage 单位页）。
- 宿主环境适配：物理内存大小用 `uint32_t` 承载（MIPS 32 位物理地址），不用 `size_t`/`long`；CMOS 端口读取用两个 stdin 整数等价替代，因为沙箱是 x86-64 用户态进程、无法访问 MIPS 的 CMOS 端口。
- 与真实内核的关系：换算逻辑与真实内核逐字一致；本地仍应在 gxemul 里跑真实 CMOS 探测路径验证。
- 内容来源标注：`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 mm/pmap.c 的 mips_detect_memory()。stdin 两个 KB 读数模拟 CMOS：basemem_kb extmem_kb。输出一行四个十进制数：maxpa basemem extmem npage（前三者字节，npage 页数）。
```

## 本地完整验证

本地完整验证（选填）：`make` 后 gxemul 启动，在 `mips_init` 里 `printf("maxpa=%x npage=%d\n", maxpa, npage);`，与 gxemul 命令行指定的内存大小（如 `-m 64`）核对：64MB 应对应 `npage = 16384`。

## 常见错误

1. 把 KB 读数直接当字节写进 basemem/extmem（`wrong.c` 的做法）：maxpa 与 npage 全部缩小 1024 倍。
2. `maxpa` 只算了 extmem 漏掉 basemem：1MB 以下的内存凭空消失。
3. `npage` 用 `maxpa / 4096` 之外的粒度（如按字节或按 KB）：后续所有页号越界。
4. 用 `int` 承载物理地址：大于 2GB 时溢出为负，必须用 `uint32_t`。
