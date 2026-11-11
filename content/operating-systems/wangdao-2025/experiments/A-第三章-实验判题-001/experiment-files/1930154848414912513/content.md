# 实验  3.3 进程标识

## 实验背景

**题目原文**

### 任务描述 

在操作系统中，每个进程都有一个唯一的标识符（`env_id`），用于区分和管理不同的进程。本实验的任务是通过进程标识符获取对应的进程控制块（PCB），实现进程的管理和调度。

进程编号兼有槽位与生成号，槽位重用后必须防止旧编号访问新进程。

## 知识要点

### 知识要点

1. **进程标识符（`env_id`）**：
   - 每个进程都有一个唯一的标识符，用于区分不同的进程。
   - 在创建新进程时，必须为其分配一个唯一的 `env_id`。
2. **进程控制块（PCB）**：
   - PCB 是操作系统用于管理进程的数据结构，包含进程的状态、优先级、程序计数器、寄存器集合、内存管理信息等。
3. **函数 `mkenvid`**：
   - 用于生成一个新的进程标识符。
4. **函数 `envid2env`**：
   - 通过进程标识符获取对应的进程控制块。

**理解与应用**：平台 ENVX 取低 6 位，完整 env_id 还要比较生成号；envid 为 0 表示 curenv。

## 关联源码

| 文件 | 作用 | 关注符号 |
| --- | --- | --- |
| `lib/env.c` | 题面所涉代码位置 | `static int envid2env(uint32_t envid, struct Env **penv, int checkperm)` |

**内容来源**：原文来自 question.content；原指导 Listing 未随工作区提供，平台按题面及公开语义构造等价示例。

## 实验任务

### 实验任务

1. **实现 `envid2env` 函数**：
   - 在 `env.c` 文件中实现 `envid2env` 函数。
   - 该函数通过给定的 `env_id` 获取对应的进程控制块。
2. **理解进程标识符的作用**：
   - 理解 `env_id` 在进程管理中的作用，以及如何通过 `env_id` 找到对应的 PCB。
3. **测试和验证**：
   - 编写测试代码，验证 `envid2env` 函数的正确性，确保能够正确地通过 `env_id` 获取对应的 PCB。

**实施与验证**：按编号定位 PCB，排除空闲或编号不符的槽位；checkperm 为 1 时按平台契约检查自己或当前进程的父进程，再返回指针。用 E、N 比较查询结果。

## 本平台判题说明

- 判题方式：Tier A 单文件 C 主机端等价 harness，填写 `starter.c` 中成对标记包围的学生填写区：`static int envid2env(uint32_t envid, struct Env **penv, int checkperm)`。
- 宿主以固定宽度整数及数组模拟地址与内存；完整验证仍需本地 MIPS 环境。
- 以下逐字引用 `cases.json.description`；stdin 按空白分词，输出的行内空格敏感。

> 驱动 lib/env.c 的 envid2env()。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..48），随后 n 行：I=调用平台 env_init()（重复打印 I SKIP）；A parent=以 parent 为父进程 id 调 env_alloc，打印「A 返回码 PCB下标 新envid」；U idx=把 curenv 设为 envs[idx]；S idx status=设 env_status（0=ENV_FREE,1=ENV_NOT_RUNNABLE,2=ENV_RUNNABLE）；F idx=释放 envs[idx] 回空闲链表；N idx=打印「N idx env_id env_status env_parent_id」；E envid checkperm=调用学生 envid2env，打印「E 返回码 命中下标」（未命中或指针为空/越界时下标 -1，返回码 -2 即 -E_BAD_ENV）。checkperm 只能 0 或 1，且为 1 时 curenv 必须已设置，否则驱动直接结束。envid 按 uint32 读入，ENVX 取低 6 位，因此超大 envid 是合法输入而非语法错误。mkenvid 语义：envid=(生成号<<7)|下标，生成号从 1 起每次分配递增。

**操作与参数 → 输出**：

- `I` → I 数量 / I SKIP
- `A` parent → A 返回码 下标 envid
- `U` idx → U idx
- `S` idx status → S idx status
- `F` idx → F idx
- `N` idx → N idx env_id status parent
- `E` envid checkperm → E 返回码 下标

**契约片段**：「A 返回码 PCB下标 新envid」；「N idx env_id env_status env_parent_id」；「E 返回码 命中下标」。

## 常见错误

1. 只检查槽位状态而不比较完整编号，旧 ID 仍可能命中。
2. 忽略 checkperm 会允许未授权查询。
3. 把 envid 0 当普通编号或失败时留下旧指针。
