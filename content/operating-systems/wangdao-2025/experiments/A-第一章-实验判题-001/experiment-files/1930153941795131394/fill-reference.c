/* ===== 学生填写区：实现 readelf.c 的 readelf()，输出所有 Section Header 的序号与地址 ===== */
/* 参考解：与 MOS readelf.c 的目标实现一致。
 * 第 i 个 Section Header 的文件偏移 = e_shoff + i * e_shentsize（必须乘 e_shentsize，
 * 因为 Section Header Table 是定长记录数组，记录长度由 e_shentsize 给出，不一定等于 40）。
 * 输出格式逐字采用原文 "%x:d:0x%x\n"。
 * 额外做 size 边界检查：越界不读，避免错误 e_shoff/e_shnum 造成越界访问。 */
static void readelf(u_char *binary, int size)
{
    Elf32_Ehdr *ehdr = (Elf32_Ehdr *)binary;
    Elf32_Shdr *shdr;
    uint32_t off;
    int i;
    if (size < (int)sizeof(Elf32_Ehdr)) return;
    for (i = 0; i < (int)ehdr->e_shnum; i++) {
        off = ehdr->e_shoff + (uint32_t)i * (uint32_t)ehdr->e_shentsize;
        if (off + (uint32_t)sizeof(Elf32_Shdr) > (uint32_t)size) return;
        shdr = (Elf32_Shdr *)(binary + off);
        printf("%x:d:0x%x\n", i, (unsigned)shdr->sh_addr);
    }
}