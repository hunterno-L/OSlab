# 实验  3.8 创建进程

## 实验背景

**题目原文**

### 任务描述

本实验的目标是实现进程的创建和管理功能。具体任务包括编写 `env_create` 函数和 `env_create_priority` 函数，这些函数负责分配新的进程控制块（PCB），设置进程控制块，并加载二进制代码到对应的地址空间。

创建进程将 PCB 分配、镜像装载及就绪链表组织连接起来；创建成功与立即可调度是不同阶段。

## 知识要点

### 知识要点

1. **进程控制块（PCB）**：
   - 理解 PCB 的结构和作用，包括进程状态、寄存器集合和内存管理信息。
2. **进程创建**：
   - 理解进程创建的基本步骤，包括分配 PCB、设置进程状态、分配内存空间等。
3. **内存管理**：
   - 理解虚拟内存的分配和管理，特别是页目录和页表的作用。
4. **ELF 文件格式**：
   - 理解 ELF 文件的结构，包括程序头和节头，以及如何从中提取段信息。
5. **MIPS 架构**：
   - 理解 MIPS 架构中的寄存器和指令，特别是与内存管理相关的指令。

**理解与应用**：平台以输入段表和入口代替 ELF 镜像解析；binary 与 size 用于空镜像守卫，默认优先级为 1，指定值 0 也合法。

## 关联源码

| 文件 | 作用 | 关注符号 |
| --- | --- | --- |
| `lib/env.c` | 题面所涉代码位置 | `static void env_create(void *binary, uint32_t size)；static void env_create_priority(void *binary, uint32_t size, int priority)` |

**内容来源**：原文来自 question.content；原指导 Listing 未随工作区提供，平台按题面及公开语义构造等价示例。

## 实验任务

### 实验任务

1. **实现 `env_create` 函数**：
   - 分配一个新的 PCB 结构体。
   - 设置 PCB 的基本属性，如进程状态、寄存器集合等。
   - 调用 `env_alloc` 函数分配 PCB 并初始化。
   - 调用 `load_icode` 函数加载二进制代码到虚拟内存。
2. **实现 `env_create_priority` 函数**：
   - 类似于 `env_create` 函数，但允许指定进程的优先级。
   - 设置 PCB 的优先级属性。

**实施与验证**：拒绝空镜像并检查分配失败；完成装载、优先级与 RUNNABLE 设置后头插就绪链表。用 T、L、Q 比较状态、顺序和映射。

## 本平台判题说明

- 判题方式：Tier A 单文件 C 主机端等价 harness，填写 `starter.c` 中成对标记包围的学生填写区：`static void env_create(void *binary, uint32_t size)；static void env_create_priority(void *binary, uint32_t size, int priority)`。
- 宿主以固定宽度整数及数组模拟地址与内存；完整验证仍需本地 MIPS 环境。
- 以下逐字引用 `cases.json.description`；stdin 按空白分词，输出的行内空格敏感。

> 驱动 lib/env.c 的 env_create()/env_create_priority()。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..80），随后 n 个操作：I=平台 env_init()（重复打印 I SKIP）；B=把镜像缓冲填成 seg_binary[i]=i&0xFF；G=清空段表；S va size perm=追加一段（最多8段，va<1073741824、size<=16384、perm 0..4095），打印「S 当前段数」；E entry=设 elf_entry；A x=ENV_CREATE(x)（x∈A/B/C，默认优先级1），打印「A x」；P x pri=ENV_CREATE_PRIORITY(x,pri)（pri 0..65535），打印「P x pri」；Z size=env_create(binary_user_A,size)（size=0 触发空镜像守卫）；Y=env_create(NULL,64)（NULL 触发守卫）；J=平台 env_alloc(&e,0)（不加载、不入就绪链表，用于耗尽空闲PCB），打印「J 返回码 下标」；H=就绪链表头下标（空 -1）；T idx=打印「T idx env_id parent status pri cp0_epc sp」；L=就绪链表顺序（头插故为创建逆序）；F=空闲PCB数；C=空闲物理页数；Q idx va=物理地址（无映射 4294967295）；D idx va=32位小端字（无映射/跨页 4294967295）；R idx va=所映射物理页 pp_ref（无映射 -1）。idx<64。关键常量：ENV_RUNNABLE=2、ENV_NOT_RUNNABLE=1、USTACKTOP=1073741824、栈页映射在 USTACKTOP-BY2PG=1073737728。宿主等价：load_icode 用 stdin 段表+elf_entry 加载，binary/size 仅用于「空镜像(NULL或size0)不创建进程」守卫；env_create 默认优先级1，env_create_priority 原样保存 pri（0 合法），必须置 ENV_RUNNABLE 并头插 env_sched_list，env_alloc 失败立即返回。

**操作与参数 → 输出**：

- `I` → I 数量 / I SKIP
- `B` → B
- `G` → G
- `S` va size perm → S 段数
- `E` entry → E entry
- `A` x → A x
- `P` x pri → P x pri
- `Z` size → Z size
- `Y` → Y
- `J` → J 返回码 下标
- `H` → H 下标
- `T` idx → T idx env_id parent status pri cp0_epc sp
- `L` → L 下标...
- `F` → F 空闲数
- `C` → C 空闲页数
- `Q` idx va → Q 物理地址
- `D` idx va → D 字
- `R` idx va → R 引用数

**契约片段**：「S 当前段数」；「A x」；「P x pri」；「J 返回码 下标」；「T idx env_id parent status pri cp0_epc sp」；「空镜像(NULL或size0)不创建进程」。

## 常见错误

1. 默认优先级设为 0。
2. 不调用 load_icode 或不置 ENV_RUNNABLE。
3. 忽略空镜像守卫，或分配失败后仍加入就绪链表。
