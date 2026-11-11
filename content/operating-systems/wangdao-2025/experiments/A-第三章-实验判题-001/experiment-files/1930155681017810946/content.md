# 实验  3.9 进程创建宏命令应用

## 实验背景

**题目原文**

### 任务描述

本实验的目标是将已经定义的 `ENV_CREATE` 和 `ENV_CREATE_PRIORITY` 宏命令应用到操作系统的初始化过程中。具体任务是将这两条进程创建命令加入到 `init/init.c` 文件中，以初始化创建两个进程。

启动阶段创建初始用户进程，使系统随后能调度执行它们。

## 知识要点

### 知识要点

1. **宏命令应用**：
   - 理解宏命令的定义和用法，以及如何在代码中正确地应用宏命令。
2. **进程初始化**：
   - 理解操作系统启动过程中进程初始化的步骤和重要性。
3. **C 语言编程**：
   - 理解 C 语言的基本语法和编程技巧，特别是如何在文件中添加和使用宏命令。

**理解与应用**：题面要求 A 优先级 2、B 优先级 1；平台把原来的初始化片段封装为函数，宏使用符号拼接，参数是 user_A、user_B。

## 关联源码

| 文件 | 作用 | 关注符号 |
| --- | --- | --- |
| `init/init.c` | 题面所涉代码位置 | `static void init_user_envs(void)` |

**内容来源**：原文来自 question.content；原指导 Listing 未随工作区提供，平台按题面及公开语义构造等价示例。

## 实验任务

### 实验任务

1. **定位合适的代码位置**：
   - 在 `init/init.c` 文件中找到合适的位置，通常是在操作系统启动过程中初始化用户进程的部分。
2. **添加宏命令**：
   - 将 `ENV_CREATE_PRIORITY(user_A, 2);` 和 `ENV_CREATE_PRIORITY(user_B, 1);` 这两条语句添加到 `init.c` 文件中，以初始化创建两个具有不同优先级的进程。

**实施与验证**：依原文顺序写两条 ENV_CREATE_PRIORITY 调用；用 N 执行，使用 T 检查两个优先级，用 L 检查头插顺序。

## 本平台判题说明

- 判题方式：Tier A 单文件 C 主机端等价 harness，填写 `starter.c` 中成对标记包围的学生填写区：`static void init_user_envs(void)`。
- 宿主以固定宽度整数及数组模拟地址与内存；完整验证仍需本地 MIPS 环境。
- 以下逐字引用 `cases.json.description`；stdin 按空白分词，输出的行内空格敏感。

> 驱动 init/init.c 的初始化片段 init_user_envs()（北航 3.9：把 ENV_CREATE_PRIORITY(user_A,2); 与 ENV_CREATE_PRIORITY(user_B,1); 两条语句加进 init.c）。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..96），随后 n 个操作：I=平台 env_init()（重复打印 I SKIP）；B=把镜像缓冲填成 seg_binary[i]=i&0xFF；G=清空段表；S va size perm=追加一段（最多8段，va<1073741824、size<=16384、perm 0..4095），打印「S 当前段数」；E entry=设 elf_entry；J=平台 env_alloc(&e,0) 占用一个 PCB（不装镜像、不入就绪链表），打印「J 返回码 下标」；N=调用学生填写的 init_user_envs()，打印「N」；H=就绪链表头下标（空 -1）；L=就绪链表顺序（头插故为创建逆序）；T idx=打印「T idx env_id parent status pri cp0_epc sp」；F=空闲PCB数；C=空闲物理页数；Q idx va=物理地址（无映射 4294967295）；D idx va=32位小端字（无映射/跨页 4294967295）；R idx va=所映射物理页 pp_ref（无映射 -1）。idx<64。关键常量：ENV_RUNNABLE=2、ENV_NOT_RUNNABLE=1、ENV_FREE=0、USTACKTOP=1073741824、栈页映射在 USTACKTOP-BY2PG=1073737728。env_init 头插建空闲链表，故分配顺序 63,62,61...；env_id=(生成号<<7)|下标，生成号从1起每次分配递增。每个非空镜像进程耗5页（单段）或8页（本驱动两段样例），空镜像进程耗3页，单纯 env_alloc 占1页。宏参数是镜像名后缀（user_A），不是字符串也不是 A。正确实现必须创建两个进程：先 user_A 优先级2，再 user_B 优先级1，二者都置 ENV_RUNNABLE 并头插就绪链表。

**操作与参数 → 输出**：

- `I` → I 数量 / I SKIP
- `B` → B
- `G` → G
- `S` va size perm → S 段数
- `E` entry → E entry
- `J` → J 返回码 下标
- `N` → N
- `H` → H 下标
- `L` → L 下标...
- `T` idx → T idx env_id parent status pri cp0_epc sp
- `F` → F 空闲数
- `C` → C 空闲页数
- `Q` idx va → Q 物理地址
- `D` idx va → D 字
- `R` idx va → R 引用数

**契约片段**：「S 当前段数」；「J 返回码 下标」；「N」；「T idx env_id parent status pri cp0_epc sp」。

## 常见错误

1. 改用默认优先级的 ENV_CREATE，两个进程均为 1。
2. 把镜像参数写成字符串或 A 而非 user_A。
3. 交换创建顺序，或只检查进程数量而不检查优先级。
