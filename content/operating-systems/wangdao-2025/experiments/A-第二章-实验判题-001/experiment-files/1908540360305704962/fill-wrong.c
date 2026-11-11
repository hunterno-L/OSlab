/* ===== 学生填写区：实现 mm/pmap.c 的 boot_map_segment() ===== */

/* 典型错误：写页表项时漏掉 PTE_V。
 * 物理地址与权限都对，但有效位为 0，MMU 会认为该页不存在，
 * 后续 create==0 的步行与 page_lookup 都取不到映射。 */
static void boot_map_segment(Pde *pgdir, uint32_t va, uint32_t size, uint32_t pa, int perm)
{
    uint32_t va_temp;
    Pte *pgtable_entry;
    if (size % BY2PG != 0) {
        return;
    }
    for (va_temp = va; va_temp < va + size; va_temp += BY2PG, pa += BY2PG) {
        pgtable_entry = boot_pgdir_walk(pgdir, va_temp, 1);
        if (pgtable_entry == NULL) { return; }
        *pgtable_entry = pa | (uint32_t)perm;
    }
}