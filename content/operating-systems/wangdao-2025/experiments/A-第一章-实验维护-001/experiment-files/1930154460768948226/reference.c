#include <stdio.h>
int main(void) {
    char c;
    long n;
    if (scanf("%c %ld", &c, &n) != 2) return 0;
    printf("char=%c dec=%ld hex=%lx\n", c, n, (unsigned long)n);
    return 0;
}
