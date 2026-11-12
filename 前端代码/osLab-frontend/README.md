# ZhiCore 前端

Vue 3 + TypeScript 教学平台界面，项目介绍与团队信息见 [总 README](../../README.md)。依赖清单为 [package.json](package.json)，具体安装版本锁定于 [package-lock.json](package-lock.json)。

## 开发

Node.js 18+、npm 9+，后端默认 `http://localhost:8121`。在本目录运行：

```bash
npm ci
npm run serve -- --port 8081
npm run lint -- --no-fix
npm run test:contracts
npm run build
```

`VUE_APP_API_TARGET` 设置开发 `/api` 代理，默认 `http://localhost:8121`；生产 Nginx 同源代理到后端服务。只有以 `VUE_APP_` 开头的变量会进入前端构建，绝不能用此前缀存放密钥。

仓库根目录的 Windows 启动脚本读取根 `.env`；单独运行 Vue CLI 时需先导入变量，详见 [环境配置](../../docs/environment.md)。切换分支后遇到旧模块解析错误，重启开发服务并等待编译完成。

## 结构

- `src/views/`：学生学习、实验、教师工作台与管理页面。
- `src/components/`、`src/composables/`：共享界面、课程与导师状态。
- `src/api/`、`generated/`：手写封装与 OpenAPI 客户端。
- `public/`：产品运行所需的静态资源，含兼容算法演示，不是可随意删除的构建缓存。
- `tests/`：独立回归脚本与必要 fixture。

需要重新生成 API 客户端时，先在可信开发环境开启后端 API 文档，再使用锁定依赖提供的 `openapi` 命令。不要用线上文档或带凭据的 URL 生成并提交代码。
