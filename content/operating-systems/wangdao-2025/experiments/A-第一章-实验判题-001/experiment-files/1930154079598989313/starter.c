/* 操作系统实验 1.3：链接脚本的段地址计算。
 * 按题面规定逐段 ALIGN 对齐并顺序放置，末尾 _end 按页对齐。
 * 这是通用布局规则的教学示例；完整链接仍需在本地 MIPS 工具链验证。
 * 仅填写标记的学生区域。
 */
#include <stdio.h>
#include <stdint.h>

#define MAXS 16
#define PAGE_SIZE 4096u

struct Sec {
    char name[16];
    uint32_t size;
    uint32_t align;
};

/* ===== 学生填写区：ld SECTIONS 的段加载地址计算 ===== */
/* 从 base 起依次放置 n 个段：每段先把当前地址向上对齐到 secs[i].align，
 * 该地址即段 VMA 写入 vma[i]，然后当前地址前进 secs[i].size。
 * 返回全部段放完后向上对齐到 PAGE_SIZE 的 _end。align 为 0 时视为不对齐。 */
uint32_t layout_sections(uint32_t base, const struct Sec *secs, int n, uint32_t vma[])
{
    /* finish your code here */
    (void)base; (void)secs; (void)n; (void)vma;
    return 0;
}
/* ===== 填写区结束 ===== */

int main(void)
{
    uint32_t base, end;
    int n, i;
    struct Sec secs[MAXS];
    uint32_t vma[MAXS] = {0};   /* 驱动自有输出缓冲必须初始化，否则 -Werror=maybe-uninitialized 报错 */
    if (scanf("%u %d", &base, &n) != 2) return 0;
    if (n < 1 || n > MAXS) return 0;
    for (i = 0; i < n; i++) {
        if (scanf("%15s %u %u", secs[i].name, &secs[i].size, &secs[i].align) != 3) return 0;
    }
    end = layout_sections(base, secs, n, vma);
    for (i = 0; i < n; i++) printf("%s 0x%08x\n", secs[i].name, (unsigned)vma[i]);
    printf("_end 0x%08x\n", (unsigned)end);
    return 0;
}