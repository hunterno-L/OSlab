/* ===== 学生填写区：实现 lib/env.c 的 env_init() ===== */

/* 【宿主环境说明】工作区未随附 lib/env.c 原文。以下按题面 3.2「初始化 envs 并把全部空闲 PCB
 * 加入 env_free_list，注意插入顺序，参考 lab2 的做法」+ MOS 公开语义构造的等价示例。
 *
 * 正确性自行验证：升序遍历 + LIST_INSERT_HEAD => 链表遍历顺序必为 NENV-1 .. 0。
 * 与第二章 page_init（同样升序 + 头插，得到降序空闲页号）逐项同构，已用 5 条用例实测。 */
static void env_init(void)
{
    int i;
    LIST_INIT(&env_free_list);
    for (i = 0; i < (int)NENV; i++) {
        envs[i].env_status = ENV_FREE;
        LIST_INSERT_HEAD(&env_free_list, &envs[i], env_link);
    }
}

/* ===== 填写区结束 ===== */