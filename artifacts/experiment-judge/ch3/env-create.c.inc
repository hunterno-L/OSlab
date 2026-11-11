/* ---- 平台提供的 lib/env.c env_create / env_create_priority 与 ENV_CREATE 宏
 *      （实验 3.8 考查函数本体时排除本片段；实验 3.9 只考查宏的应用，故保留） ----
 * MOS 公开语义：env_alloc 拿 PCB -> load_icode 装镜像 -> 置优先级 ->
 * env_status = ENV_RUNNABLE -> 头插进就绪链表 env_sched_list。
 * env_create 等价于优先级为 1 的 env_create_priority。
 *
 * 宿主等价：真实内核里 binary 是链接进内核的用户镜像字节数组（binary_user_A 等），
 * size 是其字节数；宿主没有真实 ELF，镜像段表/入口由 stdin 装入 env-image 段的全局量，
 * binary/size 保留原始签名并用于「镜像必须非空」的守卫。 */

static MOS_UNUSED unsigned char binary_user_A[64];
static MOS_UNUSED unsigned char binary_user_B[64];
static MOS_UNUSED unsigned char binary_user_C[64];

#define ENV_CREATE(x)             env_create(binary_##x, (uint32_t)sizeof binary_##x)
#define ENV_CREATE_PRIORITY(x, y) env_create_priority(binary_##x, (uint32_t)sizeof binary_##x, (y))

static MOS_UNUSED void env_create_priority(void *binary, uint32_t size, int priority)
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

static MOS_UNUSED void env_create(void *binary, uint32_t size)
{
    env_create_priority(binary, size, 1);
}