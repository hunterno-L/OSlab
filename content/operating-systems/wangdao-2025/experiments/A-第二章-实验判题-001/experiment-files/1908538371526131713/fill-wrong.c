/* ===== 学生填写区：实现 mm/pmap.c 的 page_init() ===== */

/* 典型错误：Step 4 用 LIST_INSERT_TAIL 代替 LIST_INSERT_HEAD。
 * 空闲链表变成页号递增序，page_alloc 会先分出低地址页，
 * 与真实内核的 LIFO 行为相反（官方 page_check 依赖头插顺序）。 */
static void page_init(void)
{
    uint32_t count = 0;
    LIST_INIT(&page_free_list);
    freemem = ROUND(freemem, BY2PG);
    for (count = 0; count < freemem / BY2PG; count++) {
        pages[count].pp_ref = 1;
    }
    for (; count < npage; count++) {
        pages[count].pp_ref = 0;
        LIST_INSERT_TAIL(&page_free_list, &pages[count], pp_link);
    }
}