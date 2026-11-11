/* ===== 学生填写区：实现 mm/pmap.c 的 mips_detect_memory() ===== */

/* 由 CMOS 读数初始化 maxpa / basemem / extmem / npage。
 * 单位约定：cmos_basemem_kb / cmos_extmem_kb 是 KB；
 *           maxpa / basemem / extmem 是字节；npage 是页数（每页 BY2PG 字节）。
 * 真实源码中 maxpa = 0x4000000、basemem = maxpa、extmem = 0、npage = maxpa/4096。 */
void mips_detect_memory(void)
{
    /* finish your code here */
}

/* ===== 填写区结束 ===== */

int main(void)
{
    if (scanf("%u %u", &cmos_basemem_kb, &cmos_extmem_kb) != 2) { return 0; }
    if (cmos_basemem_kb > 524288u || cmos_extmem_kb > 524288u) { return 0; }
    mips_detect_memory();
    printf("%u %u %u %u\n", maxpa, basemem, extmem, npage);
    return 0;
}