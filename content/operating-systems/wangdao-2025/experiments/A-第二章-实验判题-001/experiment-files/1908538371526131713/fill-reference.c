/* ===== 学生填写区：实现 mm/pmap.c 的 page_init() ===== */

/* 与真实 mm/pmap.c page_init() 逐 Step 对应。 */
static void page_init(void)
{
    uint32_t count = 0;
    /* Step 1 */
    LIST_INIT(&page_free_list);
    /* Step 2 */
    freemem = ROUND(freemem, BY2PG);
    /* Step 3：freemem 以下的页已被内核占用 */
    for (count = 0; count < freemem / BY2PG; count++) {
        pages[count].pp_ref = 1;
    }
    /* Step 4：其余页入空闲链表，头插 => 页号递减序 */
    for (; count < npage; count++) {
        pages[count].pp_ref = 0;
        LIST_INSERT_HEAD(&page_free_list, &pages[count], pp_link);
    }
}