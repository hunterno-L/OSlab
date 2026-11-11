/* ---- 第四章共享：系统调用号、分发表类型与内核侧前置声明 ----
 * 真实源码锚：include/syscall.h 的 SYS_* 号、lib/syscall_all.c 的 sys_call_table[]。
 *
 * 【宿主环境说明】工作区未随附 include/syscall.h、lib/syscall_all.c 原文。
 * 系统调用号取 MOS 公开值；函数签名取题面逐字给出的形式，题面未给签名的
 * （sys_ipc_recv / sys_ipc_can_send / sys_env_alloc / sys_set_pgfault_handler /
 *  sys_set_env_status）按 MOS 公开语义构造，属等价示例而非原文转录。
 *
 * 第四章只要求 9 个系统调用，因此 0 / 6 / 7 号槽位为空，
 * handle_sys 遇到空槽位或越界号必须返回 -E_NO_SYS。
 *
 * 【分发表类型】真实 MOS 的 sys_call_table 是 void *[]，handle_sys 把槽位强转成
 * 「sysno + 5 个参数」的函数指针再调用（MIPS o32 下多余参数被被调方忽略）。
 * 宿主沿用同一约定，故 sys_mem_alloc 等能保持题面逐字给出的签名。
 * void * -> 函数指针的强转在 -Wall -Wextra -Werror 下合法
 * （改成同类型函数指针强转会触发 -Wcast-function-type）。
 *
 * 【CH4_USER_DRIVER】用户态实验（4.9/4.10/4.13/4.15）定义此宏：这些用户代码只用
 * 6 个系统调用，从不触发 yield 与 IPC，故平台省掉 sys-yield、sys-ipc 两段及其前置声明，
 * 分发表对应槽位留 NULL（handle_sys 正确判成 -E_NO_SYS），以满足 65536 字节单文件上限。
 * 这是源码预算裁剪，不是语义裁剪：被考查函数的行为与完整平台逐字一致。
 */

#define SYS_mem_alloc           1
#define SYS_mem_map             2
#define SYS_mem_unmap           3
#define SYS_env_alloc           4
#define SYS_set_env_status      5
#define SYS_ipc_recv            8
#define SYS_ipc_can_send        9
#define SYS_set_pgfault_handler 10
#define SYS_yield               11
#define SYS_MAX                 12

/* handle_sys 强转用的目标类型：正好是真实 msyscall 压入的 6 个值 */
typedef int (*syscall_fn)(int sysno, uint32_t a0, uint32_t a1, uint32_t a2,
                          uint32_t a3, uint32_t a4);

/* ---- 内核侧系统调用函数（题面给出的签名逐字保留） ---- */
static MOS_UNUSED int sys_mem_alloc(int sysno, uint32_t envid, uint32_t va, uint32_t perm);
static MOS_UNUSED int sys_mem_map(int sysno, uint32_t srcid, uint32_t srcva,
                                  uint32_t dstid, uint32_t dstva, uint32_t perm);
static MOS_UNUSED int sys_mem_unmap(int sysno, uint32_t envid, uint32_t va);
#ifndef CH4_USER_DRIVER
static MOS_UNUSED void sys_yield(void);
static MOS_UNUSED int sys_ipc_recv(int sysno, uint32_t dstva);
static MOS_UNUSED int sys_ipc_can_send(int sysno, uint32_t envid, uint32_t value,
                                       uint32_t srcva, uint32_t perm);
#endif
static MOS_UNUSED int sys_env_alloc(int sysno);
static MOS_UNUSED int sys_set_pgfault_handler(int sysno, uint32_t envid,
                                              uint32_t func, uint32_t xstacktop);
static MOS_UNUSED int sys_set_env_status(int sysno, uint32_t envid, uint32_t status);

/* 宿主 glue：sys_yield 的签名是 void(void)，直接放进 void* 表再按 6 参数 int
 * 返回类型调用，返回值会是 eax 里的残留值（不确定）。真实内核靠 msyscall 忽略
 * SYS_yield 的返回值规避这一点；宿主判题要求输出确定，故用本包装函数补一个 0。
 * 学生填写的 sys_yield 本体签名不变。 */
#ifndef CH4_USER_DRIVER
static MOS_UNUSED int sys_yield_entry(int sysno, uint32_t a0, uint32_t a1,
                                      uint32_t a2, uint32_t a3, uint32_t a4);
#endif

/* 内核侧系统调用入口。缺页处理 page_fault_handler 不在此前置声明：
 * 只有实验 4.11 会定义它，而 gcc 对「声明了 static 却始终没定义」的函数会报错，
 * MOS_UNUSED 压不住这条诊断，故它的声明留给 env-traps 段自身。 */
static MOS_UNUSED void handle_sys(void);