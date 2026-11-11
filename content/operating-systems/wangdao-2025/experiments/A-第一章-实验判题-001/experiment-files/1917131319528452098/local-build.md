# 实验 1.1 本地完整验证（构建 Minix 内核）

> **平台自动判分的只是「make 依赖图重建顺序」这一个概念子任务。**
> **AC 不等于内核构建成功。** 真实构建必须在本地 MIPS 交叉编译环境完成，步骤如下。
> 子任务与真实任务的关系：make 的合法重建顺序就是目标依赖图的拓扑序，
> 判的是 Makefile 语义本身；但它**不能**替代交叉编译器路径配置、链接脚本、镜像生成与 gxemul 启动验证。

## 0. 内容来源缺口（如实记录）

工作区**没有** `include.mk` 与顶层 Makefile 的 Listing 原文：
实测 `question.id=1917131319528452098` 的 `originalCode` 仅 **43 字节垃圾**、`answer` 仅 **48 字节**，
`content` 中只有对 `Listing 1: 顶层 Makefile`、`Listing 2: include.mk` 的**引用**，没有 Listing 正文。

因此：
- 平台判分子任务里的依赖图（目标名 `vmlinuz` / `modules` / `objects` / `kernel.o` / `lib.o`）
  是**按 make 公开语义构造的等价示例**，目标名取自工作区说明中真实出现的词，
  **不是北航原 Makefile 的转录**；
- 下面的构建步骤依据工作区 `question.content` 的实验任务原文（任务 1–5）与 make/交叉编译的公开语义给出，
  变量名与目录名以你手上的源码树为准。

## 1. 前置条件

- MIPS 交叉工具链（北航实验常用 `mips-linux-gnu-gcc` / `mips-linux-gnu-ld`，
  或指导指定的 `mips_4KC-` 前缀工具链），已安装且可在命令行直接调用。
- gxemul（用于启动 `oldtestmips` 机器）。
- MOS/Minix 内核源码树，含顶层 `Makefile`、`include.mk`、`tools/scse0_3.lds`、各模块子目录。

## 2. 阅读和理解顶层 Makefile（实验任务 1）

阅读 `Listing 1: 顶层 Makefile`，弄清三件事：
1. **变量定义**：`drivers_dir`、`boot_dir` 等目录变量指向哪些模块；
2. **`modules` 与 `objects`**：`modules` 通常是要递归进入构建的子目录列表，
   `objects` 是最终要链接进内核的目标文件集合。二者的区别决定了构建是「递归 make」还是「一次性链接」；
3. **`vmlinuz` 目标**：它的依赖是谁、recipe 里调用了什么（通常是 `$(LD) -T tools/scse0_3.lds ...`）。

**这就是平台子任务判的东西**：把「目标 → 依赖」关系读出来，被依赖者必须先重建。
子任务里 `dep[i][j]=1` 表示目标 i 依赖目标 j，输出的是合法重建顺序；
有循环依赖时 make 会报错，子任务对应输出 `CYCLE` 并给出已构建的部分进度。

## 3. 阅读和理解 `include.mk`（实验任务 2）

阅读 `Listing 2: include.mk`，弄清四个变量：

| 变量 | 作用 | 填错的后果 |
| --- | --- | --- |
| `CROSS_COMPILE` | 交叉工具链前缀（如 `mips-linux-gnu-`） | 找不到编译器，或误用宿主 x86 gcc 生成 x86 目标文件 |
| `CC` | 通常定义为 `$(CROSS_COMPILE)gcc` | 编译出错误架构的 `.o` |
| `CFLAGS` | 编译选项（`-mips3`/`-march=4kc`、`-nostdlib`、`-fno-builtin`、`-I` 头文件路径等） | 指令集不匹配、误链宿主 libc、头文件找不到 |
| `LD` | 链接器（`$(CROSS_COMPILE)ld` 或 `$(CROSS_COMPILE)gcc`） | 链接阶段报「file format not recognized」 |

## 4. 修改 `include.mk`（实验任务 3，核心交付物）

1. 把 `CROSS_COMPILE` 改成你本机交叉工具链的**真实前缀**。先确认工具链存在：
   ```bash
   which mips-linux-gnu-gcc mips-linux-gnu-ld
   mips-linux-gnu-gcc --version
   ```
   若工具链装在非标准路径，`CROSS_COMPILE` 要写成**绝对路径 + 前缀**（如 `/opt/mips/bin/mips-linux-gnu-`）。
2. 确认 `LD` 指向同一套工具链的链接器（不要混用宿主 `ld`）。
3. 不要顺手改 `CFLAGS` 里的架构选项 —— 那属于实验 1.3/1.4 的范围。

## 5. 执行构建（实验任务 4）

```bash
make clean          # 清掉可能残留的 x86 目标文件，避免混链
make                # 或 make vmlinuz
```

观察构建过程：
- 每个模块是否按依赖顺序被进入（先 `lib`/`boot` 等被依赖者，最后链接 `vmlinuz`）；
- 是否出现 `No rule to make target` → 依赖名写错或文件缺失；
- 是否出现 `circular dependency` → 依赖图有环（**平台子任务的 `CYCLE` 分支对应的就是这种情况**）；
- 是否出现 `file format not recognized` → `CROSS_COMPILE`/`LD` 仍指向宿主工具链。

调试手段：
```bash
make -n             # 只打印将要执行的命令，不真的执行
make -p             # 打印 make 解析出的完整规则与变量数据库
make --debug=b      # 打印每条规则为何被判定为需要重建
```
`make -p` 输出的依赖图，就是平台子任务让你手写拓扑序的那张图。

## 6. 验证构建结果（实验任务 5）

```bash
ls -l gzemul/vmlinuz
file gzemul/vmlinuz                       # 应显示 ELF 32-bit MSB/LSB executable, MIPS
mips-linux-gnu-readelf -h gzemul/vmlinuz  # Machine 应为 MIPS，Entry point 应为内核入口
gxemul -E oldtestmips -d gzemul/vmlinuz   # 具体命令以实验指导为准
```

验证点：
1. `gzemul/` 目录下**确实生成了 `vmlinuz`**；
2. `file` / `readelf -h` 确认是 **MIPS** 架构而不是 x86；
3. gxemul 能启动并看到内核串口输出 → 镜像可运行；
4. 若 gxemul 立刻退出或无输出，转实验 1.3（段加载地址）与 1.4（启动汇编）排查。

## 7. 提交物

- 修改后的 `include.mk`（`CROSS_COMPILE` / `LD`）；
- `make` 的完整构建日志；
- `ls -l gzemul/vmlinuz`、`file`、`readelf -h` 的输出；
- gxemul 启动记录。

平台侧子任务按 `PASS` 记完成（概念子任务真实可判、可通过）；
**真实构建成功与否由上述本地证据判定，不由平台 AC 判定。**