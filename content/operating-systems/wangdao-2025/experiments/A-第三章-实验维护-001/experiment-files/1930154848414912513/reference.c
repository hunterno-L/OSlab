#include <stdio.h>
int main(void) {
    int n;
    long id;
    if (scanf("%d %ld", &n, &id) != 2) return 0;
    printf("index=%ld\n", id % n);
    printf("valid=%d\n", id >= 0 ? 1 : 0);
    return 0;
}
