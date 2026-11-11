/* ---- 平台提供的 lib/env.c env_init（实验 3.2 考查时排除本片段） ----
 * 与第二章 page_init 同构：升序遍历 + LIST_INSERT_HEAD，
 * 因此空闲链表遍历顺序是 NENV-1 .. 0（题面「注意插入的顺序」）。 */

static MOS_UNUSED void env_init(void)
{
    int i;
    LIST_INIT(&env_free_list);
    for (i = 0; i < (int)NENV; i++) {
        envs[i].env_status = ENV_FREE;
        LIST_INSERT_HEAD(&env_free_list, &envs[i], env_link);
    }
}