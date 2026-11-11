/* ===== 学生填写区：实现 lib/env.c 的 env_init() ===== */

/* 典型错误解：把遍历方向写成降序（i = NENV-1 .. 0）却仍用头插。
 * 这是题面「注意插入的顺序」最常踩的坑 —— 结果链表顺序整个反过来，
 * 变成 0 .. NENV-1，于是 env_alloc 第一次拿到的是 envs[0] 而不是 envs[NENV-1]。 */
static void env_init(void)
{
    int i;
    LIST_INIT(&env_free_list);
    for (i = (int)NENV - 1; i >= 0; i--) {
        envs[i].env_status = ENV_FREE;
        LIST_INSERT_HEAD(&env_free_list, &envs[i], env_link);
    }
}

/* ===== 填写区结束 ===== */