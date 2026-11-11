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
}