/* ===== 学生填写区：ld SECTIONS 的段加载地址计算 ===== */
/* 参考解：ld 的通用顺序布局规则。
 * 每段先 ALIGN(align) 向上对齐，得到该段 VMA；随后 location counter 前进 size。
 * 全部段放完后 _end = ALIGN(PAGE_SIZE)，与内核按页管理内存的语义一致。
 * align==0 视为不对齐（ld 中 ALIGN(0) 无意义，此处显式定义避免除零）。
 * 全部运算用 uint32_t：MinGW LLP64 与 Linux LP64 下宽度一致（harness-spec §7）。 */
static uint32_t align_up(uint32_t a, uint32_t n)
{
    if (n == 0) return a;
    return ((a + n - 1u) / n) * n;
}

uint32_t layout_sections(uint32_t base, const struct Sec *secs, int n, uint32_t vma[])
{
    uint32_t cur = base;
    int i;
    for (i = 0; i < n; i++) {
        cur = align_up(cur, secs[i].align);
        vma[i] = cur;
        cur += secs[i].size;
    }
    return align_up(cur, PAGE_SIZE);
}