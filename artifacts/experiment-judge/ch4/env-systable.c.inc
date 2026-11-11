/* ---- 平台提供的 lib/syscall_all.c sys_call_table[]（实验 4.2 一并考查） ----
 * 真实 MOS 就是一张 void * 数组，用系统调用号作下标（C99 指定初始化器）。
 * 0 / 6 / 7 号槽位保持 NULL：第四章只要求 9 个系统调用，
 * handle_sys 必须把这些空槽位判成 -E_NO_SYS，而不是跳进去执行。
 * SYS_yield 槽位放的是宿主 glue sys_yield_entry（理由见 env-sysno 段）。
 * CH4_USER_DRIVER（用户态实验）下 yield / IPC 三段被裁掉，对应槽位留 NULL。 */

#ifndef CH4_USER_DRIVER
/* 宿主 glue：真实 MOS 的 sys_yield 返回 void，而本表统一用 syscall_fn（返回 int）。
 * 适配层放在这里而不是 sys-yield 段，这样实验 4.6 排除 sys-yield 时只挖掉学生要写的
 * sys_yield 本体，表与 glue 仍由平台提供。 */
static MOS_UNUSED int sys_yield_entry(int sysno, uint32_t a0, uint32_t a1,
                                      uint32_t a2, uint32_t a3, uint32_t a4)
{
    (void)sysno; (void)a0; (void)a1; (void)a2; (void)a3; (void)a4;
    sys_yield();
    return 0;
}
#endif

static MOS_UNUSED void *sys_call_table[SYS_MAX] = {
    [SYS_mem_alloc]           = sys_mem_alloc,
    [SYS_mem_map]             = sys_mem_map,
    [SYS_mem_unmap]           = sys_mem_unmap,
    [SYS_env_alloc]           = sys_env_alloc,
    [SYS_set_env_status]      = sys_set_env_status,
#ifndef CH4_USER_DRIVER
    [SYS_ipc_recv]            = sys_ipc_recv,
    [SYS_ipc_can_send]        = sys_ipc_can_send,
#endif
    [SYS_set_pgfault_handler] = sys_set_pgfault_handler,
#ifndef CH4_USER_DRIVER
    [SYS_yield]               = sys_yield_entry,
#endif
};