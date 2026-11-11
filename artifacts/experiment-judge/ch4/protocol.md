# 第四章检查驱动输入协议（教师 / 验收用）

本文件是 `artifacts/experiment-judge/ch4/driver.c.inc` 的完整操作符说明。
驱动是平台件，学生只填写「学生填写区」里的函数，不需要自己构造输入；
判题用例由 `cases.json` 给出，期望输出一律由 `verify-experiment-harness.mjs`
真实运行 `reference.c` 得到，再逐条人工复核。

为把单文件源码压在沙箱 65536 字节上限内，驱动源码里只保留操作符骨架，
详细语义集中在本文件。各实验 `content.md` 的「检查驱动与输入协议」一节引用本文件。

## 头部

```
np fm      np = 宿主物理页数（1..NPAGE_MAX=256），fm = 空闲内存上界字节数（<=65536）
n          操作条数（0..MAXOPS=96）
```

`np`/`fm` 不合法（np 为 0 或 > 256、fm > 65536）时驱动立即 `return 0`，不打印任何内容。

## 物理页账本（复核用例时必用）

- 启动期 `boot_kernel_map` 用 bump 分配器从 `fm` 起建内核映射，占 2 页页表；
  `page_init` 把 `ROUND(fm,4096)/4096` 之前的页标记为已用。
  故 `fm=8192` 时前 4 页（0..3）已用，**初始空闲页数 = np - 4**。
  `np=256` → 252；`np=8` → 4；`np=6` → 2。
- 每次 `env_alloc` 占 1 页作页目录，按空闲链表头顺序发出：
  `envs[63].env_cr3=1044480`(页255)、`envs[62]=1040384`(页254)、`envs[61]=1036288`(页253)。
- 首次映射某个 4MB 区间（`PDX(va)` 相同）额外占 1 页作页表。
- `page_alloc` **不**递增 `pp_ref`；`page_insert` 自己递增；`page_remove` 递减到 0 时回收。
- `page_free(pp)` 在 `pp_ref==1` 时是**空操作**（MOS 原样语义），只有 `pp_ref==0` 才真正入空闲链表。

## 操作符

| 操作符 | 参数 | 行为 | 输出 |
| --- | --- | --- | --- |
| `I` | — | `env_init()`；重复 `I` 只打印 `I SKIP` | `I <空闲 PCB 数>` |
| `A` | `parent` | `env_alloc(&e, parent)` | `A <返回码> <下标> <env_id>` |
| `U` | `idx` | `curenv = &envs[idx]`；用户态驱动同时设 `env` | `U <idx>` |
| `S` | `idx st` | 直接置 `env_status`（0=FREE 1=NOT_RUNNABLE 2=RUNNABLE） | `S <idx> <st>` |
| `R` | `idx` | 不在就绪链表则 `LIST_INSERT_HEAD` 挂入 | `R <idx>` |
| `P` | `idx va perm` | `page_alloc` + `page_insert`，真实 backing 一页 | `P <返回码> <物理地址或 4294967295>` |
| `B` | `idx va perm` | 只登记页表项（`PTE_ADDR=0`），构造「已登记未 backing」缺页夹具。**仅 `CH4_TRAP_DRIVER`（4.11）** | `B 0` / `B <错误码>` / `B -3`（页目录非法） |
| `K` | `s a0 a1 a2 a3 a4` | 填好 `KERNEL_SP` 现场（`regs[2]=s`，`regs[4..8]=a0..a4`）后调 `handle_sys()`。**必须恰好 6 个数**；`curenv==NULL` 时拒绝该输入。**`CH4_TRAP_DRIVER`（4.11）下不提供** | `K <$v0 按 int 解读>` |
| `G` | — | 只读当前 `$v0`，不触发系统调用 | `G <值>` |
| `Q` | `idx va` | 驱动自有地址翻译（不依赖学生实现） | `Q <物理页基址>`，无映射 `4294967295` |
| `O` | `idx va` | 读页表项原始值 | `O <pte>`，无页表 `4294967295` |
| `T` | `idx` | 进程控制块摘要 | `T <idx> <env_id> <parent> <status> <pri> <cr3>` |
| `N` | `idx epc v0` | 给 `envs[idx].env_tf` 写可辨认的 `cp0_epc` 与 `regs[2]`($v0)，用来检验 4.8 的「整体复制父进程现场」与「子进程 $v0 归零」 | `N <idx> <epc> <v0>` |
| `J` | `idx` | 读进程现场四个关键字段：`cp0_epc`、`regs[2]`($v0)、`regs[29]`($sp)、`cp0_status` | `J <idx> <epc> <v0> <sp> <status>` |
| `E` | `idx` | 缺页处理函数与 IPC 字段 | `E <idx> <pgfault_handler> <ipc_receiving> <ipc_dstva> <ipc_from> <ipc_value>` |
| `H` | — | 就绪链表头 | `H <下标>`，空链表 `-1` |
| `L` | — | 就绪链表全部下标 | `L <下标...>` |
| `F` | — | 空闲 PCB 链表全部下标 | `F <下标...>` |
| `C` | — | 空闲物理页数 | `C <页数>` |
| `Y` | — | **非用户态**：`lcontext`/`env_pop_tf` 事件日志。**用户态且装配 fork 时改为子进程分支夹具**（见下），两者互斥 | 非用户态：`Y <条数>` 后跟每条 `kind arg cur`（0=lcontext，1=env_pop_tf）；用户态：`Y <fork 返回值> <env 指向的下标>` |
| `Z` | — / `va` | **非用户态**：清空事件日志。**用户态且装配 pgfault 时改为整页内容观察**（见下） | 非用户态：`Z`；用户态：`Z <页基址> <整页字节和> <前 8 字节...>`，未映射 `Z <页基址> -1` |
| `M` | `va` | 把 `va` 写进 `TIMESTACK.cp0_badvaddr` 后调 `page_fault_handler(TIMESTACK)`。**仅 `CH4_TRAP_DRIVER`（4.11）** | `M <处理后该页 pte>`，`curenv` 非法时 `4294967295` |
| `V` | — | `fork()`。**仅 `CH4_FORK_DRIVER`**（4.9/4.10/4.15）；`curenv`/`env` 为空时拒绝 | `V <返回值> <子进程下标或 -1> <env 指向的下标>` |
| `D` | `envid pn` | `duppage(envid, pn)`，`pn < VPN(USTACKTOP)`。**仅 `CH4_DUPPAGE_DRIVER`**（4.9/4.10/4.15） | `D <父进程该页 pte> <子进程该页 pte>` |
| `X` | `va` | `pgfault(va)`。**仅 `CH4_PGFAULT_DRIVER`**（4.13） | `X <处理后该页 pte>` |
| `W` | `dst src n` | `user_memcpy(dst, src, n)`，`n <= BY2PG`。**仅 `CH4_USER_DRIVER`** | `W <返回码>` |

### 两个用户态夹具的必要性（诚实性说明）

- **`Y` 子进程分支夹具**：真实硬件上子进程从 `fork` 返回时 `$v0` 已被内核置 0，故走
  `envid == 0` 分支。宿主的 `syscall_env_alloc` 永远返回非 0 的子进程 envid，
  **该分支本来不可达** —— 不加夹具，4.9 的核心考点（`env` 指向自身 PCB）就判不出来。
  夹具置起 `env-user` 段的 `fork_child_stub` 令这一次 `syscall_env_alloc` 返回 0，
  并先把 `env` 置 `NULL` 作哨兵：正确实现必须把它改回 `curenv` 自己的 PCB（输出下标 = `curenv` 下标），
  漏写赋值留下 `-1`，误用返回的 `envid`（=0）会得到 `0`。只影响这一次调用，
  不改学生代码，也不改父进程分支的任何语义。
- **`Z` 整页内容观察**：`page_alloc` 会把新页清零，若源页也全 0，则「复制了整页」与
  「根本没复制」在页表项与空闲页数上完全同形，4.13 的 `user_memcpy` 那一步就判不出来。
  配合 `W` 从 `va=1073750016`（`UTOP+8192`，映射到 PA 8192 = 启动期第一张页表，
  bump 分配器从 `fm=8192` 起分配）搬入可辨认的非零内容：前四个页表项为 3/4099/8195/12291，
  整页字节和 **108**、前 8 字节 **3 0 0 0 3 16 0 0**。
  注意 `va=UTOP` 本身映射到全零的 PA 0，**不能**用作内容源。

未知操作符、`scanf` 失败、下标 `>= NENV`、`va >= UTOP`（`P`/`B`）一律 `return 0`：
驱动拒绝该输入而不是打印伪造结果，也保证退出码为 0（harness-spec §6）。

## 系统调用号与错误码

`1=mem_alloc 2=mem_map 3=mem_unmap 4=env_alloc 5=set_env_status 8=ipc_recv
9=ipc_can_send 10=set_pgfault_handler 11=yield`，`SYS_MAX=12`，`0/6/7` 为空槽位。

错误码按 `uint32` 补码写进 `$v0`，驱动再按 `int` 打印：
`-2=E_BAD_ENV -3=E_INVAL -4=E_NO_MEM -5=E_NO_FREE_ENV -10=E_NO_SYS -11=E_IPC_NOT_RECV`。

## 源码预算裁剪（65536 字节单文件上限）

沙箱限制的是**源码文件字节数**，不是预处理后的字节数 —— 故 `#ifndef` 守卫不省预算，
必须**物理排除整个片段**。`mk.sh` 据此做三处裁剪，全部是预算裁剪而非语义裁剪：
被考查函数的行为与完整平台逐字一致。

1. **用户态实验（4.9/4.10/4.13/4.15，`CH4_USER_DRIVER`）**：额外装下 syscall 包装库
   （`env-user`）、访存等价件（`env-usermem`）与被考查函数。这些用户代码只用 6 个系统调用，
   从不触发 `yield` 与 IPC，故省掉 `ch3/env-run`、`ch3/env-sched`、`ch3/env-recorder`、
   `ch4/sys-yield`、`ch4/sys-ipc` 五段（约 7.5KB），分发表对应槽位留 `NULL`
   （`handle_sys` 正确判成 `-E_NO_SYS`）。`env-user` 也只提供这 6 个包装。
2. **内核缺页实验（4.11，`CH4_TRAP_DRIVER`）**：`page_fault_handler` 由 `M` 直接调用，
   全程不经系统调用分发，故省掉整个 ch4 系统调用层（调用号 / 9 个 `sys_*` / 分发表 /
   `handle_sys`，约 12KB），驱动的 `K` 操作符同步关闭。该函数只依赖 ch2/ch3 的页表与
   物理页设施。裁剪后 starter.c 仅 46869 字节。
3. **`ch3/env-recorder` 独立成段**：`tf_in_range`/`lcontext`/`env_pop_tf` 三个记录替身
   只被 `env_run` 调用，从 `env-pcb` 拆出后才能在用户态模式下整段排除。
   `rec_*` 数组与 `MAXREC` 仍留在 `env-pcb`（复位与 `Y` 事件日志要用）。

四个用户态操作符各自门控（`CH4_FORK_DRIVER` / `CH4_DUPPAGE_DRIVER` / `CH4_PGFAULT_DRIVER`），
未装配对应函数的实验里该操作符按未知输入拒绝 —— 否则会出现隐式声明或
「声明了 static 却始终没定义」的 `-Werror` 编译错误。

### 裁剪的输出中立性证明

上述改动触及共享片段，故对**全部既有 harness** 做了协调重构建
（`node artifacts/experiment-judge/sweep-rebuild.mjs`，24 个实验串行）：
改动前后 32 个既有 `judge-cases.json` 的 md5 **逐字节相同**，
唯一差异是本轮新增的 4.9/4.10/4.13/4.15 四个文件。