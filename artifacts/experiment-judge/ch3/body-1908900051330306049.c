/* ===== 学生填写区：实现 mm/pmap.c 的 mips_vm_init()（为 envs 分配内存并映射 UENVS） ===== */

/* 本填写区对应 mips_vm_init() 的内存分配与映射步骤。
 * 宿主等价适配：去掉启动期 printf（改由检查驱动打印观测量）；
 * pages/envs/boot_pgdir 是平台提供的指针全局量，alloc/boot_pgdir_walk/
 * boot_map_segment 已由平台按真实 mm/pmap.c 实现。
 *
 * Step 1: 用 alloc(BY2PG, BY2PG, 1) 分配一页作为页目录（一级页表），
 *         令 boot_pgdir = pgdir，并把页目录物理地址记入 mCONTEXT。
 * Step 2: 用 alloc(npage * sizeof(struct Page), BY2PG, 1) 分配 pages 数组，
 *         n = ROUND(npage * sizeof(struct Page), BY2PG)（必须向上取整到页！），
 *         boot_map_segment(pgdir, UPAGES, n, PADDR(pages), PTE_R)。
 * Step 3: 用 alloc(NENV * sizeof(struct Env), BY2PG, 1) 分配 envs 数组，
 *         n = ROUND(NENV * sizeof(struct Env), BY2PG)，
 *         boot_map_segment(pgdir, UENVS, n, PADDR(envs), PTE_R)。
 *         权限必须是 PTE_R：用户态要能读 envs/pages，但不能写。 */
static void mips_vm_init(void)
{
    /* finish your code here */
}

/* ===== 填写区结束 ===== */

#define MAXOPS 32

int main(void)
{
    uint32_t np = 0, fm = 0;
    int n = 0, i, did_v = 0;
    if (scanf("%u %u", &np, &fm) != 2) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > 65536u) { return 0; }
    reset_vm(np, fm);
    if (scanf("%d", &n) != 1 || n < 0 || n > MAXOPS) { return 0; }
    for (i = 0; i < n; i++) {
        char op = 0;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'V') {
            if (did_v) { printf("V SKIP\n"); continue; }
            did_v = 1;
            mips_vm_init();
            printf("V %u %u %u\n", freemem, mCONTEXT, pa_of(boot_pgdir));
        } else if (op == 'E') {
            uint32_t cnt = count_mapped(UENVS);
            uint32_t last = (cnt > 0) ? boot_va2pa(UENVS + (cnt - 1u) * BY2PG) : ~0u;
            printf("E %u %u %u %u %u\n", pa_of(envs), cnt, pte_perm(UENVS),
                   boot_va2pa(UENVS), last);
        } else if (op == 'P') {
            printf("P %u %u %u\n", pa_of(pages), count_mapped(UPAGES), pte_perm(UPAGES));
        } else if (op == 'Q') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            printf("Q %u\n", boot_va2pa(va));
        } else {
            return 0;
        }
    }
    return 0;
}