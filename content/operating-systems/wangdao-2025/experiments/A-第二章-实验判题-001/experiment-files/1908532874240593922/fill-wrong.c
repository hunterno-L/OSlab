/* ===== 学生填写区：实现 mm/pmap.c 的 mips_detect_memory() ===== */

/* 典型错误：把 CMOS 的 KB 读数直接当成字节数写进 basemem/extmem，
 * 忘了乘 1024，于是 maxpa 与 npage 全部缩小 1024 倍。 */
void mips_detect_memory(void)
{
    basemem = cmos_basemem_kb;
    extmem = cmos_extmem_kb;
    maxpa = basemem + extmem;
    npage = maxpa / BY2PG;
}