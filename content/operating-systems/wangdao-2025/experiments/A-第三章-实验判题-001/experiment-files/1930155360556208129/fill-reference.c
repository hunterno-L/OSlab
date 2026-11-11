/* ===== 学生填写区：实现 lib/env.c 的 load_icode() ===== */

/* 【宿主环境说明】工作区未随附 lib/env.c 原文。以下按题面 3.7「设 PC 为 ELF 入口 / 为初始栈分配页」+ MOS 公开语义
 * 构造的等价示例。
 *
 * 正确性自行验证要点（已用 5 条用例实测）：
 *   - load_elf 失败必须立即返回：用例 4 只给 4 页物理内存，第 3 个数据页分配失败，
 *     此时 cp0_epc 必须仍是 env_alloc 清零后的 0，栈页也不得被映射；
 *   - cp0_epc 原样等于 entry，不裁剪：用例 5 传 4294967295 也必须原样写入；
 *   - 栈页映射在 USTACKTOP-BY2PG = 1073737728，【不是】USTACKTOP 本身
 *     （USTACKTOP 是栈顶指针，指向栈页之后的那个字节，属于内核段）；
 *   - 栈页权限 PTE_R|PTE_V，pp_ref 恒为 1（page_insert 已负责递增，不再额外 ++）；
 *   - regs[29] = USTACKTOP = 1073741824：用例 5 先用 X 把 sp 污染成 0xDEADBEEF，
 *     只有真正赋值才能通过（env_alloc 预设的 sp 不能拿来蒙混）。 */
static void load_icode(struct Env *e, uint32_t entry)
{
    struct Page *p = NULL;
    if (load_elf(e, load_icode_mapper) != 0) { return; }
    e->env_tf.cp0_epc = entry;
    if (page_alloc(&p) != 0) { return; }
    if (page_insert(e->env_pgdir, p, USTACKTOP - BY2PG, PTE_R | PTE_V) != 0) { return; }
    e->env_tf.regs[29] = USTACKTOP;
}

/* ===== 填写区结束 ===== */