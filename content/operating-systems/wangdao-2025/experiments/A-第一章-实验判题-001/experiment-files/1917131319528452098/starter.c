/* 操作系统实验 1.1：构建依赖顺序。
 * 按 make 的目标依赖语义填写合法构建顺序；依赖图是教学示例。
 * 完整内核构建需在本地 MIPS 工具链验证，本子任务通过不代表内核构建成功。
 * 仅填写标记的学生区域。
 */
#include <stdio.h>
#include <string.h>

#define MAXT 64
#define NAMELEN 16

static char name[MAXT][NAMELEN];
static int dep[MAXT][MAXT];   /* dep[i][j] = 1 表示目标 i 依赖目标 j */

/* ===== 学生填写区：make 依赖图的合法重建顺序（拓扑序） ===== */
/* 把 make 的合法重建顺序写入 order[]（被依赖者先重建）。
 * 同时就绪的目标有多个时，一律取编号最小者，保证顺序唯一。
 * 全部重建成功返回 0；存在循环依赖返回 -1。
 * 无论成功与否，都把已重建个数写入 *built（make 会先构建能构建的部分再报环）。 */
int build_order(int n, int dep[MAXT][MAXT], int order[MAXT], int *built)
{
    /* finish your code here */
    (void)n; (void)dep; (void)order;
    *built = 0;
    return 0;
}
/* ===== 填写区结束 ===== */

int main(void)
{
    int n, i, j, k, d, cnt, rc;
    /* 驱动自有输出缓冲必须初始化：gcc 无法证明学生函数会写满 order[]，
     * 否则 -Werror=maybe-uninitialized 直接拒绝编译（harness-spec §6）。 */
    int order[MAXT] = {0};
    if (scanf("%d", &n) != 1) return 0;
    if (n < 1 || n > MAXT) return 0;
    for (i = 0; i < n; i++) {
        if (scanf("%15s %d", name[i], &k) != 2) return 0;
        if (k < 0 || k > n) return 0;
        for (j = 0; j < k; j++) {
            if (scanf("%d", &d) != 1) return 0;
            if (d < 0 || d >= n) return 0;
            dep[i][d] = 1;
        }
    }
    cnt = 0;
    rc = build_order(n, dep, order, &cnt);
    /* 学生返回的个数与下标都先过范围校验再使用：
     * 错误实现可能把 *built 写成任意值或把 order[] 填成越界下标，
     * 直接索引会造成访问违例 → 沙箱判 Runtime Error 而非答案错误（harness-spec §6）。 */
    if (cnt < 0) cnt = 0;
    if (cnt > MAXT) cnt = MAXT;
    for (i = 0; i < cnt; i++) {
        if (order[i] < 0 || order[i] >= n) printf("? ");
        else printf("%s ", name[order[i]]);
    }
    printf("%s\n", (rc < 0) ? "CYCLE" : "OK");
    return 0;
}