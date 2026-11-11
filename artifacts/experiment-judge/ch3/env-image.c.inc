/* ---- 平台提供的进程镜像模型 + load_elf 宿主等价 ----
 *
 * 【宿主环境说明】工作区未提供 lib/elfloader.c、user 目录下的 .elf 与 include/elf.h 原文。
 * 真实 load_icode 调 load_elf(binary, size, &entry, e, load_icode_mapper) 解析 ELF32
 * 程序头，逐段回调 mapper。宿主沙箱里没有 ELF 文件，因此：
 *   - 段表（va/size/perm）与入口 entry 由 stdin 直接给出，装进下面的全局量；
 *   - load_elf 的「解析程序头」部分退化为遍历段表并逐段回调 mapper，
 *     回调签名、调用顺序、每段一次的语义与真实 load_elf 一致；
 *   - 段内容用 seg_binary 这块 4 页缓冲模拟 ELF 文件字节流（因此单段 size 上限 16384，
 *     驱动在边界上强制校验；真实 ELF 段可以更大，这是宿主沙箱的规模限制）。
 * 这是按 ELF/MOS 公开语义构造的等价示例，不是原文转录。
 */

#define MAXSEG 8u
#define SEGBYTES (BY2PG * 4u)

struct Seg { uint32_t va; uint32_t size; uint32_t perm; };

static MOS_UNUSED struct Seg seg_table[MAXSEG];
static MOS_UNUSED uint32_t seg_n;
static MOS_UNUSED uint32_t elf_entry;
static MOS_UNUSED unsigned char seg_binary[SEGBYTES];

/* include/env.h 等价前置声明：填写区之间会互相调用。
 * 带 MOS_UNUSED 是因为某个实验只填其中一个，另一个可能既不被使用也不被定义。 */
static MOS_UNUSED int load_icode_mapper(void *binary, uint32_t size, uint32_t va, int perm, struct Env *e);
static MOS_UNUSED void load_icode(struct Env *e, uint32_t entry);

typedef int (*mapper_t)(void *, uint32_t, uint32_t, int, struct Env *);

/* 真实 load_elf 的宿主等价：逐段回调 mapper，段序与段表一致。
 * mapper 返回非 0 时立即停止（真实 load_elf 同样会把错误上抛）。 */
static MOS_UNUSED int load_elf(struct Env *e, mapper_t mapper)
{
    uint32_t i;
    int r;
    if (!env_in_range(e)) { return -E_BAD_ENV; }
    for (i = 0; i < seg_n && i < MAXSEG; i++) {
        r = mapper((void *)seg_binary, seg_table[i].size, seg_table[i].va,
                   (int)seg_table[i].perm, e);
        if (r != 0) { return r; }
    }
    return 0;
}