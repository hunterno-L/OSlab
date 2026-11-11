/* ===== 学生填写区：实现 lib/env.c 的 env_create() 与 env_create_priority() ===== */

/* 【宿主环境说明】工作区未随附 lib/env.c 原文。以下按题面 3.8「分配 PCB / 调 env_alloc / 调 load_icode / 设优先级」
 * + MOS 公开语义构造的等价示例，不是原文逐字转录。
 *
 * 正确性自行验证要点（已用 5 条用例实测，期望输出全部由本地 gcc 真实运行导出）：
 *   - env_alloc 失败必须立即返回：用例 4 先耗尽 64 个空闲 PCB，再 ENV_CREATE，
 *     就绪链表头/空闲PCB数/空闲页数都不得变化（不能把半个进程挂进调度链表）；
 *   - load_icode 必须真的被调用：用例 1/3 的 cp0_epc=入口、段内容、栈页映射都依赖它，
 *     只 env_alloc 不 load_icode 会让 epc 停在 0、段无映射；
 *   - env_pri 原样保存：用例 2 的 pri=5 / pri=0 / 默认 1 三种都要正确（0 是合法优先级）；
 *   - env_status 必须置 ENV_RUNNABLE(2)：env_alloc 预设的是 NOT_RUNNABLE(1)，不重写就调度不到；
 *   - 头插就绪链表：用例 2 创建序 63,62,61，链表序必须是逆序 61 62 63；
 *   - 空镜像守卫：用例 5 的 Z(size=0) 与 Y(binary=NULL) 都不得创建进程。
 *
 * 【宿主等价】binary/size 保留原始签名；宿主 load_icode 改用 stdin 段表+elf_entry，
 *   故 binary/size 仅用于「空镜像不创建进程」守卫（见 body 填写区说明）。 */
static void env_create_priority(void *binary, uint32_t size, int priority)
{
    struct Env *e = NULL;
    if (binary == NULL || size == 0) { return; }
    if (env_alloc(&e, 0) != 0) { return; }
    if (!env_in_range(e)) { return; }
    load_icode(e, elf_entry);
    e->env_pri = (uint32_t)priority;
    e->env_status = ENV_RUNNABLE;
    LIST_INSERT_HEAD(&env_sched_list, e, env_sched_link);
}

static void env_create(void *binary, uint32_t size)
{
    env_create_priority(binary, size, 1);
}

/* ===== 填写区结束 ===== */