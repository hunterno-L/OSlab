#include <stdio.h>
int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 0;
    printf("envs=%d\n", n);
    printf("free_list=");
    for (int i = 0; i < n; i++) printf(i ? " %d" : "%d", i);
    printf("\n");
    return 0;
}
