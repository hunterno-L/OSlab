/* ===== 学生填写区：实现 mm/pmap.c 的 mips_vm_init()（为 envs 分配内存并映射 UENVS） ===== */

/* 典型错误解（用于验证判题能区分）：
 *   缺陷 1：Step 3 忘记 ROUND 到 BY2PG，直接把 NENV*sizeof(struct Env) 当 size。
 *           boot_map_segment 的 `size % BY2PG != 0` 守卫会整段返回，UENVS 一个页都没映射。
 *   缺陷 2：权限只给 PTE_V，漏了 PTE_R，用户态读 envs 会触发地址错误异常。 */
static void mips_vm_init(void)
{
    Pde *pgdir;
    uint32_t n;

    pgdir = (Pde *)alloc(BY2PG, BY2PG, 1);
    if (pgdir == NULL) { return; }
    mCONTEXT = PADDR(pgdir);
    boot_pgdir = pgdir;

    pages = (struct Page *)alloc(npage * (uint32_t)sizeof(struct Page), BY2PG, 1);
    if (pages == NULL) { return; }
    n = ROUND(npage * (uint32_t)sizeof(struct Page), BY2PG);
    boot_map_segment(pgdir, UPAGES, n, PADDR(pages), PTE_R);

    envs = (struct Env *)alloc(NENV * (uint32_t)sizeof(struct Env), BY2PG, 1);
    if (envs == NULL) { return; }
    n = NENV * (uint32_t)sizeof(struct Env);          /* 缺陷 1：没有 ROUND */
    boot_map_segment(pgdir, UENVS, n, PADDR(envs), PTE_V);  /* 缺陷 2：漏 PTE_R */
}

/* ===== 填写区结束 ===== */