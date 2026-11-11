# 实验  3.5 设置进程控制块

## 实验背景

**题目原文**

### 任务描述

本实验旨在通过实现 `env_alloc` 函数，学习如何在操作系统中创建新进程并正确设置其状态。具体任务包括从空闲进程链表中获取一个新的进程控制块（PCB），初始化该 PCB，并设置进程的状态寄存器（SR）以确保进程能够正确运行。

从空闲 PCB 到可装载进程需要建立地址空间、身份和初始寄存器；失败路径也必须保留空闲槽位。

## 知识要点

### 知识要点

1. **进程控制块（PCB）**：
   - PCB 是操作系统用于管理进程的数据结构，包含进程的状态、优先级、程序计数器、寄存器集合、内存管理信息等。
2. **状态寄存器（SR）**：
   - SR 是 MIPS 架构中用于存储 CPU 状态的寄存器，包括用户模式（KUc）、中断使能（IEc）、内核模式（KUp）等。
3. **进程状态**：
   - 理解进程的不同状态，如用户态和内核态，以及如何在这些状态之间切换。
4. **MIPS 寄存器**：
   - 理解 MIPS 架构中的寄存器，特别是程序计数器（PC）和状态寄存器（SR）。
5. **内存管理**：
   - 理解虚拟内存的分配和管理，特别是页目录和页表的作用。

**理解与应用**：题面中的 cpo_status 按原文保留；平台字段名为 cp0_status。平台使用 KUp、IEp 等位设置初始返回现场。

## 关联源码

| 文件 | 作用 | 关注符号 |
| --- | --- | --- |
| `lib/env.c` | 题面所涉代码位置 | `static int env_alloc(struct Env **newenv, uint32_t parent_id)` |

**内容来源**：原文来自 question.content；原指导 Listing 未随工作区提供，平台按题面及公开语义构造等价示例。

## 实验任务

### 实验任务

1. **实现 `env_alloc` 函数**：
   - 从 `env_free_list` 中获取一个新的 PCB。
   - 初始化 PCB 的各个字段，包括 `id`、`status`、`sp` 寄存器、`CPU` 状态、`parent_id` 等。
   - 特别注意设置 `cpo_status` 字段，确保进程能够正确地在用户态和内核态之间切换。
2. **设置进程状态**：
   - 在 `env_alloc` 函数中，设置 `cpo_status` 字段，确保进程在创建时处于正确的状态。
3. **初始化虚拟内存布局**：
   - 调用 `env_setup_vm` 函数，为新进程初始化虚拟内存布局。

**实施与验证**：先检查空闲 PCB，再建立页目录；成功后初始化标识、父进程、状态、栈及现场，最后摘链并返回。使用 N、P 与污染后重用场景检查字段。

## 本平台判题说明

- 判题方式：Tier A 单文件 C 主机端等价 harness，填写 `starter.c` 中成对标记包围的学生填写区：`static int env_alloc(struct Env **newenv, uint32_t parent_id)`。
- 宿主以固定宽度整数及数组模拟地址与内存；完整验证仍需本地 MIPS 环境。
- 以下逐字引用 `cases.json.description`；stdin 按空白分词，输出的行内空格敏感。

> 驱动 lib/env.c 的 env_alloc()。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..80），随后 n 行：I=调用平台 env_init()（重复打印 I SKIP）；A parent=调用学生 env_alloc(&e,parent)，打印「A 返回码 PCB下标 新envid」（*newenv 为空或越界时下标 -1、envid 0）；N idx=打印「N idx env_id parent_id status pri sp sr KUc IEc KUp IEp」；P idx=打印「P idx env_pgfault_handler ipc_receiving ipc_from ipc_value」；D idx=驱动把该 PCB 的 Trapframe 全寄存器污染成 0xDEADBEEF、cp0_status 污染成 0xFFFFFFFF、env_pri=99、env_status=ENV_RUNNABLE、pgfault=12345、ipc=1/777/888，用于检验 env_alloc 是否真的重新初始化；F idx=平台 env_free；U idx=设 curenv；L k=打印空闲 PCB 链表前 k 个下标；W idx=打印「W idx 页目录物理地址 env_cr3」；C=空闲物理页数。idx 必须 <64。错误码：-5=-E_NO_FREE_ENV（空闲 PCB 耗尽），-4=-E_NO_MEM（env_setup_vm 拿不到物理页）。ENV_USER_SR=KUp|IEp|IM4|CU0=268439564，USTACKTOP=0x40000000=1073741824。

**操作与参数 → 输出**：

- `I` → I 数量 / I SKIP
- `A` parent → A 返回码 下标 envid
- `N` idx → N idx env_id parent status pri sp sr KUc IEc KUp IEp
- `P` idx → P idx handler receiving from value
- `D` idx → D idx
- `F` idx → F idx
- `U` idx → U idx
- `L` k → L 下标...
- `W` idx → W idx 物理地址 env_cr3
- `C` → C 空闲页数

**契约片段**：「A 返回码 PCB下标 新envid」；「N idx env_id parent_id status pri sp sr KUc IEc KUp IEp」；「P idx env_pgfault_handler ipc_receiving ipc_from ipc_value」；「W idx 页目录物理地址 env_cr3」。

## 常见错误

1. 页目录分配失败前就摘链，会丢失 PCB。
2. 新 PCB 直接设为 RUNNABLE，可能在装载前被调度。
3. 不清理旧 Trapframe、缺页处理和 IPC 字段，或把 KUp/IEp 错设为 KUc/IEc。
