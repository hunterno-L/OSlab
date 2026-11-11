/* ---- 平台提供的 lib/env.c env_setup_vm（实验 3.4 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 分配一页作页目录，并把 pp_ref 递增（页目录被本进程长期持有）；
 *   Step 2 env_pgdir = 页目录内核虚拟地址，env_cr3 = 页目录物理地址；
 *   Step 3 把 boot_pgdir 的内核段（PDX(UTOP)..1023）逐项拷贝过来，
 *          使每个进程都能看到同一份内核映射；
 *   Step 4 UVPT 自映射：env_pgdir[PDX(UVPT)] = env_cr3 | PTE_V | PTE_R，
 *          让用户态能通过 UVPT 窗口只读地观察自己的页表。 */

static MOS_UNUSED int env_setup_vm(struct Env *e)
{
    uint32_t i;
    int r;
    struct Page *p = NULL;
    if (!env_in_range(e)) { return -E_BAD_ENV; }
    r = page_alloc(&p);
    if (r != 0) { return r; }
    p->pp_ref++;
    e->env_pgdir = (Pde *)page2kva(p);
    e->env_cr3 = page2pa(p);
    for (i = PDX(UTOP); i < 1024u; i++) { e->env_pgdir[i] = boot_pgdir[i]; }
    e->env_pgdir[PDX(UVPT)] = e->env_cr3 | PTE_V | PTE_R;
    return 0;
}