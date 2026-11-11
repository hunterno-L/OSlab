/* ===== 学生填写区：实现 mm/pmap.c 的 page_alloc() 与 page_free() ===== */

/* 从空闲链表取一页并清零。
 * 成功：*pp 指向该页，返回 0；失败（空闲链表为空）：返回 -E_NO_MEM。
 * 注意：page_alloc 不增加 pp_ref，引用计数由调用方（page_insert）负责。 */
static int page_alloc(struct Page **pp)
{
    /* finish your code here */
    (void)pp;
    return -E_NO_MEM;
}

/* 释放一页。pp_ref == 1 说明仍有虚拟地址引用它，直接返回；
 * pp_ref == 0 时用 LIST_INSERT_HEAD 挂回空闲链表；
 * pp_ref < 0 说明此前已出错，调用 panic。 */
static void page_free(struct Page *pp)
{
    /* finish your code here */
    (void)pp;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 64
#define MARKER 0xDEADBEEFu

int main(void)
{
    uint32_t np = 0, fm = 0;
    int n = 0, i, nalloc = 0;
    int alloc_idx[MAXOPS];
    if (scanf("%u %u", &np, &fm) != 2) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > np * BY2PG) { return 0; }
    reset_state(np, fm);
    page_init();
    if (scanf("%d", &n) != 1 || n < 0 || n > MAXOPS) { return 0; }
    for (i = 0; i < n; i++) {
        char op = 0;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'A') {
            struct Page *p = NULL;
            int ret = page_alloc(&p);
            if (ret != 0 || !page_in_range(p) || (uint32_t)(p - pages) >= np) {
                printf("A -4\n");
            } else {
                uint32_t word = 0;
                memcpy(&word, page2kva(p), sizeof word);
                printf("A %d %u\n", (int)(p - pages), word);
                word = MARKER;
                memcpy(page2kva(p), &word, sizeof word);
                alloc_idx[nalloc++] = (int)(p - pages);
            }
        } else if (op == 'F' || op == 'S') {
            int k = 0;
            struct Page *p;
            if (scanf("%d", &k) != 1 || k < 1 || k > nalloc) { return 0; }
            p = &pages[alloc_idx[k - 1]];
            if (op == 'S') {
                int r = 0;
                if (scanf("%d", &r) != 1 || r < -1 || r > 4) { return 0; }
                p->pp_ref = r;
            } else {
                page_free(p);
                printf("F %d %d\n", (int)(p - pages), (int)p->pp_ref);
            }
        } else {
            return 0;
        }
    }
    print_free_list();
    return 0;
}