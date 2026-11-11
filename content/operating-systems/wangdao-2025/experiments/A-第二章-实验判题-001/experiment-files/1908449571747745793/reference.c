/* 操作系统实验 2.1 内存控制块 —— include/queue.h 链表操作宏
 * 主机端等价判题环境：保留 MOS 的 LIST_ENTRY/LIST_HEAD 真实语义，
 * 用 int 承载 pp_ref 以便在平台沙箱内编译运行。
 * 真实内核构建仍在本地 MIPS 交叉编译环境完成，见实验说明。
 */
#include <stdio.h>
#include <string.h>

#define LIST_HEAD(name, type) \
    struct name { struct type *lh_first; }

#define LIST_ENTRY(type) \
    struct { struct type *le_next; struct type **le_prev; }

#define LIST_EMPTY(head)        ((head)->lh_first == NULL)
#define LIST_FIRST(head)        ((head)->lh_first)
#define LIST_NEXT(elm, field)   ((elm)->field.le_next)

#define LIST_INIT(head) do { LIST_FIRST((head)) = NULL; } while (0)

#define LIST_FOREACH(var, head, field) \
    for ((var) = LIST_FIRST((head)); (var); (var) = LIST_NEXT((var), field))

#define LIST_INSERT_HEAD(head, elm, field) do { \
    if ((LIST_NEXT((elm), field) = LIST_FIRST((head))) != NULL) \
        LIST_FIRST((head))->field.le_prev = &LIST_NEXT((elm), field); \
    LIST_FIRST((head)) = (elm); \
    (elm)->field.le_prev = &LIST_FIRST((head)); \
} while (0)

#define LIST_REMOVE(elm, field) do { \
    if (LIST_NEXT((elm), field) != NULL) \
        LIST_NEXT((elm), field)->field.le_prev = (elm)->field.le_prev; \
    *(elm)->field.le_prev = LIST_NEXT((elm), field); \
} while (0)

struct Page { int pp_ref; LIST_ENTRY(Page) pp_link; };
LIST_HEAD(Page_list, Page);

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
} while (0)/* ===== 填写区结束 ===== */

#define MAXP 64
static struct Page pool[MAXP];
static struct Page_list free_list;

/* 驱动侧保护：只对学生实现「确实已链入」的元素做移除/后插。
 * 否则错误实现会让 LIST_REMOVE 解引用 NULL 的 le_prev 而崩溃，
 * 沙箱把非零退出判为 Runtime Error，学生就看不到「答案错误」了。
 * 步行上限 MAXP，避免错误实现造成环时死循环。 */
static int in_list(struct Page *target)
{
    struct Page *p = LIST_FIRST(&free_list);
    int steps = 0;
    while (p != NULL && steps < MAXP) {
        if (p == target) { return 1; }
        p = LIST_NEXT(p, pp_link);
        steps++;
    }
    return 0;
}

int main(void)
{
    int n = 0;
    int i;
    struct Page *p = NULL;
    int first = 1;
    int steps = 0;
    if (scanf("%d", &n) != 1 || n < 0 || n > 256) { return 0; }
    LIST_INIT(&free_list);
    for (i = 0; i < MAXP; i++) {
        pool[i].pp_ref = -1;
        memset(&pool[i].pp_link, 0, sizeof pool[i].pp_link);
    }
    for (i = 0; i < n; i++) {
        char op = 0;
        int a = 0, b = 0;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'T') {
            if (scanf("%d", &a) != 1 || a < 0 || a >= MAXP) { return 0; }
            pool[a].pp_ref = a;
            if (!in_list(&pool[a])) {
                LIST_INSERT_TAIL(&free_list, &pool[a], pp_link);
            }
        } else if (op == 'A') {
            if (scanf("%d %d", &a, &b) != 2 || a < 0 || a >= MAXP || b < 0 || b >= MAXP) { return 0; }
            pool[b].pp_ref = b;
            if (in_list(&pool[a]) && !in_list(&pool[b])) {
                LIST_INSERT_AFTER(&pool[a], &pool[b], pp_link);
            }
        } else if (op == 'H') {
            if (scanf("%d", &a) != 1 || a < 0 || a >= MAXP) { return 0; }
            pool[a].pp_ref = a;
            if (!in_list(&pool[a])) {
                LIST_INSERT_HEAD(&free_list, &pool[a], pp_link);
            }
        } else if (op == 'R') {
            if (scanf("%d", &a) != 1 || a < 0 || a >= MAXP) { return 0; }
            if (in_list(&pool[a])) {
                LIST_REMOVE(&pool[a], pp_link);
            }
        } else {
            return 0;
        }
    }
    p = LIST_FIRST(&free_list);
    while (p != NULL && steps < MAXP) {
        if (!first) { printf(" "); }
        printf("%d", p->pp_ref);
        first = 0;
        p = LIST_NEXT(p, pp_link);
        steps++;
    }
    printf("\n");
    return 0;
}