/* ---- 平台提供的 user/fork.c fork（4.9 考子进程分支、4.15 考父进程分支时排除本片段） ----
 *
 * 【宿主环境说明】工作区未随附 user/fork.c 原文，按题面 + MOS 公开语义构造，
 * 属等价示例。真实 MOS 就是一个 fork()，拆成 4.9/4.15 两题，填写区都是整个 fork()，
 * 区别只在用例与错误变体各自针对哪一半。
 *
 * 七步：1 envid = syscall_env_alloc()；2 envid==0 -> env = &envs[ENVX(syscall_getenvid())]，
 * return 0；3 遍历 pn=0..VPN(USTACKTOP)-1，对带 PTE_V 的页调 duppage(envid, pn)；
 * 4 syscall_mem_alloc(envid, UXSTACKTOP-BY2PG, PTE_V|PTE_R)；
 * 5 syscall_set_pgfault_handler(envid, PGFAULT_ENTRY, UXSTACKTOP)；
 * 6 syscall_set_env_status(envid, ENV_RUNNABLE)；7 return envid。 */

/* 4.10 把 duppage 当作学生填写目标（定义在 body 里、位于本段之后），故先前置声明；
 * 当 user-duppage.c.inc 作为平台件被包含时，本声明与随后的定义兼容，无副作用。 */
static MOS_UNUSED void duppage(uint32_t envid, uint32_t pn);

static MOS_UNUSED int fork(void)
{
    uint32_t envid, pn;
    envid = (uint32_t)syscall_env_alloc();
    if (envid == 0) {
        env = &envs[ENVX(syscall_getenvid())];
        return 0;
    }
    for (pn = 0; pn < VPN(USTACKTOP); pn++) {
        if (user_pte(pn << 12) & PTE_V) { duppage(envid, pn); }
    }
    syscall_mem_alloc(envid, UXSTACKTOP - BY2PG, PTE_V | PTE_R);
    syscall_set_pgfault_handler(envid, PGFAULT_ENTRY, UXSTACKTOP);
    syscall_set_env_status(envid, ENV_RUNNABLE);
    return (int)envid;
}