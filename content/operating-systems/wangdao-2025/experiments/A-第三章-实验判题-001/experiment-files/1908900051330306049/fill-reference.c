/* ===== 学生填写区：实现 mm/pmap.c 的 mips_vm_init()（为 envs 分配内存并映射 UENVS） ===== */

/* 与工作区 answer 字段的真实 mips_vm_init 逐 Step 对应（去掉启动期 printf）。
 * 正确性自行验证要点：
 *   - Step 2/3 的 size 必须 ROUND 到 BY2PG，否则 boot_map_segment 的
 *     `size % BY2PG != 0` 守卫会直接返回，整段一个页表项都不写；
 *   - 权限必须是 PTE_R（只读），不是 PTE_V  alone，也不是可写；
 *   - envs/pages 的物理地址用 PADDR() 从内核虚拟地址换算，不能直接当物理地址用。 */
static void mips_vm_init(void)
{
    Pde *pgdir;
    uint32_t n;

    /* Step 1: 分配一页作为页目录 */
    pgdir = (Pde *)alloc(BY2PG, BY2PG, 1);
    if (pgdir == NULL) { return; }
    mCONTEXT = PADDR(pgdir);
    boot_pgdir = pgdir;

    /* Step 2: 分配 pages 数组并把 UPAGES 映射到它 */
    pages = (struct Page *)alloc(npage * (uint32_t)sizeof(struct Page), BY2PG, 1);
    if (pages == NULL) { return; }
    n = ROUND(npage * (uint32_t)sizeof(struct Page), BY2PG);
    boot_map_segment(pgdir, UPAGES, n, PADDR(pages), PTE_R);

    /* Step 3: 分配 envs 数组并把 UENVS 映射到它 */
    envs = (struct Env *)alloc(NENV * (uint32_t)sizeof(struct Env), BY2PG, 1);
    if (envs == NULL) { return; }
    n = ROUND(NENV * (uint32_t)sizeof(struct Env), BY2PG);
    boot_map_segment(pgdir, UENVS, n, PADDR(envs), PTE_R);
}

/* ===== 填写区结束 ===== */