/* ===== 学生填写区：实现 lib/env.c 的 env_setup_vm() ===== */

/* 题面 3.4：为新进程分配页目录、映射内核空间、设置 env_pgdir 与 env_cr3。
 *   Step 1: page_alloc(&p) 取一页作页目录，失败原样返回错误码；成功后 p->pp_ref++
 *           （页目录被该进程长期持有，不能回到空闲链表）；
 *   Step 2: e->env_pgdir = (Pde *)page2kva(p)  —— 内核虚拟地址；
 *           e->env_cr3   = page2pa(p)          —— 物理地址，切换地址空间时写进 CP0；
 *   Step 3: for (i = PDX(UTOP); i < 1024; i++) e->env_pgdir[i] = boot_pgdir[i];
 *           把内核段映射整段拷过来，使所有进程看到同一份内核；
 *   Step 4: UVPT 自映射 e->env_pgdir[PDX(UVPT)] = e->env_cr3 | PTE_V | PTE_R，
 *           让用户态能通过 UVPT 窗口只读地观察自己的页表。
 * 返回 0 表示成功。 */
static int env_setup_vm(struct Env *e)
{
    /* finish your code here */
    (void)e;
    return -E_NO_MEM;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 48

/* stdin: 第一行 "npage freemem stale"
 *   npage   1..NPAGE_MAX  物理页数
 *   freemem 0..65536      启动期已被占用的字节数（bump 分配器起点）
 *   stale   0/1           1 = 在 boot_pgdir 里额外留一条用户态映射（va 0x00400000），
 *                         用于检验「只拷内核段」这一边界
 * 第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I          env_init()，打印空闲 PCB 数
 *   A          env_alloc(&e,0)，打印 "A 返回码 下标"
 *   V idx      直接对 envs[idx] 调 env_setup_vm，打印 "V 返回码 idx"
 *   W idx      打印 "W idx 页目录物理地址 env_cr3 两者一致 内核段拷贝项数 UVPT项 pp_ref"
 *   Q idx va   打印该进程地址空间里 va 的物理地址（无映射为 4294967295）
 *   B va       打印 boot_pgdir 里 va 的物理地址（证明夹具本身存在）
 *   C          打印空闲物理页数
 */
int main(void)
{
    uint32_t np = 0, fm = 0, stale = 0;
    int n = 0, i, did_i = 0;
    if (scanf("%u %u %u", &np, &fm, &stale) != 3) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > 65536u || stale > 1u) { return 0; }
    reset_env_state_ex(np, fm, stale);
    if (scanf("%d", &n) != 1 || n < 0 || n > MAXOPS) { return 0; }
    for (i = 0; i < n; i++) {
        char op = 0;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'I') {
            if (did_i) { printf("I SKIP\n"); continue; }
            did_i = 1;
            env_init();
            printf("I %d\n", count_env_list(&env_free_list, 0));
        } else if (op == 'A') {
            struct Env *e = NULL;
            int r = env_alloc(&e, 0);
            printf("A %d %d\n", r, env_idx(e));
        } else if (op == 'V') {
            uint32_t idx = 0;
            int r;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            r = env_setup_vm(&envs[idx]);
            printf("V %d %u\n", r, idx);
        } else if (op == 'W') {
            uint32_t idx = 0, pa;
            struct Env *e;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            e = &envs[idx];
            pa = pa_of(e->env_pgdir);
            printf("W %u %u %u %u %u %u %d\n", idx, pa, e->env_cr3,
                   (pgdir_ok(e->env_pgdir) && pa != ~0u && e->env_cr3 == pa) ? 1u : 0u,
                   kernel_pde_copied(e->env_pgdir),
                   pgdir_ok(e->env_pgdir) ? e->env_pgdir[PDX(UVPT)] : ~0u,
                   pgdir_pp_ref(e));
        } else if (op == 'Q') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("Q %u\n", env_va2pa(envs[idx].env_pgdir, va));
        } else if (op == 'B') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            printf("B %u\n", env_va2pa(boot_pgdir, va));
        } else if (op == 'C') {
            printf("C %d\n", count_free_pages());
        } else {
            return 0;
        }
    }
    return 0;
}