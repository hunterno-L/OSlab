# 维护工具

从仓库根目录运行，Node 工具需 Node.js 18+；PowerShell 工具建议 PowerShell 7。工具所需第三方 Node 依赖使用前端的锁定依赖，不额外安装未锁定包。

## 只读校验

```bash
node scripts/validate-course-content.mjs
node scripts/verify-page-replacement-content.mjs
node scripts/check-content-consistency.mjs
node scripts/check-experiment-mappings.mjs
node --test scripts/tests/member-package.test.mjs
node scripts/check-repository.mjs
```

`check-member-package.mjs` 检查独立内容交付包；`verify-experiment-harness.mjs <实验目录>` 使用本机 GCC 编译初始、参考和错误三个版本并验证用例。`probe-harness-robustness.mjs` 支持额外探针，先查看其参数说明。

`check-repository.mjs` 检查 Git 跟踪文件、文档链接、部署输入、环境变量接线和常见密钥模式；依赖前端 `npm ci` 安装的 YAML 解析器。新增文件先 `git add` 再检查，临时目录中的同名文件不会被当作已交付输入。

## 内容导入与构建

- `import-experiment-package.mjs`：通过平台接口导入实验维护包，先运行 `--help`，先预览再确认写入；只在已授权的数据环境使用。
- `assemble-harness.mjs <out.c> <片段...>`：合并 C 验证程序片段。
- `derive-harness-variants.mjs <实验目录>`：根据填写区生成参考/错误变体，会修改目标目录文件。

已删除仅用于历史批次、依赖临时工作区或私人配置的生成/导出脚本。现有数据库迁移作为交付输入保留，后续修订使用新迁移，不重写历史迁移。

## 开发与联调

- `lib/import-env.ps1`：按数据读取 `.env`，供本机与 Compose 启动脚本使用，不执行文件内容。
- `tests/environment.test.ps1`：环境文件与启动配置回归。
- `smoke-learning.ps1`：学习闭环接口检查，详见参数帮助。
- `dev/`：隔离开发环境的演示账号、AI、检索与学习验证；`sandbox_stub.py` 仅为测试替身，不能作为真实判题服务。

演示与接口工具可能创建测试账号、作答或任务。显式设置目标和凭据，不使用生产库。运行报告输出在忽略目录 `artifacts/`，实际 Key、口令与登录会话不入库。
