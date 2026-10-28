#include <stdio.h>
int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 0;
    printf("free_head=0\n");
    printf("free_list=");
    for (int i = n - 1; i >= 0; i--) printf(i == n - 1 ? "%d" : " %d", i);
    printf("\n");
    return 0;
}
