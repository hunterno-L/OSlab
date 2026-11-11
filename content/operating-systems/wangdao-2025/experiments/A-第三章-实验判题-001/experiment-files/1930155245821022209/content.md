# 实验  3.6 加载镜像映射

## 实验背景

**题目原文**

### 任务描述

本实验的目标是实现进程镜像加载功能，具体任务包括为新创建的进程分配虚拟内存空间，并加载其二进制镜像（ELF文件）到该空间中。实验涉及理解 ELF 文件格式、虚拟内存管理和进程控制块（PCB）的初始化。

可执行镜像的段可能跨越多个物理页，也可能从页中间开始，映射与内容必须一同正确。

## 知识要点

### 知识要点

1. **ELF 文件格式**：
   - 理解 ELF（Executable and Linkable Format）文件的结构和内容，包括程序头（Program Header）和节头（Section Header）。
2. **虚拟内存管理**：
   - 理解虚拟内存的基本概念，包括页目录（Page Directory）、页表（Page Table）和虚拟地址到物理地址的映射。
3. **进程控制块（PCB）**：
   - 理解 PCB 的结构和作用，包括进程状态、寄存器集合和内存管理信息。
4. **内存分配**：
   - 理解如何在操作系统中分配和管理内存页。
5. **MIPS 架构**：
   - 理解 MIPS 架构中的寄存器和指令，特别是与内存管理相关的指令。

**理解与应用**：每页由 page_alloc 分配，page_insert 建立映射并负责增加引用计数；首页偏移和末页不足部分必须按实际字节处理。

## 关联源码

| 文件 | 作用 | 关注符号 |
| --- | --- | --- |
| `lib/env.c` | 题面所涉代码位置 | `static int load_icode_mapper(void *binary, uint32_t size, uint32_t va, int perm, struct Env *e)` |

**内容来源**：原文来自 question.content；原指导 Listing 未随工作区提供，平台按题面及公开语义构造等价示例。

## 实验任务

### 实验任务

1. **实现 `load_icode_mapper` 函数**：
   - 该函数用于将 ELF 文件中的每个段映射到正确的虚拟地址。
   - 需要加载 ELF 文件的所有内容到内存，并根据段的大小分配相应的内存页。
2. **理解 `load_elf` 函数**：
   - `load_elf` 函数负责解析 ELF 文件并提取每个段的信息，包括虚拟地址、段大小和文件内容。
   - 理解该函数如何将这些信息传递给 `load_icode_mapper` 函数。
3. **内存页分配**：
   - 在 `load_icode_mapper` 函数中，为每个段分配内存页，并增加引用计数。

**实施与验证**：将覆盖区间向页边界对齐，把镜像从 va 对应偏移写入，按 perm 插入映射；使用 Q、P、D、R、C 检查地址、权限、内容与引用。

## 本平台判题说明

- 判题方式：Tier A 单文件 C 主机端等价 harness，填写 `starter.c` 中成对标记包围的学生填写区：`static int load_icode_mapper(void *binary, uint32_t size, uint32_t va, int perm, struct Env *e)`。
- 宿主以固定宽度整数及数组模拟地址与内存；完整验证仍需本地 MIPS 环境。
- 以下逐字引用 `cases.json.description`；stdin 按空白分词，输出的行内空格敏感。

> 驱动 lib/env.c 的 load_icode_mapper()。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..48），随后 n 行：I=平台 env_init()（重复打印 I SKIP）；A=平台 env_alloc(&e,0)，打印「A 返回码 PCB下标」；U idx=选定后续 M 的目标进程；B=把镜像缓冲填成 seg_binary[i]=i&0xFF（位置相关图样）；M va size perm=调学生 load_icode_mapper(seg_binary,size,va,perm,目标进程)，打印「M 返回码」，要求 va<1073741824、size<=16384、perm 0..4095，且必须先 U；Q idx va=打印物理地址（无映射 4294967295）；P idx va=打印原始 PTE（含权限位）；D idx va=打印该 va 处的 32 位小端字（无映射或跨页 4294967295）；R idx va=打印该 va 所映射物理页的 pp_ref（无映射 -1）；C=空闲物理页数。idx 必须 <64。引用计数契约：page_insert 自身 pp_ref++，mapper 不得重复递增，故每个数据页 pp_ref 恒为 1。注意 page_insert 首次映射某个 4MB 区间时还会额外分配一页作页表，C 的计数包含页表页。

**操作与参数 → 输出**：

- `I` → I 数量 / I SKIP
- `A` → A 返回码 下标
- `U` idx → U idx
- `B` → B
- `M` va size perm → M 返回码
- `Q` idx va → Q 物理地址
- `P` idx va → P 页表项
- `D` idx va → D 字
- `R` idx va → R 引用数
- `C` → C 空闲页数

**契约片段**：「A 返回码 PCB下标」；「M 返回码」。

## 常见错误

1. 首页从页首复制导致非对齐段整体错位。
2. 对段尾向下取整会丢失最后一页。
3. 在 page_insert 外再次增加引用，造成页泄漏。
