# 实验 1.1 构建 Minix 内核

## 实验背景

在本实验中，你将学习如何使用 Makefile 构建 Minix 内核。通过修改 `include.mk` 文件中的交叉编译器路径，确保编译过程能够正确执行，并生成 Minix 内核文件。

## 知识要点

1. **Makefile 基础**：
   - 理解 Makefile 的基本结构和语法。
   - 了解变量定义和使用（如 `drivers_dir`、`boot_dir` 等）。
   - 理解目标（如 `all`、`vmlinuz`）及其依赖关系。
2. **交叉编译**：
   - 理解交叉编译的概念及其重要性。
   - 学习如何设置交叉编译工具链路径。
3. **模块化构建**：
   - 理解模块化构建的原理。
   - 学习如何通过 Makefile 管理多个模块的构建。
4. **链接脚本**：
   - 理解链接脚本的作用及其在内核构建中的重要性。

**扩写（make 公开语义）**：make 用时间戳判定「要不要重建」，用依赖图的**拓扑序**判定「按什么顺序重建」。`modules` 是递归 make 的子目录列表，`objects` 是链接进内核的 `.o` 集合，二者不能混。有环时 make 报 `circular dependency` 并停止，对应子任务的 `CYCLE` 分支。`CC=$(CROSS_COMPILE)gcc`、`LD=$(CROSS_COMPILE)ld`。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `Makefile（顶层）` | 定义目标与依赖 | modules、objects、vmlinuz |
| `include.mk` | 交叉工具链与编译选项 | CROSS_COMPILE、CC、CFLAGS、LD |
| `tools/scse0_3.lds` | 链接脚本，决定段加载地址 | SECTIONS、. = 0x80000000、_end |
| `gzemul/vmlinuz` | 构建产物 | ELF header e_machine = MIPS |

**内容来源缺口**：顶层 Makefile 与 `include.mk` 的**原指导 Listing 未随工作区提供**（`originalCode` 实测 43 字节、`answer` 48 字节，均无效）。子任务依赖图是**按 make 公开语义构造的等价示例**，非北航原 Makefile 转录。

## 实验任务

**实验任务**

1. **阅读和理解顶层 Makefile**：
   - 阅读 `Listing 1: 顶层 Makefile`，理解各个变量的定义和使用。
   - 理解 `modules` 和 `objects` 的定义及其在构建过程中的作用。
   - 理解 `vmlinuz` 目标的构建过程。
2. **阅读和理解 `include.mk` 文件**：
   - 阅读 `Listing 2: include.mk`，理解 `CROSS_COMPILE`、`CC`、`CFLAGS` 和 `LD` 变量的定义。
   - 理解这些变量在交叉编译过程中的作用。
3. **修改 `include.mk` 文件**：
   - 修改 `include.mk` 文件中的 `CROSS_COMPILE` 变量，确保交叉编译器的路径正确。
   - 确保 `LD` 变量指向正确的链接器。
4. **执行构建命令**：
   - 在终端中执行 `make` 命令，开始构建 Minix 内核。
   - 观察构建过程，确保没有错误发生。
5. **验证构建结果**：
   - 检查 `gzemul` 目录下是否生成了 `vmlinuz` 文件。
   - 验证 `vmlinuz` 文件是否能够正确运行。

**每步验证**：步骤 1–2 能口述 vmlinuz 依赖谁；步骤 3 改好 `include.mk`，`which ${CROSS_COMPILE}gcc` 验证；步骤 4 `make -n` 看命令、`make -p` 看依赖图；步骤 5 `readelf -h` 确认 Machine=MIPS。

## 本平台判题说明

- 判题方式：**Tier C**：概念子任务自动判分，真实构建仍需本地。**平台自动判分的是 make 依赖图的合法重建顺序（拓扑序）；AC 不等于内核构建成功。** 见下方「本地完整验证」。
- 你需要填写：`int build_order(int n, int dep[MAXT][MAXT], int order[MAXT], int *built)`（`starter.c` 填写区）。`dep[i][j]=1` 表示 i 依赖 j；顺序写进 `order[0..*built-1]`（存下标），无环返回 0，有环返回负值并把**已构建的部分进度**写进 `*built`。
- 判题输入：首行目标数 n（1..64），随后 n 行「目标名 依赖个数k 依赖下标...」（目标名 ≤15 字符，0<=k<=n，下标 0..n-1）。
- 判题输出：已重建目标名空格分隔，后接 `OK`；有环则接 `CYCLE`。多目标同时就绪时**取编号最小者**，不是名字字典序。
- 宿主环境适配：无 MIPS 依赖（`MAXT=64`、`NAMELEN=16`）。驱动 `order[MAXT] = {0}` 已初始化，把 `*built` 钳到 `[0,MAXT]`、校验 `order[i] ∈ [0,n)`，越界打印哨兵 `?`，使错误实现被判答案错误而非 Runtime Error。
- 与真实内核的关系：判的量就是 `make -p` 打印的依赖图拓扑序；工具链路径、链接脚本、镜像生成与 gxemul 启动仍须本地完成。
- 内容来源标注：原指导 Listing **未随工作区提供**，依赖图为按 make 公开语义构造的**等价示例**，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 make 依赖图重建顺序。输入首行目标数 n（1..64），随后 n 行「目标名 依赖个数k 依赖下标...」（目标名 ≤15 字符，0<=k<=n，依赖下标 0..n-1）。dep[i][j]=1 表示 i 依赖 j。输出为已重建目标名（空格分隔，就绪时取编号最小者）后接 OK；存在循环依赖则接 CYCLE。
```

## 本地完整验证

完整步骤见同目录 `local-build.md`。要点：① `which mips-linux-gnu-gcc` 确认工具链；② 改 `include.mk` 的 `CROSS_COMPILE` 与 `LD`；③ `make clean && make`；④ `readelf -h` 确认 Machine=MIPS；⑤ `gxemul -E oldtestmips -d gzemul/vmlinuz` 看串口输出。

## 常见错误

1. 按输入顺序输出、不看依赖（`wrong.c` 的做法）：`mos-module-chain` 输出 `vmlinuz kernel.o lib.o modules objects`，链接早于 `.o` 就绪。
2. 有环仍返回成功：`three-cycle` 须输出 `CYCLE`，`self-loop-with-buildable-prefix` 须输出 `objects CYCLE`——裸 `CYCLE` 会丢掉部分进度。
3. 就绪集合按名字字典序挑：`all-independent` 应答 `all boot mm lib`（下标序），按名字排会得 `all boot lib mm`。
4. 用 DFS 后序冒充拓扑序：`kahn-min-index-vs-dfs-postorder` 上 Kahn 得 `c d b all`、DFS 后序得 `d b c all`。
