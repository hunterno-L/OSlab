# Agent A 工作报告：第三章 11 个 + 第四章 14 个 harness 与 25 篇 content.md

> 本文件是**活文档**：每闭环一个实验就追加一行台账。若上下文中断，主 agent 可凭本文件精确接续。
> 分支 `feature/ai-upgrade`。**未 commit / 未 push / 未 merge / 未部署**。

## 0. 当前状态快照（每次更新）

- 已完成（三门全过）：**3.1**（`1908900051330306049`）、**3.2**（`1930154686162456577`）、**3.3**（`1930154848414912513`）、**3.4**（`1930154980048949249`）
- 探针集已扩到 **54 条**（新增 19 条 `s3-*` 三数字头探针，覆盖 3.4 的 `npage freemem stale` 语法）；
  3.1–3.4 一次性跑完：**648 runs / 0 failures，退出码 0**
- 进行中：**3.5**（`1930155119123681282`，`env_alloc`）
- 未开始：3.6–3.10、3.14（6 个）、第四章 4.2–4.15（14 个）、25 篇 `content.md`
- 归属 B、我未触碰：`1930155905954140162`(3.12)、`1930156027966443521`(3.13)、`1930156360117571585`(4.1)
- 批次根 `experiment-maintenance.json`：归主 agent，我未创建

## 1. 进度台账（25 行目标，逐行追加）

| # | 实验 | id | 章节 | 目标函数 | verify 退出码 | probe 退出码 | 用例数 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 3.1 | 1908900051330306049 | 3 | `static void mips_vm_init(void)` | 0 | 0（105 runs / 0 fail） | 5 | 专用 3 片段基座（指针型全局），与 ch2 静态数组基座冲突，见 §3.1 |
| 2 | 3.2 | 1930154686162456577 | 3 | `static void env_init(void)` | 0 | 0 | 5 | NENV 固定 64，用 `L k` 观察窗口替代计划的「n 个」，见 §6 偏差 1 |
| 3 | 3.3 | 1930154848414912513 | 3 | `static int envid2env(uint32_t, struct Env**, int)` | 0 | 0 | 5 | 用例 3 的 envid 318→319 已修正并重跑，starter 现在 5/5 失败 |
| 4 | 3.4 | 1930154980048949249 | 3 | `static int env_setup_vm(struct Env *e)` | 0 | 0 | 5 | 新增 `stale` 夹具与 `B` 操作；wrong 的「整表拷贝」缺陷不可观测，已换成可观测的 off-by-one |
| 5 | 3.5 | 1930155119123681282 | 3 | `static int env_alloc(struct Env**, uint32_t)` | 0 | 0 | 5 | 新增 `D`（污染 PCB）与 `P`（IPC 字段）操作，使「复用槽位必须重新初始化」可判 |
| 6 | 3.6 | 1930155245821022209 | 3 | `static int load_icode_mapper(void*, uint32_t, uint32_t, int, struct Env*)` | 0 | 0 | 5 | `seg_binary` 扩到 4 页；`B` 位置图样 + `D` 读字校验首页偏移/末页截断 |
| 7 | 3.7 | 1930155360556208129 | 3 | `static void load_icode(struct Env*, uint32_t)` | 0 | 0 | 5 | 段表由 `G`/`S` 驱动；`X` 污染 sp/epc；栈页 pp_ref 统一为 1 |
| 8 | 3.8 | 1930155484338507777 | 3 | `env_create` / `env_create_priority` | 进行中 | — | — | 进行中 |
| 9 | 3.9 | 1930155681017810946 | 3 | `ENV_CREATE_PRIORITY` 宏应用（init.c） | — | — | — | 未开始 |
| 10 | 3.10 | 1930155792691154945 | 3 | `static void env_run(struct Env *e)` | — | — | — | 未开始；`lcontext`/`env_pop_tf` 用宿主记录替身 |
| 11 | 3.14 | 1930156143783759874 | 3 | `static void sched_yield(void)` | — | — | — | 未开始 |
| 12 | 4.2 | 1930156493454495745 | 4 | `handle_sys` | — | — | — | 未开始 |
| 13 | 4.3 | 1930156605618573314 | 4 | `sys_mem_alloc` | — | — | — | 未开始 |
| 14 | 4.4 | 1930156740377366529 | 4 | `sys_mem_map` | — | — | — | 未开始 |
| 15 | 4.5 | 1930156844614209538 | 4 | `sys_mem_unmap` | — | — | — | 未开始 |
| 16 | 4.6 | 1930156972624367618 | 4 | `sys_yield` | — | — | — | 未开始 |
| 17 | 4.7 | 1930157095085461505 | 4 | `sys_ipc_recv` / `sys_ipc_can_send` | — | — | — | 未开始 |
| 18 | 4.8 | 1930157212584693762 | 4 | `sys_env_alloc` | — | — | — | 未开始 |
| 19 | 4.9 | 1930157404163723265 | 4 | `fork` 子进程分支 | — | — | — | 未开始 |
| 20 | 4.10 | 1930157516164222977 | 4 | `duppage` | — | — | — | 未开始 |
| 21 | 4.11 | 1930157629511094274 | 4 | `page_fault_handler` | — | — | — | 未开始 |
| 22 | 4.12 | 1930157740261691394 | 4 | `sys_set_pgfault_handler` | — | — | — | 未开始 |
| 23 | 4.13 | 1930157867273605121 | 4 | `pgfault` | — | — | — | 未开始 |
| 24 | 4.14 | 1930157984642813953 | 4 | `sys_set_env_status` | — | — | — | 未开始 |
| 25 | 4.15 | 1930158086405017602 | 4 | `fork` 父进程分支 | — | — | — | 未开始 |

## 2. 第四章原始素材可得性侦察（实测字节数）

路径 `artifacts/team-handoff/2026-10-17/A-开工包/reference/experiments/<id>/`。
**14 个第四章实验的 `原始代码.txt` 与 `参考解.txt` 全部为 0 字节**，只有 `题面.md` 有内容。

| 实验 | id | 原始代码.txt | 参考解.txt | 题面.md |
| --- | --- | --- | --- | --- |
| 4.2 | 1930156493454495745 | 0 | 0 | 1295 |
| 4.3 | 1930156605618573314 | 0 | 0 | 1020 |
| 4.4 | 1930156740377366529 | 0 | 0 | 1038 |
| 4.5 | 1930156844614209538 | 0 | 0 | 1047 |
| 4.6 | 1930156972624367618 | 0 | 0 | 994 |
| 4.7 | 1930157095085461505 | 0 | 0 | 1225 |
| 4.8 | 1930157212584693762 | 0 | 0 | 1178 |
| 4.9 | 1930157404163723265 | 0 | 0 | 995 |
| 4.10 | 1930157516164222977 | 0 | 0 | 879 |
| 4.11 | 1930157629511094274 | 0 | 0 | 1076 |
| 4.12 | 1930157740261691394 | 0 | 0 | 966 |
| 4.13 | 1930157867273605121 | 0 | 0 | 966 |
| 4.14 | 1930157984642813953 | 0 | 0 | 872 |
| 4.15 | 1930158086405017602 | 0 | 0 | 982 |

第三章同样：除 3.1（`1908900051330306049` 有真实 `pmap.c` 原始代码 + 真实 `mips_vm_init` 参考解）外，
3.2/3.3/3.4/3.5/3.6/3.7/3.8/3.9/3.10/3.14 共 **10 个**的 `原始代码.txt`、`参考解.txt` 均为 0 字节
（题面字节数：3.2=1326、3.3=1305、3.4=1138、3.5=1669、3.6=1608、3.7=1572、3.8=1375、3.9=1038、3.10=1315、3.14=1233）。

→ 这 **24 个实验**的 `content.md` 与 harness 注释必须带诚实标注：
「原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例」。

## 3. 新增环境片段清单与宿主等价适配

目录 `artifacts/experiment-judge/ch3/`（我拥有，可改）；`ch2/**` 与 `scripts/**` 只读复用未修改。

### 3.1 第三章片段

| 片段 | 用途 | 宿主等价适配与理由 |
| --- | --- | --- |
| `env-boot-pre.c.inc` | 仅 3.1 用：头文件、`MOS_UNUSED`、`BY2PG`/`PDMAP`/`PTE_V`/`PTE_R`、`PTE_ADDR`/`PDX`/`PTX`/`ROUND`/`ROUNDDOWN`/`bzero`、`Pde`/`Pte`、`LIST_*`、`struct Page`、`NPAGE_MAX 256`、`static unsigned char phys[]` | 物理地址 == 数组下标，`KADDR(pa)=phys+pa`。真实 MOS 用 `KSEG0` 线性偏移，宿主无 MMU，只能用数组基址等价 |
| `env-pcbdef.c.inc` | 共享常量 + `struct Trapframe` + `struct Env` | `Trapframe` 只保留 `regs[32]`+4 个 CP0 字段（真实 MIPS 还有 `cp0_*` 若干，与本实验无关）；地址一律 `uint32_t` 规避 MinGW LLP64；`ENV_USER_SR=0x1000100c` 按公开 SR 位组合构造（工作区无 `trap.h`） |
| `env-boot-vm.c.inc` | 仅 3.1 用：指针型全局 `pages/envs/boot_pgdir`、`npage/freemem/maxpa/mCONTEXT`、真实 `alloc`/`boot_pgdir_walk`/`boot_map_segment`、驱动防护 | `alloc` 溢出时返回 `NULL` 而非 `panic`（沙箱非零退出=RE）；`panic` 是非终止空操作 |
| `env-pcb.c.inc` | 3.2–3.14 + 第四章共享 PCB 环境 | `TIMESTACK`/`KERNEL_SP` 定义为 `struct Trapframe*` 宏而非 `uint32` 地址（避免 64 位指针截断）；`lcontext`/`env_pop_tf` 为**记录替身**（记录调用时的 `curenv` 下标，使 `env_run` 四步顺序可真实判定）；`kernel_pde_copied` 跳过 `PDX(UVPT)`（自映射合法覆盖该槽，满分 767） |
| `env-insert.c.inc` | 平台 `page_insert`/`page_remove` | 沿用 2.7 已验证语义（`page_insert` 负责 `pp_ref++`），加 `pgdir_ok`/`page_in_range`/`pte_in_range` 防护 |
| `env-init.c.inc` | 平台 `env_init`（3.2 的填写目标，其它实验作平台件） | — |
| `env-envid2env.c.inc` | 平台 `envid2env` | — |
| `env-setupvm.c.inc` | 平台 `env_setup_vm`（3.4 的填写目标） | — |
| `env-envalloc.c.inc` | 平台 `env_alloc` | `env_setup_vm` 失败时**不**把 PCB 摘出空闲链表（与真实 MOS 一致） |
| `env-image.c.inc` | 宿主 ELF 模型：`seg_table`/`seg_n`/`elf_entry`/`seg_binary`、`load_elf` | 真实 MOS 解析 ELF 头；宿主用显式段表等价（沙箱内无真实 ELF 文件），段权限/入口/内容全部由 stdin 驱动 |
| `env-mapper.c.inc` | 平台 `load_icode_mapper` | 不额外 `pp_ref++`（`page_insert` 已负责，见 2.7 契约） |
| `env-loadicode.c.inc` | 平台 `load_icode` | — |
| `env-create.c.inc` | `env_create`/`env_create_priority` + `ENV_CREATE*` 宏 + `binary_user_A/B/C` | `binary==NULL \|\| size==0` 直接返回（真实 MOS 会 panic） |
| `env-run.c.inc` | 平台 `env_run` | 用 `TIMESTACK` 保存旧现场；`lcontext`/`env_pop_tf` 记录替身 |
| `env-sched.c.inc` | 平台 `sched_yield` | 环形遍历步数上限 `NENV`，跳过非 `ENV_RUNNABLE`；`left=max(1,env_pri)` 实现「优先级即时间片」 |
| `probes.json` | 35 条健壮性探针 | 见 §5 |
| `mk.sh` | 拼装 + 派生 + 验证助手 | 3.1 走专用基座；其余走 ch2 五片段 + ch3 pcbdef/pcb/insert + 额外片段 + body |

### 3.2 实测结构体尺寸（用于 ROUND 计算）

`sizeof(struct Env)=232`、`sizeof(struct Page)=24`、`sizeof(struct Trapframe)=144`；
`NENV*232=14848` → `ROUND(...,BY2PG)=16384`（4 页）。

### 3.3 第四章片段（待建）

计划新增 `ch4/env-syscall-table.c.inc`（`sys_call_table` 函数指针表 + 9 个 `SYS_*` 号）、
`ch4/env-ipc.c.inc`、`ch4/env-cow.c.inc`、`ch4/env-fork.c.inc`、`ch4/probes.json`。
（尚未创建，完成后在此补表。）

## 4. 诚实标注清单（原文缺失）

- 第三章 10 个：3.2、3.3、3.4、3.5、3.6、3.7、3.8、3.9、3.10、3.14
- 第四章 14 个：4.2–4.15 全部
- 另：工作区缺 `include/mmu.h`、`include/env.h`、`include/trap.h`、`lib/env.c`、`lib/elfloader.c`
  （仅在 `pmap.c` 注释中被引用）。因此地址布局常量（`UTOP`/`UPAGES`/`UENVS`/`UVPT`/`UXSTACKTOP`）
  与 CP0 SR 位组合均为**按公开 MOS 语义构造的宿主等价值**，已在 `env-pcbdef.c.inc` 顶部注明。

## 5. 三道门与探针

- 门 1：`node scripts/verify-experiment-harness.mjs <dir>`，退出码 0；日志 `artifacts/experiment-judge/verify-ch3.log` / `verify-ch4.log`
- 门 2：`node scripts/probe-harness-robustness.mjs <dir> --probes artifacts/experiment-judge/ch3/probes.json`，退出码 0；日志 `probe-ch3.log` / `probe-ch4.log`
- 门 3：人工逐用例覆盖度复核（§5.1），`NOTE: n 个用例为纯边界用例` 必须逐个解释

`ch3/probes.json` 共 35 条：empty / zeros / huge-unsigned / negative / negative-count /
op-count-overflow / op-count-huge / unknown-op / truncated-op-alloc / truncated-op-args /
truncated-header / truncated-count / garbage / garbage-ops / out-of-range-envid /
out-of-range-envid-small / out-of-range-index / out-of-range-index-huge / out-of-range-va /
out-of-range-va-kernel / bad-status / bad-status-negative / perm-overflow / perm-negative /
size-overflow / size-unaligned / double-free-env / free-unallocated-env / exhaust-envs /
exhaust-pages / repeat-vm-init / run-unallocated / yield-empty-queue / ipc-self-send /
zero-npage-with-ops。

## 6. 与计划 Task 3 / Task 4 表格的偏差

1. **3.2 的 n**：计划写「首行 n（≤NENV），序列为 n-1..0」。实际 `env_init` 无参数、`NENV` 固定 64，
   无法按 n 缩放。改为首行仍是 `npage freemem`（内存规模），用 `L k` 观察窗口打印空闲链表前 k 个下标。
   判定锚（头插 → 降序 63..0）不变。
2. **3.1 基座**：真实 `mips_vm_init` 给指针型全局赋值，与 ch2 的静态数组基座不兼容，
   故 3.1 单独用 `env-boot-pre` + `env-pcbdef` + `env-boot-vm` 三片段。
3. **3.10 替身**：`lcontext`/`env_pop_tf` 为宿主记录函数（已在片段与 body 注释写明），
   但记录里带调用时刻的 `curenv` 下标，四步**顺序**仍被真实校验。
4. （后续偏差继续追加）

## 7. 逐实验用例明细与覆盖度复核

（每闭环一个实验追加一节：目标函数 / 5 个用例名 / 参考输出 / starter 与 wrong 各在哪些用例失败 /
纯边界用例逐条解释。）

### 7.1 实验 3.1 `1908900051330306049` — `mips_vm_init`

- 门 1 = 0，门 2 = 0（3 变体 × 35 探针 = 105 runs / 0 failures），无 NOTE。
- 用例：
  1. 官方锚：64 页物理内存，envs 占 4 页并以 `PTE_R` 映射到 `UENVS`
  2. `freemem` 非页对齐：`alloc` 必须先 `ROUND` 到 `BY2PG` 再分配
  3. 256 页：`pages` 数组 6144 字节需 `ROUND` 成 2 页映射到 `UPAGES`
  4. 16 页小内存 + `freemem` 从 0 起：`pages` 数组不足一页仍映射 1 页
  5. `UENVS` 逐页边界：第 4 页仍映射、第 5 页必须无映射
- starter 5/5 失败；wrong 5/5 失败。
- wrong.c 缺陷：Step 3 漏 `ROUND`（`envs` 只按 14848 字节而非 16384 推进 `freemem`）+ 用 `PTE_V` 而非 `PTE_R`。
- 参考输出与手算 boot 分配轨迹逐项吻合（`freemem 40960`、`envs pa 20480`、4 页、perm 3）。

### 7.2 实验 3.2 `1930154686162456577` — `env_init`

- 门 1 = 0，门 2 = 0（54 探针），无 NOTE。
- 用例：①官方锚（前 8 个 63..56）②全量 64 个降序 ③连续两次 alloc 取 63、62
  ④`env_free` 头插使 63 回表头 ⑤只观察表头 1 个 + 三次分配后长度与剩余顺序。
- starter 5/5 失败；wrong 5/5 失败。
- wrong.c 缺陷：降序循环 + 头插 → 遍历顺序整体反转为 0..63。
- 用例 1 名称已修正（原名写「7..0」与实际 NENV=64 不符）。

### 7.3 实验 3.3 `1930154848414912513` — `envid2env`

- 门 1 = 0（用例 3 修正后已重跑），门 2 = 0（54 探针），无 NOTE。
- 用例：①`envid=0` 返回 curenv + 生成号不符拒绝 ②两进程各自命中 + `~0`/裸下标不得误命中
  ③槽位复用（旧 191 失效、新 319 命中）④checkperm（自己/父进程放行，无关拒绝）
  ⑤`ENV_FREE` 拒绝、非 FREE 放行。
- starter 5/5 失败；wrong 5/5 失败（wrong.c 缺 `env_id` 相等校验 + 缺 checkperm）。
- **已修的真实用例设计缺陷**：用例 3 原写 `E 318 0`，但 `mkenvid` 第二次给 `(2<<7)|63=319`；
  318 的低 6 位是 62（FREE），参考解返回 -2，导致 **starter 也能通过该用例**，声称的边界没被覆盖。
  已改为 `E 319 0` 并改名。**需重跑 verify 让 judge-cases.json 反映修正后的输入。**

### 7.4 实验 3.4 `1930154980048949249` — `env_setup_vm`

- 门 1 = 0，门 2 = 0（54 探针），无 NOTE。
- stdin 语法：首行 `npage freemem stale`，次行操作数，操作 `I / A / V idx / W idx / Q idx va / B va / C`。
- 5 个用例与参考输出（全部由本地 gcc 真实运行导出）：
  1. **官方锚**：`256 8192 0` + `I,A,W 63` →
     `I 64 / A 0 63 / W 63 1044480 1044480 1 767 1044483 1`
  2. **两进程独立页目录**：`I,A,A,W 63,W 62` →
     `… W 63 1044480 … / W 62 1040384 1040384 1 767 1040387 1`
  3. **不经 env_alloc 直接建页目录 + 地址翻译**：`I,V 0,W 0,Q 0 UTOP,Q 0 UENVS,Q 0 0,Q 0 2140143616` →
     `I 64 / V 0 0 / W 0 1044480 1044480 1 767 1044483 1 / Q 0 / Q 8192 / Q 4294967295 / Q 8192`
     （最后一个 `Q` 走 UVPT 自映射窗口读自己的页目录第 256 项，是自映射是否真的生效的强判据）
  4. **拷贝范围下界**：`256 8192 1` + `I,B 4194304,A,Q 63 4194304,W 63` →
     `I 64 / B 4096 / A 0 63 / Q 4294967295 / W 63 1044480 1044480 1 767 1044483 1`
     `B 4096` 先证明 boot_pgdir 里确有这条用户态映射（避免零区分度），`Q ~0` 证明它没被拷进新进程。
  5. **物理页耗尽**：`8 8192 0` + `I,A×5,W 60,W 59,C` →
     `I 64 / A 0 63 / A 0 62 / A 0 61 / A 0 60 / A -4 -1 / W 60 16384 16384 1 767 16387 1 / W 59 4294967295 0 0 4294967295 4294967295 -1 / C 0`
- starter 5/5 失败；wrong 5/5 失败。
- wrong.c 三个缺陷（均可观测）：①漏 `p->pp_ref++`（W 末字段 0 vs 1）
  ②内核段拷贝下界写成 `PDX(UTOP)+1`（项数 766 vs 767，且 `Q idx UTOP` 变 ~0）③漏 UVPT 自映射（UVPT 项 0 vs `cr3|3`）。
- **覆盖度复核**：Step 1 由用例 1/5 的 `pp_ref` 与 `-E_NO_MEM` 覆盖；Step 2 由 `W` 的 pa/cr3/一致性三字段覆盖；
  Step 3 由项数 767 + `Q UTOP`/`Q UENVS`/`Q 0` + 用例 4 的下界夹具覆盖；Step 4 由 UVPT 项 + 用例 3 的 UVPT 窗口翻译覆盖。
  无「纯边界且零区分度」用例：用例 4 的 `B` 操作专门用来证明夹具存在。
- **手算与实测逐项吻合**：`boot_kernel_map` 用 bump 分配器在 pa 8192/12288 建两张页表 → `freemem` 16384 →
  `page_init` 把页 0..3 标占用、页 4..255 头插 → 首次 `page_alloc` 得页 255（pa 1044480）。
  `stale=1` 时多一张页表（pa 16384）→ `freemem` 20480 → 页 0..4 占用。
  `npage=8` 时只剩页 4..7 共 4 页，第 5 次分配必然 `-E_NO_MEM`。

### 7.5 实验 3.5 `1930155119123681282` — `env_alloc`

- 门 1 = 0，门 2 = 0（60 探针 × 3 变体；3.1–3.5 合计 900 runs / 0 failures），无 NOTE。
- stdin：首行 `npage freemem`，操作 `I / A parent / N idx / P idx / D idx / F idx / U idx / L k / W idx / C`，MAXOPS 80。
- 5 个用例：
  1. **官方锚**（`256 8192`，`I,A 0,N 63,P 63,L 4,C`）→
     `I 64 / A 0 63 191 / N 63 191 0 1 1 1073741824 268439564 0 0 1 1 / P 63 0 0 0 0 / L 62 61 60 59 / C 251`
     `N` 的 11 个字段把 id/parent/status/pri/sp/sr 与 KUc/IEc/KUp/IEp 四个位全部展开，SR 组合写错立刻可见。
  2. **parent_id 透传 + mkenvid 递增**（`A 0 / A 191 / A 318`）→ envid 191/318/445，parent 0/191/318。
  3. **env_setup_vm 失败**（`8 8192`，5 次 `A 0`）→ 前 4 次成功、第 5 次 `A -4 -1 0`，
     `L 59 58 57 56`（PCB 仍在表头）、`N 59` 全 0、`C 0`。
     wrong 因先摘链再失败，`L 58 57 56 55` —— 泄漏一个 PCB 槽位被抓到。
  4. **空闲 PCB 耗尽**（`256 8192`，65 次 `A 0`）→ 64 次成功（idx 63→0，envid 191→8192），
     第 65 次 `A -5 -1 0`，`L`（空）、`C 188`。
  5. **槽位复用 + 污染夹具**（`I,A 0,F 63,D 63,A 7,N 63,P 63,L 3`）→
     `A 0 63 319 / N 63 319 7 1 1 1073741824 268439564 0 0 1 1 / P 63 0 0 0 0 / L 62 61 60`。
     `D` 把 Trapframe 全寄存器设 0xDEADBEEF、sr 设 0xFFFFFFFF、pri=99、status=RUNNABLE、
     pgfault=12345、ipc=1/777/888；只有真正 memset + 复位才能通过。
- starter 5/5 失败；wrong 在 1/2/3/5 失败（用例 4 两者输出相同，属纯耗尽边界，由 starter 的 64 次失败提供区分度）。
- wrong.c 四缺陷：①status=ENV_RUNNABLE ②sr 置 KUc|IEc 而非 KUp|IEp（268439555 vs 268439564）
  ③先 LIST_REMOVE 再 env_setup_vm ④不 memset Trapframe、不复位 pgfault/IPC。
- **覆盖度复核**：Step 1 由用例 4 覆盖；Step 2 由用例 3 覆盖（含「失败不摘链」这一真实边界）；
  Step 3 由用例 1/2/5 的 id/parent/status/pri 覆盖；Step 4 由用例 1 的 sp/sr 与用例 5 的污染夹具覆盖；
  Step 5 由用例 1/3/5 的 `L` 与 `A` 的下标覆盖。无零区分度用例。

### 7.6 实验 3.6 `1930155245821022209` — `load_icode_mapper`

- 门 1 = 0，门 2 = 0（69 探针；3.1–3.6 合计 1242 runs / 0 failures），无 NOTE。
- stdin：首行 `npage freemem`，操作 `I / A / U idx / B / M va size perm / Q idx va / P idx va / D idx va / R idx va / C`，MAXOPS 48。
  `B` 把镜像缓冲填成 `seg_binary[i] = i & 0xFF`（位置相关图样），`D` 读回 32 位小端字，
  因此「首页跳过页内偏移」「末页只拷剩余字节」都能被逐字节判定，而不是只看页数。
- 5 个用例与关键参考输出：
  1. **整页对齐单段**（`M 4096 4096 2`）→ `M 0 / Q 1040384 / D 50462976 / R 1 / P 1040387 / C 249`
     （`P = pa|3` 因为平台 `page_insert` 会再或上 `PTE_V`；`C 249` = 252 − 1 页目录 − 1 数据页 − 1 页表页）
  2. **非对齐 va=6144、size=8192 跨三页** → `Q 1040384 / Q 1032192 / Q 1028096 / Q ~0`、
     `D 6144 = 50462976`（镜像第 0 字节确实落在 va 处）、`D 4096 = 0`（首页前 2048 字节是清零区）、
     `D 14332 = 4294901244 = 0xFFFEFDFC`（镜像第 8188..8191 字节）、`D 14336 = 0`（段尾之后保持清零）、
     `R 1 / C 247`。
  3. **size=0 不耗页 + perm 透传** → `M 0 / C 251 / M 0 / P 1040389 / Q 1040384 / C 249`
  4. **物理页耗尽**（`8 8192`，`M 4096 16384 3`）→ `M -4 / C 0 / Q 24576 / Q 16384 / Q ~0 / Q ~0`
     （4 页里 1 页作页目录、1 页作页表，只剩 2 页数据页，第 3 页分配失败）
  5. **同一 va 重复映射** → `R 1 / R 1 / Q 1032192 / R 1 / C 248`，
     旧页 `pp_ref` 归零被 `page_free` 回收、新页仍为 1，双重计数立刻暴露。
- starter 5/5 失败；wrong 在 1/2/5 失败（3/4 两者输出相同，属纯边界，由 starter 提供区分度）。
- wrong.c 三缺陷：①首页目的偏移恒为 0（镜像整体前移 off 字节）②`page_insert` 之外再 `p->pp_ref++`
  ③`end` 用 `ROUNDDOWN`（段尾不足一页的部分整页丢失）。
- **本实验抓到参考解自身的真实缺陷（见 §8 缺陷 9）**：初版 mapper 把首页内容 memcpy 到页首，
  等价于把整段镜像前移 `off` 字节。用例 2 的实测 `D` 值与手算相反才暴露出来。
  已修正为 `dst = (v == start) ? off : 0`，平台片段 `env-mapper.c.inc` 同步修正（3.7 会复用它）。
  修正后 5 个用例的实测值与手算逐项吻合。

### 7.7 实验 3.7 `1930155360556208129` — `load_icode`

- 门 1 = 0，门 2 = 0（76 探针；3.1–3.7 合计 1596 runs / 0 failures），无 NOTE。
- stdin：首行 `npage freemem`，操作 `I / A / U idx / B / G / S va size perm / L entry / X idx / N idx / Q / P / D / R / C`，MAXOPS 48。
  `G` 清段表、`S` 追加段（≤8），`L entry` 调学生 `load_icode`；`X idx` 把 sp 污染成 0xDEADBEEF、
  cp0_epc 污染成 0x12345678，使「必须真正重写这两个字段」可判（否则 `env_alloc` 预设的 sp 会让学生漏写也通过）。
- 5 个用例与关键参考输出：
  1. **官方锚**（单段 `S 4096 4096 3`，`L 4096`）→
     `N 63 4096 1073741824 / Q 1040384 / D 50462976 / Q 1032192 / P 1032195 / R 1 / C 247`
     栈页 pa 1032192、PTE = pa|3、pp_ref 1。
  2. **多段 + 非对齐 + 入口落在段中间**（`S 6144 8192 3`、`S 32768 4096 2`，`L 8192`）→
     `N 63 8192 … / Q 1040384 / D 50462976 / Q 1024000 / D 50462976 / P 1024003 / Q 1019904 / C 244`
     两段都落在 PDX 0（PDMAP=4MB），共用一张页表，故 C 只减 1 而非 2 —— 已按实测核算。
  3. **零段镜像**（`G` 后直接 `L 0`）→ `N 63 0 1073741824 / Q 1040384 / P 1040387 / D 0 / D 0 / R 1 / C 249`
     栈页全清零；`D 63 1073741820`（栈页最后一个字）也是 0。
  4. **load_elf 失败必须立即返回**（`8 8192`，`S 4096 12288 3`）→
     `N 63 0 1073741824 / Q 24576 / Q 16384 / Q ~0 / Q ~0 / C 0`
     cp0_epc 保持 0、栈页未映射；wrong 因忽略返回值而把 cp0_epc 设成 4096，被抓到。
  5. **entry 不裁剪 + 污染夹具**（`X 63` 后 `L 4294967295`）→
     `N 63 4294967295 1073741824 / R 1 / R 1 / C 247`。
- starter 5/5 失败；wrong 5/5 失败。
- wrong.c 四缺陷：①忽略 `load_elf` 返回值 ②栈页映射到 `USTACKTOP` 而非 `USTACKTOP-BY2PG`
  ③额外 `p->pp_ref++` ④漏设 `regs[29]`。
- **覆盖度复核**：Step 1 由用例 1/2/4 覆盖（含失败即停这一真实边界）；Step 2 由用例 1/2/5 覆盖
  （含 entry=0、entry 在段中间、entry=~0 三个取值）；Step 3 由用例 1/3/5 的 Q/P/R/N 覆盖。
  用例 3 是「零段」纯边界，starter 与 wrong 都失败（栈页没建），区分度成立。
- **平台约定调整**：`env-loadicode.c.inc` 原本在 `page_insert` 之外再 `p->pp_ref++`（栈页 pp_ref=2），
  与 3.6  documented 的「page_insert 独占 pp_ref」契约冲突。已统一为 pp_ref=1，并在片段与填写区注释写明理由。

### 7.8 实验 3.8 `1930155484338507777` — `env_create` / `env_create_priority`

（进行中，闭环后补全。）

## 8. 发现并修复的真实缺陷

| # | 位置 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | `env-pcb.c.inc` `reset_env_state` | `page_init()` 先于 `boot_kernel_map()` 执行，bump 分配器把已在空闲链表上的页又发出去 | 改为 `reset_state` → `boot_kernel_map` → `page_init`（真实 boot 顺序 `mips_vm_init`→`page_init`），并把 `boot_kernel_map` 定义上移 |
| 2 | `env-pcbdef.c.inc` | ch2 基座没有 `ROUNDDOWN`，与 3.1 boot-pre 的同名定义冲突 | 加 `#ifndef ROUNDDOWN` 守卫 |
| 3 | `env-pcb.c.inc` `kernel_pde_copied` | UVPT 槽被自映射合法覆盖，正确实现只得 767/768，无法与「没拷」区分 | 跳过 `PDX(UVPT)`；满分 767，不拷者 765，可区分 |
| 4 | 3.3 `cases.json` 用例 3 | envid 手算错（318 应为 319），零区分度且 starter 通过 | 输入改 `E 319 0`，用例改名（§7.3） |
| 5 | 3.2 `cases.json` 用例 1 | 用例名与实际 NENV=64 不符 | 改名为「前 8 个是 63..56」 |
| 6 | 3.4 `fill-wrong.c` 缺陷 2 | 原写「整表拷贝 1024 项」，但 `boot_pgdir` 在 `PDX(UTOP)` 以下全为 0，且 `kernel_pde_copied` 只统计 ≥256 的项 → 该缺陷**完全不可观测**（wrong 与 reference 输出相同） | 换成可观测的 off-by-one（下界 `PDX(UTOP)+1`）；另新增 `stale` 夹具 + `B` 操作，使「整表拷贝」这一真实边界也能被独立用例覆盖 |
| 7 | 探针调用方式 | `probe-harness-robustness.mjs` 的 `--probes` **必须是第一个参数**，放在目录后面会被当成目录名 → ENOENT 退出 1 | 改为 `node scripts/probe-harness-robustness.mjs --probes <file> <dir> [dir...]`，一次跑多个目录 |
| 8 | `env-envalloc.c.inc` | 3.4 把 `env_setup_vm` 当填写目标（body 在片段之后），平台 `env_alloc` 调用它时缺前置声明 | 在片段顶部加 `static MOS_UNUSED int env_setup_vm(struct Env *e);`，与平台件定义兼容 |
| 9 | **`env-mapper.c.inc` + 3.6 `fill-reference.c`（参考解自身真实缺陷）** | 首页 `memcpy(page2kva(p), binary+copied, want)` 把镜像第 0 字节放到**页首**而不是 `va` 处，整段镜像在地址空间里前移 `off` 字节；`want=BY2PG-off` 只限制了长度却没限制落点。用例 2 实测 `D 6144=0 / D 4096=50462976` 与手算相反才暴露 | 改为 `dst = (v==start) ? off : 0; memcpy(page2kva(p)+dst, ...)`，`want = BY2PG - dst`。平台片段与参考解同步修正，重跑 verify 后 5 用例实测与手算逐项吻合 |
| 10 | `env-image.c.inc` 注释 | 注释里写了 `user/*.elf`，其中 `/*` 触发 `-Werror=comment`（"/*" within comment），编译直接失败 | 改成「user 目录下的 .elf」；并给两个前置声明加 `MOS_UNUSED`，避免「declared static but never defined」告警 |

## 9. 对 harness-spec.md 的建议（仅建议，未修改该文件）

1. 建议补一条：**用例名中的数值必须与 harness 实际常量一致**（本次 3.2 用例名写 7..0 而 NENV=64）。
2. 建议补一条：**负例必须验证「参考解与 starter 输出不同」**，而不只是「wrong 与 reference 不同」
   （3.3 用例 3 的 318/319 事故正是 starter 意外通过）。可考虑在 verify 脚本里加断言：
   每个用例至少满足 `starter != reference` **或** `wrong != reference`，并对纯边界用例要求两者都不同。
3. 建议明确 `pp_ref` 归属：`page_insert` 负责 `pp_ref++`，调用方不得重复自增（2.7 契约），
   否则 3.6 的 `load_icode_mapper` 会出现双重计数歧义。

## 10. 未验证项（不得声称通过）

- **Docker 不可用**（`docker: command not found`）→ 沙箱内端到端判题**未验证**。
  本地 gcc 只验证了 `gcc -O2 -pipe main.c -o main` 等价的编译与运行，未验证容器内 runner.py 全链路。
- 后端全链路（`JudgeServiceImpl` → 代码沙箱 → `DefaultJudgeStrategy.normalizeOutput`）**未验证**（未启动后端）。
- 前端 UI（实验页渲染、判题结果展示）**未验证**（未启动前端，未碰任何前端文件）。
- `judge-cases.json` 与数据库 `question.content` 的一致性**未验证**（数据库只读，未做写入/映射）。

## 11. 25 篇 content.md 字节数与自检

（待写。目标：每篇 2500–6000 字节，固定 7 节，无空节 / 无 TODO / 无「略」，与 cases.json 描述及实际 I/O 一致。）