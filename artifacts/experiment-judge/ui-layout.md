# 实验工作台布局记录

## 基线与参考

- 变更前文件：`ViewQuestionView.before.vue`，1238 行，SHA-256 `3FCBDFE3FE644F9A52D3E9E3A099C445A33EB67F1FD581E09427863E4F018F16`。该文件包含已有用户修改。
- 已实际查看计划所列外部截图：左侧说明独立滚动，右侧编辑器与结果上下分区。本次仅借鉴分区，不新增图中功能。
- 12ui 账户连接未完成，未生成设计稿；按本轮任务中的参考图保守实现。impeccable context 引擎下载失败，已直接读取 PRODUCT.md、DESIGN.md 和相关指南。

## 实现与边界

- 仅追加 ViewQuestionView.vue 的 scoped 样式：宽屏说明与结果独立滚动、窄屏纵向流、说明排版与代码和表格横向滚动、触屏控件最小高度。令牌仅定义在本页，未修改共享主题文件。
- 历史阶段：布局首次完成时，template 与完整 script setup 均与基线相同；当时 script SHA-256 为 `6227cf63a23767fc0feb8a6963e3a14f169db189f06a45634a2bd8a4796e83a8`。随后主对话接入 LOCAL_BUILD 的展示与提交禁用，本轮修复仅修改 scoped CSS。当前相对基线的全部差异见 `ui-layout.diff`，其中包含主对话的模式接入。
- 历史阶段：布局首次完成时未接入 judgeMode；之后主对话基于实际 VO 字段接入 LOCAL_BUILD 提示、按钮禁用及提交入口保护。

## 静态检查

- `npm run lint -- --no-fix`：0；168 条警告与变更前相同。
- `ui-guideline-contracts.js`、`workbench-layout-contracts.js`、`design-system-contracts.js`、`ai-conversation-contracts.js`、`question-answer-context.js`：均退出 0，变更前后均通过。
- 前端 dev server 已编译，`http://127.0.0.1:8081/`。

## 历史阶段：首次浏览器检查

当时等待主对话确认 8121 后端健康，再检查真实页面与截图。Browser 插件不可用，使用已有的工作区 Playwright 运行时；不伪造登录状态或实验数据。提交、沙箱、图谱和导师行为以实际操作结果为准。

当时的真实路由检查：使用已安装 Playwright 1.62.1 与系统 Edge，访问 `/experiments/code/1908449571747745793` 后重定向到 `/user/login`，登录页正常且未捕获控制台错误。当时无已认证会话，尚无工作台截图或交互结论；后续已使用独立测试账户完成下述真实页面检查。

## 真实浏览器验收（8122 独立库）

- 标准 ESLint 开发配置编译成功；使用 Playwright 1.62.1 / 系统 Edge，通过测试平台账号真实登录。1.1、2.1、1.4 路由及导入内容正常显示；无 pageerror。
- 320、390、1024、1440 视口下三题 document.scrollWidth 等于 viewport 宽度；1440 左侧说明可独立滚动，代码块 1675px 内容在 550px 容器中自身横滚。表格样本宽 550px，与容器同宽，未触发横向溢出边界。
- 200% CSS zoom 截图可见；另用 720x450 模拟 1440x900 在浏览器 200% 缩放时的有效 CSS 视口，页面宽 720、单列、无横向溢出。这不等同于实际浏览器缩放开关测试。
- 1.1 概念子任务与 AC 不等于内核构建成功提示可见；1.1 和 2.1 提交按钮可用；1.4 本地 MIPS 提示可见、按钮为原生 disabled，分发 click 后捕获提交 POST 数 0。未提交自动实验。
- 2.1 Monaco 编辑、刷新后草稿恢复、C→C++→C 后草稿仍在；图谱标签显示 canvas；导师“只填入”打开面板并在导师输入框保留问题，未发送模型请求。
- 首轮发现：1.4 禁用按钮与启用主按钮同样深色，缺少清楚的视觉禁用态；其原生 disabled 实际有效。该问题已在下述复测中修复。
- 局限：未发起自动评测，Docker 真实判题、提交轮询和 AC 未验证。截图见 screenshots；已查看桌面、320、390、本地模式与概念说明截图。

## CSS 修复复测

- Arco `.arco-tabs` 原有 `overflow: hidden` 阻止说明区标签粘性定位；仅在说明区将该祖先改为 `overflow: visible`。1440px 真实页面滚动 1000px 前后，标签栏顶部均为 242.19px；标签仍可点击并切换图谱。
- 本地构建主按钮使用浅灰底与深灰字作为禁用态；浏览器确认原生 `disabled=true`、`cursor: not-allowed`，尝试派发点击未产生提交 POST。
- 320px 页面宽度与文档宽度均为 320px；未捕获页面脚本错误。截图：`screenshots/fix-desktop-sticky.png`、`screenshots/fix-disabled-desktop.png`、`screenshots/fix-disabled-320.png`，已逐张查看。
- 修复仅改变 scoped CSS，未改主对话新增的 template/script。当前 script SHA-256 为 `70164ea56da9a98c3cbb3181b8156914c9b6bff29269779008dc3effb2cf913e`；`node tests/workbench-layout-contracts.js` 退出 0。最终 lint/build 由主对话运行。
