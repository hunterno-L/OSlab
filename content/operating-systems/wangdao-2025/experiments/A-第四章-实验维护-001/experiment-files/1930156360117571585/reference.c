#include <stdio.h>
int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 0;
    const char *s;
    switch (n) {
        case 1: s = "sys_putchar"; break;
        case 2: s = "sys_getchar"; break;
        case 3: s = "sys_exofork"; break;
        case 4: s = "sys_env_alloc"; break;
        case 5: s = "sys_yield"; break;
        case 6: s = "sys_ipc_try_send"; break;
        case 7: s = "sys_ipc_recv"; break;
        case 8: s = "sys_mem_alloc"; break;
        default: s = "unknown";
    }
    printf("%s\n", s);
    return 0;
}
