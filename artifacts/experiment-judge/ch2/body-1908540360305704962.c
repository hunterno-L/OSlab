/* ===== 学生填写区：实现 mm/pmap.c 的 boot_map_segment() ===== */

/* 把虚拟区间 [va, va+size) 映射到物理区间 [pa, pa+size)。
 * Step 1: size 不是 BY2PG 的整数倍时直接返回，什么都不做。
 * Step 2: 逐页调用 boot_pgdir_walk(pgdir, va_temp, 1) 取页表项，
 *         写入 pa | perm | PTE_V，然后 va_temp 与 pa 各前进 BY2PG。
 * 提示：PTE_V 必须置位，否则后续 create==0 的步行会认为该页表不存在。 */
static void boot_map_segment(Pde *pgdir, uint32_t va, uint32_t size, uint32_t pa, int perm)
{
    /* finish your code here */
    (void)pgdir; (void)va; (void)size; (void)pa; (void)perm;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 32
#define VA_LIMIT 0x04000000u
#define MAX_MAP_SIZE (16u * BY2PG)
#define NO_PTE 4294967295u

static void print_pte(uint32_t va)
{
    Pte *p = boot_pgdir_walk(boot_pgdir, va, 0);
    if (!pte_in_range(p)) { printf("%u", NO_PTE); return; }
    printf("%u", *p);
}

int main(void)
{
    uint32_t np = 0, fm = 0;
    int n = 0, i;
    if (scanf("%u %u", &np, &fm) != 2) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > 65536u || fm > np * BY2PG) { return 0; }
    reset_state(np, fm);
    page_init();
    if (scanf("%d", &n) != 1 || n < 0 || n > MAXOPS) { return 0; }
    for (i = 0; i < n; i++) {
        char op = 0;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'M') {
            uint32_t va = 0, size = 0, pa = 0;
            int perm = 0;
            if (scanf("%u %u %u %d", &va, &size, &pa, &perm) != 4) { return 0; }
            if (va >= VA_LIMIT || size > MAX_MAP_SIZE || pa >= maxpa || perm < 0 || perm > 0xFFF) { return 0; }
            boot_map_segment(boot_pgdir, va, size, pa, perm);
            printf("M ");
            print_pte(va);
            printf(" ");
            if (size == 0) { printf("%u", NO_PTE); } else { print_pte(va + size - BY2PG); }
            printf("\n");
        } else if (op == 'Q') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1 || va >= VA_LIMIT) { return 0; }
            printf("Q ");
            print_pte(va);
            printf("\n");
        } else {
            return 0;
        }
    }
    return 0;
}