/* ===== 学生填写区：在 init/init.c 中应用 ENV_CREATE_PRIORITY 宏创建两个用户进程 ===== */

/* 典型错误：用 ENV_CREATE 代替 ENV_CREATE_PRIORITY。
 * ENV_CREATE(x) 展开为 env_create(binary_x, size)，而 env_create 内部固定传优先级 1，
 * 于是两个进程的 env_pri 都是 1，丢掉了题面要求的「user_A 优先级 2、user_B 优先级 1」。
 * 进程确实被创建了、也就绪了，所以只看「有没有两个进程」的检查会误判为正确；
 * 本 harness 用 T 操作打印 env_pri，把这个缺陷暴露出来。 */
static void init_user_envs(void)
{
    ENV_CREATE(user_A);
    ENV_CREATE(user_B);
}

/* ===== 填写区结束 ===== */