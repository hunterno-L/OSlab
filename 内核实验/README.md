# 内核实验（xv6）

`xv6-riscv/` 是上游 [mit-pdos/xv6-riscv](https://github.com/mit-pdos/xv6-riscv) 的干净快照，提交 `fa2ddcb`（2026-11-05），共 82 个文件。
判分时把它整份复制一份，再用学生提交的文件覆盖其中一个源码文件，编译后跑 QEMU，按输出标记判定。

- 平台侧接口：`后端代码/osLab-backend/.../kernel/KernelLabController.java`，路径 `/api/kernel/*`
- 前端页面：`前端代码/osLab-frontend/src/views/kernel/`
- 提交记录表：`数据库文件/oslab_upgrade_v15_kernel.sql` → `kernel_submission`
- 本机命令行判分：`judge.ps1`

问题清单、两端需求、环境准备与数据库内容全部在
[`../docs/内核实验-问题清单与部署需求.md`](../docs/内核实验-问题清单与部署需求.md)。

编译（Ubuntu/WSL，需要 `gcc-riscv64-linux-gnu` 与 `qemu-system-misc`）：

```bash
cd xv6-riscv
make TOOLPREFIX=riscv64-linux-gnu- -j2
make TOOLPREFIX=riscv64-linux-gnu- fs.img
make TOOLPREFIX=riscv64-linux-gnu- qemu
```

退出 QEMU：`Ctrl-A` 然后 `X`。
