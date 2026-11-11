/* ===== 学生填写区：实现 readelf.c 的 readelf()，输出所有 Section Header 的序号与地址 ===== */
/* 典型错误解：偏移计算漏乘 e_shentsize，写成 e_shoff + i。
 * 这是把 Section Header Table 当成「字节数组」而不是「定长记录数组」的经典错误：
 * 只有第 0 个 Section Header 恰好正确，其余全部读到错位字节。 */
static void readelf(u_char *binary, int size)
{
    Elf32_Ehdr *ehdr = (Elf32_Ehdr *)binary;
    Elf32_Shdr *shdr;
    uint32_t off;
    int i;
    if (size < (int)sizeof(Elf32_Ehdr)) return;
    for (i = 0; i < (int)ehdr->e_shnum; i++) {
        off = ehdr->e_shoff + (uint32_t)i;   /* BUG: 漏乘 e_shentsize */
        if (off + (uint32_t)sizeof(Elf32_Shdr) > (uint32_t)size) return;
        shdr = (Elf32_Shdr *)(binary + off);
        printf("%x:d:0x%x\n", i, (unsigned)shdr->sh_addr);
    }
}