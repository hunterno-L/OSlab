/* ===== 学生填写区：实现 include/queue.h 中的两个宏 ===== */

/* LIST_INSERT_AFTER 保持正确，以便判题能定位到具体缺陷。 */
#define LIST_INSERT_AFTER(listelm, elm, field) do { \
    LIST_NEXT((elm), field) = LIST_NEXT((listelm), field); \
    if ((LIST_NEXT((listelm), field)) != NULL) \
        LIST_NEXT((listelm), field)->field.le_prev = &LIST_NEXT((elm), field); \
    LIST_NEXT((listelm), field) = (elm); \
    (elm)->field.le_prev = &LIST_NEXT((listelm), field); \
} while (0)

/* 典型错误：把尾插直接复用头插（学生最常见错误）。 */
#define LIST_INSERT_TAIL(head, elm, field) do { \
    LIST_INSERT_HEAD((head), (elm), field); \
} while (0)