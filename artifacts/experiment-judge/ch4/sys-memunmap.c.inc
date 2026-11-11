/* ---- 平台提供的 lib/syscall_all.c sys_mem_unmap（实验 4.5 考查时排除本片段） ----
 * MOS 公开语义：解除 envid 进程地址空间中 va 处的映射。
 *   Step 1 va >= UTOP -> -E_INVAL（内核映射不许用户解除）；
 *   Step 2 envid2env(..., 0) 取 PCB，失败原样上抛；
 *   Step 3 page_remove 摘掉页表项并递减 pp_ref，归零时把物理页还回空闲链表；
 *   Step 4 返回 0。
 * 关键：va 处本来就没有映射时 page_remove 是空操作，本函数仍返回 0（不是错误）。
 * 共享页只递减引用计数，只有最后一个使用者解除映射后物理页才被回收。 */

static MOS_UNUSED int sys_mem_unmap(int sysno, uint32_t envid, uint32_t va)
{
    struct Env *e = NULL;
    int r;
    (void)sysno;
    if (va >= UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    page_remove(e->env_pgdir, va);
    return 0;
}