# 实验 3.1 初始化进程控制块数组

## 实验背景

**题目原文（question.content）**

### 任务描述

在操作系统实验中，需要为进程控制块（PCB）数组分配内存。具体任务包括修改 `pmap.c` 和 `mips_vm_init` 函数，以分配和管理进程控制块数组 `envs`。`envs` 数组包含 `NENV` 个 `Env` 结构体成员，并且需要在系统启动时分配内存。

在创建进程前，内核须为全部 PCB 分配空间，并建立用户可读映射；空闲链表由后续实验管理。

## 知识要点

### 知识要点

1. **进程控制块（PCB）**：
   - PCB 是系统为了管理进程设置的一个专门的数据结构，用于记录进程的外部特征和描述进程的运动变化过程。
   - PCB 是系统感知进程存在的唯一标志，进程与 PCB 是一一对应的。
   - PCB 通常包含以下信息：
     - `env_tf`：保存的寄存器
     - `env_link`：空闲进程链表
     - `env_id`：唯一环境标识符
     - `env_parent_id`：父进程标识符
     - `env_status`：进程状态
     - `env_pgdir`：页目录的物理地址
     - `env_cr3`：页目录的虚拟地址
     - `env_sched_link`：调度状态进程链表
     - `env_pri`：进程优先级
2. **进程状态**：
   - `ENV_FREE`：进程不活动，处于进程空闲链表中。
   - `ENV_NOT_RUNNABLE`：进程处于阻塞状态，等待条件满足后变为可调度状态。
   - `ENV_RUNNABLE`：进程处于就绪状态，等待被调度运行。
3. **内存分配**：
   - `envs` 数组包含 `NENV` 个 `Env` 结构体成员，需要在系统启动时分配内存。
   - 使用 `pmap.c` 中的内核态函数进行段映射，将 `envs` 数组映射到 `UENVS` 区域。

分配与映射长度均需页对齐；PADDR 将内核可访问地址转换为物理地址。原文对 `env_pgdir` 与 `env_cr3` 地址类型的描述予以保留；平台中前者是内核可访问的页目录指针，后者是页目录物理地址。

## 关联源码

| 文件 | 作用 | 符号 |
| --- | --- | --- |
| `mm/pmap.c` | 启动分配与映射 | `mips_vm_init`、`alloc`、`boot_map_segment` |
| `mm/pmap.c` | 官方内存自检 | `physical_memory_manage_check`、`page_check` |

**内容来源**：工作区 `originalCode` 包含真实 `mm/pmap.c`，`answer` 包含 `mips_vm_init` 片段，平台据此适配。原源码中的两个官方自检可用于理解内存管理；本题驱动不直接执行它们。

## 实验任务

### 实验任务

1. **修改 `pmap.c/mips_vm_init` 函数**：
   - 修改 `mips_vm_init` 函数，使其为 `envs` 数组分配内存。
   - 参考 `pmap.c` 中已经写过的 `pages` 数组空间的分配方式。
2. **分配 `envs` 数组空间**：
   - `envs` 数组包含 `NENV` 个 `Env` 结构体成员。
   - 使用 `pmap.c` 中的内核态函数进行段映射，将 `envs` 数组映射到 `UENVS` 区域。

先建立页目录，再分配并映射 pages，最后分配 `NENV` 个 Env 并将向上取整的长度映射到 UENVS。用 E、P 检查页数与权限，用 Q 检查首尾及范围外地址。

## 本平台判题说明

Tier A 主机端等价 C harness：在 `starter.c` 的学生填写区实现 `static void mips_vm_init(void)`。宿主用固定宽度整数和数组模拟物理内存及页表，分配失败返回空指针；完整验证仍需本地 MIPS 环境。

以下为 `cases.json.description` 原文，stdin 按空白分词，行内空格敏感：

> 驱动 mm/pmap.c 的 mips_vm_init()（真实北航参考解的宿主等价版）。stdin 首行 npage freemem：npage 是物理页数（1..256），freemem 是启动期空闲内存起始物理地址（字节，0..65536，故意允许非页对齐以考查 alloc 的 ROUND 对齐）。次行操作数 n（0..32），随后 n 行每行一个操作码：V=调用一次 mips_vm_init()（重复 V 打印 V SKIP，不再调用）；E=查询 envs 数组与 UENVS 段映射；P=查询 pages 数组与 UPAGES 段映射；Q va=查询虚拟地址 va 的物理地址。输出：V 行为「V freemem mCONTEXT 页目录物理地址」；E 行为「E envs物理地址 UENVS连续映射页数 UENVS首页页表项低12位权限 UENVS首页物理地址 UENVS末页物理地址」；P 行为「P pages物理地址 UPAGES连续映射页数 UPAGES首页权限」；Q 行为「Q 物理地址」。指针为空/野指针、无映射、页表物理地址越界一律打印 4294967295（~0）。所有地址与页数均为十进制 uint32_t。

**操作与参数**：

- `V`：初始化，重复时输出 `V SKIP`。
- `E`：检查 envs 地址与 UENVS 映射。
- `P`：检查 pages 地址与 UPAGES 映射。
- `Q` va：查询 va 对应的物理地址。

**输出格式**：「V freemem mCONTEXT 页目录物理地址」；「E envs物理地址 UENVS连续映射页数 UENVS首页页表项低12位权限 UENVS首页物理地址 UENVS末页物理地址」；「P pages物理地址 UPAGES连续映射页数 UPAGES首页权限」；「Q 物理地址」。4294967295 为无效地址哨兵。

## 常见错误

1. 未将数组长度 `ROUND` 到整页，映射函数会拒绝该段。
2. 漏设 `PTE_R`，权限与要求不符。
3. 将内核指针直接当成物理地址，或混淆 UENVS 和 UPAGES。
4. 未检查末页、范围外地址和非对齐 freemem。
