/* ===== 学生填写区：实现 mm/pmap.c 的 page_alloc() 与 page_free() ===== */

/* 典型错误：漏掉 Step 2 的 bzero。
 * 页被回收再分配时残留上一个使用者的数据，官方 page_check 的
 * 「free 后重新 alloc，*temp 必须为 0」断言会失败。 */
static int page_alloc(struct Page **pp)
{
    struct Page *ppage_temp;
    if (LIST_EMPTY(&page_free_list)) {
        return -E_NO_MEM;
    }
    ppage_temp = LIST_FIRST(&page_free_list);
    LIST_REMOVE(ppage_temp, pp_link);
    *pp = ppage_temp;
    return 0;
}

static void page_free(struct Page *pp)
{
    if (pp->pp_ref == 1) {
        return;
    }
    if (pp->pp_ref == 0) {
        LIST_INSERT_HEAD(&page_free_list, pp, pp_link);
        return;
    }
    panic("pp->pp_ref is less than zero");
}