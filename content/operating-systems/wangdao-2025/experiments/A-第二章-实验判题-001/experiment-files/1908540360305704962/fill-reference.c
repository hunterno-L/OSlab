/* ===== 学生填写区：实现 mm/pmap.c 的 boot_map_segment() ===== */

/* 与真实 mm/pmap.c 逐 Step 对应。 */
static void boot_map_segment(Pde *pgdir, uint32_t va, uint32_t size, uint32_t pa, int perm)
{
    uint32_t va_temp;
    Pte *pgtable_entry;
    /* Step 1：size 必须是 BY2PG 的整数倍，否则整段不映射 */
    if (size % BY2PG != 0) {
        return;
    }
    /* Step 2：逐页建立映射，权限位是 perm | PTE_V */
    for (va_temp = va; va_temp < va + size; va_temp += BY2PG, pa += BY2PG) {
        pgtable_entry = boot_pgdir_walk(pgdir, va_temp, 1);
        if (pgtable_entry == NULL) { return; }
        *pgtable_entry = pa | (uint32_t)perm | PTE_V;
    }
}