/* ---- 平台提供的 lib/syscall_all.c sys_mem_alloc（实验 4.3 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 perm 补上 PTE_V（用户只给权限位，有效位由内核置）；
 *   Step 2 va >= UTOP 属内核地址空间，用户无权申请 -> -E_INVAL；
 *   Step 3 envid2env(envid, &e, 0) 取目标 PCB，失败原样上抛；
 *   Step 4 page_alloc 拿一页物理内存，失败原样上抛；
 *   Step 5 page_insert 建立映射；失败必须 page_free 把刚拿到的页还回空闲链表
 *          （page_alloc 不递增 pp_ref，此时 pp_ref 为 0，page_free 正好回收）。
 * 引用计数由 page_insert 负责（实验 2.7 已验证的契约），本函数不额外递增。 */

static MOS_UNUSED int sys_mem_alloc(int sysno, uint32_t envid, uint32_t va, uint32_t perm)
{
    struct Env *e = NULL;
    struct Page *p = NULL;
    int r;
    (void)sysno;
    perm |= PTE_V;
    if (va >= UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    r = page_alloc(&p);
    if (r != 0) { return r; }
    r = page_insert(e->env_pgdir, p, va, perm);
    if (r != 0) { page_free(p); }
    return r;
}