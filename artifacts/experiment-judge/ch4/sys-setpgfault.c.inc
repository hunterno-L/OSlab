/* ---- 平台提供的 lib/syscall_all.c sys_set_pgfault_handler（实验 4.12 考查时排除） ----
 *
 * 【宿主环境说明】题面 4.12 未给签名，按 MOS 公开语义取
 * int sys_set_pgfault_handler(int sysno, u_int envid, u_int func, u_int xstacktop)。
 *
 * 题面 4.12 实验任务 2 逐条要求：
 *   「检查传入的处理函数指针是否有效」-> func 必须非 0 且落在用户空间 [0, UTOP)；
 *   「调用 syscall_mem_alloc 为处理函数分配内存」-> 为异常处理栈申请一页，
 *       映射在 xstacktop - BY2PG，权限 PTE_V | PTE_R（栈必须可写）；
 *   「设置 env_pgfault_handler 字段为传入的处理函数」-> e->env_pgfault_handler = func。
 * 步骤顺序：先校验参数 -> envid2env -> 写 handler -> 申请异常栈页，错误原样上抛。
 *
 * 宿主 PCB 未设 env_xstacktop 字段：真实 MOS 用它给陷阱入口汇编切换异常栈，
 * 而那段汇编属实验 4.1（Tier B 本地构建），第四章 14 个可判题函数都不读它，
 * 故按 YAGNI 不加字段，xstacktop 只用于算出要申请的那一页。 */

static MOS_UNUSED int sys_set_pgfault_handler(int sysno, uint32_t envid,
                                              uint32_t func, uint32_t xstacktop)
{
    struct Env *e = NULL;
    int r;
    if (func == 0 || func >= UTOP) { return -E_INVAL; }
    if (xstacktop < BY2PG || xstacktop > UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    e->env_pgfault_handler = func;
    return sys_mem_alloc(sysno, envid, xstacktop - BY2PG, PTE_V | PTE_R);
}