# 实验  3.7 完整加载镜像

## 实验背景

**题目原文**

### 任务描述

本实验的目标是实现用户进程的初始栈和程序二进制镜像的加载。具体任务包括使用 ELF 加载器将完整的二进制镜像加载到用户进程的虚拟内存中，并设置程序的初始栈和入口点。

完整装载连接段映射、入口点和用户栈；任何一项错误都可能使进程无法正常启动。

## 知识要点

### 知识要点

1. **ELF 文件格式**：
   - 理解 ELF 文件的结构，包括程序头和节头，以及如何从中提取段信息。
2. **虚拟内存管理**：
   - 理解虚拟内存的基本概念，包括页目录、页表和地址映射。
3. **进程控制块（PCB）**：
   - 理解 PCB 的结构和作用，包括进程状态、寄存器集合和内存管理信息。
4. **内存分配**：
   - 理解如何在操作系统中分配和管理内存页。
5. **MIPS 架构**：
   - 理解 MIPS 架构中的寄存器和指令，特别是与内存管理相关的指令。

**理解与应用**：平台 load_elf 根据输入段表逐段调用 mapper，发生错误即停；其不是实际解析 ELF 文件。

## 关联源码

| 文件 | 作用 | 关注符号 |
| --- | --- | --- |
| `lib/env.c` | 题面所涉代码位置 | `static void load_icode(struct Env *e, uint32_t entry)` |

**内容来源**：原文来自 question.content；原指导 Listing 未随工作区提供，平台按题面及公开语义构造等价示例。

## 实验任务

### 实验任务

1. **实现 `load_icode` 函数**：
   - 该函数负责设置用户进程的初始栈和程序二进制镜像。
   - 使用 ELF 加载器加载完整的二进制镜像到用户进程的虚拟内存中。
   - 设置程序的初始栈和入口点。
2. **理解 `load_elf` 函数**：
   - `load_elf` 函数负责解析 ELF 文件并提取每个段的信息。
   - 将这些信息传递给 `load_icode_mapper` 函数以完成加载过程。
3. **内存页分配**：
   - 在 `load_icode` 函数中，为程序的初始栈分配内存页，并设置适当的权限。
4. **设置入口点**：
   - 在 `load_icode` 函数中，设置 CPU 的程序计数器（PC）寄存器为 ELF 文件的入口点。

**实施与验证**：检查加载结果，设置 cp0_epc；在 USTACKTOP-BY2PG 映射栈页并设置 regs[29]。用 N 检查入口和栈指针，用 Q、P、R 检查栈映射。

## 本平台判题说明

- 判题方式：Tier A 单文件 C 主机端等价 harness，填写 `starter.c` 中成对标记包围的学生填写区：`static void load_icode(struct Env *e, uint32_t entry)`。
- 宿主以固定宽度整数及数组模拟地址与内存；完整验证仍需本地 MIPS 环境。
- 以下逐字引用 `cases.json.description`；stdin 按空白分词，输出的行内空格敏感。

> 驱动 lib/env.c 的 load_icode()。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..48），随后 n 行：I=平台 env_init()（重复打印 I SKIP）；A=平台 env_alloc(&e,0)，打印「A 返回码 PCB下标」；U idx=选定后续 L 的目标进程；B=把镜像缓冲填成 seg_binary[i]=i&0xFF；G=清空段表；S va size perm=追加一段（最多 8 段，va<1073741824、size<=16384、perm 0..4095），打印「S 当前段数」；L entry=调学生 load_icode(目标进程,entry)，打印「L」（函数无返回值，必须先 U）；X idx=把该 PCB 的 sp 污染成 0xDEADBEEF、cp0_epc 污染成 0x12345678；N idx=打印「N idx cp0_epc sp」；Q idx va=物理地址（无映射 4294967295）；P idx va=原始 PTE；D idx va=该 va 处 32 位小端字（无映射或跨页 4294967295）；R idx va=所映射物理页的 pp_ref（无映射 -1）；C=空闲物理页数。idx 必须 <64。关键常量：USTACKTOP=1073741824，栈页映射在 USTACKTOP-BY2PG=1073737728，权限 PTE_R|PTE_V=3，pp_ref 恒为 1（page_insert 负责递增）。宿主等价：load_elf 遍历 stdin 给出的段表逐段回调 load_icode_mapper，遇错即停。

**操作与参数 → 输出**：

- `I` → I 数量 / I SKIP
- `A` → A 返回码 下标
- `U` idx → U idx
- `B` → B
- `G` → G
- `S` va size perm → S 段数
- `L` entry → L
- `X` idx → X idx
- `N` idx → N idx cp0_epc sp
- `Q` idx va → Q 物理地址
- `P` idx va → P 页表项
- `D` idx va → D 字
- `R` idx va → R 引用数
- `C` → C 空闲页数

**契约片段**：「A 返回码 PCB下标」；「S 当前段数」；「L」；「N idx cp0_epc sp」。

## 常见错误

1. load_elf 失败后仍继续初始化入口和栈。
2. 把栈页映射到 USTACKTOP 而非其下一页。
3. 漏设栈指针，或在 page_insert 外重复增加引用。
