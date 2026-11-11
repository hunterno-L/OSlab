/* ===== 学生填写区：实现 mm/pmap.c 的 page_init() ===== */

/* Step 1: 用 LIST_INIT 初始化 page_free_list。
 * Step 2: 把 freemem 向上对齐到 BY2PG 的整数倍（ROUND 宏）。
 * Step 3: [0, freemem/BY2PG) 的页已被内核占用，pp_ref 置 1。
 * Step 4: 其余页 pp_ref 置 0，并用 LIST_INSERT_HEAD 挂入空闲链表。
 * 注意：真实源码用头插，因此空闲链表是页号递减序（LIFO）。 */
static void page_init(void)
{
    /* finish your code here */
}

/* ===== 填写区结束 ===== */

int main(void)
{
    uint32_t np = 0, fm = 0, i;
    uint32_t used = 0, freed = 0, other = 0;
    if (scanf("%u %u", &np, &fm) != 2) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > np * BY2PG) { return 0; }
    reset_state(np, fm);
    page_init();
    print_free_list();
    for (i = 0; i < np; i++) {
        if (pages[i].pp_ref == 1) { used++; }
        else if (pages[i].pp_ref == 0) { freed++; }
        else { other++; }
    }
    printf("%u %u %u %u\n", freemem, used, freed, other);
    return 0;
}