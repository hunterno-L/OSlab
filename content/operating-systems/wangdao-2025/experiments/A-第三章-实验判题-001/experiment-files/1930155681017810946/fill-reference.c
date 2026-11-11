/* ===== 学生填写区：在 init/init.c 中应用 ENV_CREATE_PRIORITY 宏创建两个用户进程 ===== */

/* 【宿主环境说明】工作区未随附 init/init.c 原文。以下两条宏调用是题面 3.9「实验任务 2」的【逐字原文】：
 *     ENV_CREATE_PRIORITY(user_A, 2);
 *     ENV_CREATE_PRIORITY(user_B, 1);
 * 唯一适配是把它们收进 init_user_envs() 函数，因为宿主沙箱是单文件程序、没有独立的
 * init.c 翻译单元；语句、宏名、镜像名、优先级数值与先后顺序均未改动。
 *
 * 为什么优先级是 2 和 1 而不是别的：题面明确指定。env_pri 在 MOS 里表示该进程
 * 一轮能连续运行的时钟滴答数（见实验 3.14 的 sched_yield），所以 user_A 比 user_B
 * 每轮多跑一倍时间。用 ENV_CREATE 代替 ENV_CREATE_PRIORITY 会让两个进程优先级都变成 1，
 * 这正是本实验要区分的典型错误。 */
static void init_user_envs(void)
{
    ENV_CREATE_PRIORITY(user_A, 2);
    ENV_CREATE_PRIORITY(user_B, 1);
}

/* ===== 填写区结束 ===== */