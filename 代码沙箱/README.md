# 内部代码沙箱

`server.py` 提供 `GET /health` 与 `POST /executeCode`，使用 `SANDBOX_AUTH_SECRET` 鉴权；`runner.py` 在一次性 Docker Worker 内编译与运行 Java、C、C++、Go。Python 部分只使用标准库，无需安装 pip 包。

镜像由 [Dockerfile](Dockerfile) 安装 Python 3、Docker CLI、OpenJDK 17、GCC/G++ 和 Go。由仓库 [Compose](../deploy/docker-compose.yml) 一起构建和启动，默认不公开沙箱端口。

| 环境变量 | 默认值与用途 |
| --- | --- |
| `SANDBOX_AUTH_SECRET` | 必填；后端 `CODESANDBOX_REMOTE_AUTH_SECRET` 必须一致 |
| `SANDBOX_PORT` | `8090`，内部监听端口 |
| `SANDBOX_RUNNER_IMAGE` | `oslab-judge-sandbox:local`，Worker 镜像 |
| `SANDBOX_MAX_CONCURRENT` | `2`，最大同时运行数 |

沙箱 API 通过 Docker socket 启动 Worker，仅用于受控内部网络。Worker 不挂载 socket 或宿主目录、关闭网络、使用非 root 身份并限制 CPU、内存、进程数及运行时间。不要将沙箱 API 直接暴露公网。

本地概念验证不等于完整内核构建。标注 `LOCAL_BUILD` 的实验由后端拒绝在线自动评测，应按课程实验说明在配套环境运行。
