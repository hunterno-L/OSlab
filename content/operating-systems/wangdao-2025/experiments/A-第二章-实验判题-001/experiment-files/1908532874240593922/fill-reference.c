/* ===== 学生填写区：实现 mm/pmap.c 的 mips_detect_memory() ===== */

/* 单位换算：KB -> 字节乘 1024；字节 -> 页数除 BY2PG。
 * 对应真实源码 maxpa = 0x4000000; basemem = 0x4000000; extmem = 0;
 * npage = maxpa/4096（此处 CMOS 读数由驱动给出，65536 0 即官方 64MB 场景）。 */
void mips_detect_memory(void)
{
    basemem = cmos_basemem_kb * 1024u;
    extmem = cmos_extmem_kb * 1024u;
    maxpa = basemem + extmem;
    npage = maxpa / BY2PG;
}