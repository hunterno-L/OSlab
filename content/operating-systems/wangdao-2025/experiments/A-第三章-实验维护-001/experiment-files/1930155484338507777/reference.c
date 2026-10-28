#include <stdio.h>
int main(void) {
    char name[128];
    int pri;
    if (scanf("%127s %d", &name, &pri) != 2) return 0;
    printf("pid=0 name=%s pri=%d\n", name, pri);
    return 0;
}
