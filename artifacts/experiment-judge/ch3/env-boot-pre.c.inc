/* 操作系统实验 3.1：启动期内存初始化。
 * pages、envs 与 boot_pgdir 为指针，由 bump 分配器 alloc() 分配，
 * 与使用静态页数组的运行时分配模型区分。
 *
 * 等价适配（语义不变）：
 *   1. u_int/u_long -> uint32_t，保证 Windows(LLP64) 与 Linux(LP64) 判题结果一致；
 *   2. 物理内存用一维字节数组 phys[] 模拟，物理地址就是数组下标，
 *      于是 KADDR(pa)=phys+pa、PADDR(kva)=kva-phys，与真实内核线性映射语义相同；
 *   3. panic 不终止进程（沙箱把非零退出判为 Runtime Error）；
 *   4. 真实 alloc/boot_pgdir_walk 在内存耗尽时 panic 后继续执行，宿主 panic 是空操作，
 *      因此这里改为返回 NULL 并由调用方守卫，避免学生写错时段错误；
 *   5. 去掉 mips_vm_init 里的启动期 printf，改由检查驱动打印确定性观测量。
 */
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#define MOS_UNUSED __attribute__((unused))

#define BY2PG    4096u
#define PDMAP    (4u * 1024u * 1024u)
#define PTE_V    0x0001u
#define PTE_R    0x0002u
#define E_NO_MEM 4

#define PTE_ADDR(pte) ((uint32_t)(pte) & ~0xFFFu)
#define PDX(va)       (((uint32_t)(va) >> 22) & 0x3FFu)
#define PTX(va)       (((uint32_t)(va) >> 12) & 0x3FFu)
#define ROUND(a, n)   (((((uint32_t)(a)) + (n) - 1u) / (n)) * (n))
#define ROUNDDOWN(a, n) ((((uint32_t)(a)) / (n)) * (n))
#define bzero(p, n)   memset((p), 0, (n))

typedef uint32_t Pde;
typedef uint32_t Pte;

/* include/queue.h：struct Page / struct Env 的链表字段需要这两个宏 */
#define LIST_HEAD(name, type) \
    struct name { struct type *lh_first; }

#define LIST_ENTRY(type) \
    struct { struct type *le_next; struct type **le_prev; }

struct Page { int32_t pp_ref; LIST_ENTRY(Page) pp_link; };
LIST_HEAD(Page_list, Page);

#define NPAGE_MAX 256u

static MOS_UNUSED unsigned char phys[NPAGE_MAX * BY2PG];