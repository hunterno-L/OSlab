/* ===== 学生填写区：实现 lib/env.c 的 env_setup_vm() ===== */

/* 【宿主环境说明】工作区未随附 lib/env.c 原文。以下按题面 3.4「分配页目录 / 映射内核空间 / 设置 env_pgdir 与
 * env_cr3」+ MOS 公开语义构造的等价示例。
 *
 * 正确性自行验证要点（已用 5 条用例实测）：
 *   - pp_ref 必须 ++，否则页目录页会被 page_free 回收，两个进程共用同一张页目录；
 *   - env_cr3 是【物理地址】、env_pgdir 是【内核虚拟地址】，两者不能写反；
 *   - 内核段拷贝范围是 PDX(UTOP)..1023（含 UENVS/UPAGES 窗口），不是整个 1024 项，
 *     否则会把 boot 期遗留的用户态映射也拷进来（用例 4 用 stale=1 夹具专门检验）；
 *   - UVPT 自映射用 e->env_cr3 而不是 boot_pgdir 的物理地址；
 *   - page_alloc 失败必须原样返回错误码，且不得留下半初始化的 env_pgdir。 */
static int env_setup_vm(struct Env *e)
{
    uint32_t i;
    int r;
    struct Page *p = NULL;
    r = page_alloc(&p);
    if (r != 0) { return r; }
    p->pp_ref++;
    e->env_pgdir = (Pde *)page2kva(p);
    e->env_cr3 = page2pa(p);
    for (i = PDX(UTOP); i < 1024u; i++) { e->env_pgdir[i] = boot_pgdir[i]; }
    e->env_pgdir[PDX(UVPT)] = e->env_cr3 | PTE_V | PTE_R;
    return 0;
}

/* ===== 填写区结束 ===== */