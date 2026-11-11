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
/* 典型错误解：忽略每段的 ALIGN 对齐、末尾 _end 也不按页取整。
 * 这正是「以为各段紧挨着排就行」的学生会写出的实现：
 * 在 base 与 size 恰好都对齐的场景下它碰巧正确，一旦 base 非对齐或 align 大于当前地址就错。 */
uint32_t layout_sections(uint32_t base, const struct Sec *secs, int n, uint32_t vma[])
{
    uint32_t cur = base;
    int i;
    for (i = 0; i < n; i++) { vma[i] = cur; cur += secs[i].size; }
    return cur;
}/* ===== 填写区结束 ===== */

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