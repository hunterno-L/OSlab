/* ===== 学生填写区：实现 include/queue.h 中的两个宏 ===== */

/* 在 listelm 之后插入 elm。注意维护 le_prev 双向指向。
 * 与工作区 originalCode/answer 中的实现一致（该宏本身正确）。 */
#define LIST_INSERT_AFTER(listelm, elm, field) do { \
    LIST_NEXT((elm), field) = LIST_NEXT((listelm), field); \
    if ((LIST_NEXT((listelm), field)) != NULL) \
        LIST_NEXT((listelm), field)->field.le_prev = &LIST_NEXT((elm), field); \
    LIST_NEXT((listelm), field) = (elm); \
    (elm)->field.le_prev = &LIST_NEXT((listelm), field); \
} while (0)

/* 在 head 链表尾部插入 elm。空链表与非空链表都要正确。
 * 相对工作区 answer 字段修正两处（实测该字段版本有缺陷）：
 *   1) le_prev 必须指向「前驱的 le_next」，原版本误指向 elm 自己的 le_next，
 *      导致后续 LIST_REMOVE 无法断开前驱，留下悬空指针；
 *   2) le_next = NULL 移到 if/else 之外，空链表分支同样要置空。
 */
#define LIST_INSERT_TAIL(head, elm, field) do { \
    if (LIST_FIRST((head)) == NULL) { \
        LIST_FIRST((head)) = (elm); \
        (elm)->field.le_prev = &LIST_FIRST((head)); \
    } else { \
        LIST_NEXT((elm), field) = LIST_FIRST((head)); \
        while (LIST_NEXT(LIST_NEXT((elm), field), field) != NULL) \
            LIST_NEXT((elm), field) = LIST_NEXT(LIST_NEXT((elm), field), field); \
        (elm)->field.le_prev = &LIST_NEXT(LIST_NEXT((elm), field), field); \
        LIST_NEXT(LIST_NEXT((elm), field), field) = (elm); \
    } \
    LIST_NEXT((elm), field) = NULL; \
} while (0)