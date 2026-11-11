/* ===== 学生填写区：实现 mm/pmap.c 的 page_alloc() 与 page_free() ===== */

/* 与真实 mm/pmap.c 逐 Step 对应。 */
static int page_alloc(struct Page **pp)
{
    struct Page *ppage_temp;
    /* Step 1：空闲链表为空则返回 -E_NO_MEM */
    if (LIST_EMPTY(&page_free_list)) {
        return -E_NO_MEM;
    }
    ppage_temp = LIST_FIRST(&page_free_list);
    LIST_REMOVE(ppage_temp, pp_link);
    /* Step 2：交付前必须清零，且不增加 pp_ref */
    *pp = ppage_temp;
    bzero(page2kva(ppage_temp), BY2PG);
    return 0;
}

static void page_free(struct Page *pp)
{
    /* Step 1：仍被引用则什么都不做 */
    if (pp->pp_ref == 1) {
        return;
    }
    /* Step 2：引用归零才回收 */
    if (pp->pp_ref == 0) {
        LIST_INSERT_HEAD(&page_free_list, pp, pp_link);
        return;
    }
    panic("pp->pp_ref is less than zero");
}