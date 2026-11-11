/* ---- 平台提供的 lib/syscall_all.c sys_mem_map（实验 4.4 考查时排除本片段） ----
 * MOS 公开语义：把源进程 srcva 处已映射的物理页，再映射到目标进程 dstva 处，
 * 两个进程从此共享同一页（这是 IPC 传页与 fork 的 duppage 的基础）。
 *   Step 1 srcva / dstva 任一 >= UTOP -> -E_INVAL；
 *   Step 2 两次 envid2env(..., 0) 取源/目标 PCB，失败原样上抛；
 *   Step 3 page_lookup 在源页表里找 srcva 对应的物理页，找不到 -> -E_INVAL
 *          （page_lookup 自身已拒绝无页表项 / PTE_V 为 0 / 物理地址越界三种情况）；
 *   Step 4 page_insert 到目标页表，perm 原样传下去（PTE_COW / PTE_LIBRARY 靠它传递），
 *          page_insert 自己会补 PTE_V 并递增 pp_ref。
 * 注意：本函数【不】解除源进程的映射，共享后同一页 pp_ref 至少为 2。 */

static MOS_UNUSED int sys_mem_map(int sysno, uint32_t srcid, uint32_t srcva,
                                  uint32_t dstid, uint32_t dstva, uint32_t perm)
{
    struct Env *src = NULL;
    struct Env *dst = NULL;
    struct Page *p;
    Pte *pte = NULL;
    int r;
    (void)sysno;
    if (srcva >= UTOP || dstva >= UTOP) { return -E_INVAL; }
    r = envid2env(srcid, &src, 0);
    if (r != 0) { return r; }
    r = envid2env(dstid, &dst, 0);
    if (r != 0) { return r; }
    p = page_lookup(src->env_pgdir, srcva, &pte);
    if (p == NULL) { return -E_INVAL; }
    return page_insert(dst->env_pgdir, p, dstva, perm);
}