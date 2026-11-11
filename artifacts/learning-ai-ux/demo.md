# ZhiCore AI 导师整合 — 5–8 分钟演示脚本

> 真实后端 + 真实模型 + 真实浏览器。演示账号 `student_demo1 / 123456`（学生）。
> 演示环境若未配置 embedding，回答会显示“降级回答（未使用语义检索）”；这是真实状态，不伪装成 AI 全链路成功。

## 0. 启动（演示前 2 分钟）

```bash
# 1) MySQL、Redis 已运行（本机 3306/6379）
# 2) 后端（若 8121 已有实例可跳过）
cd 后端代码/osLab-backend
JAVA_HOME=C:/Users/one/.jdks/ms-21.0.10 SERVER_PORT=8121 ./mvnw.cmd -q -DskipTests spring-boot:run

# 3) 前端
cd 前端代码/osLab-frontend
VUE_APP_API_TARGET=http://localhost:8121 NODE_OPTIONS=--max-old-space-size=2048 \
  npx vue-cli-service serve --port 8081
```

打开 `http://localhost:8081/user/login`，用 `student_demo1 / 123456` 登录；进入 `/home`。

## 1. 全局入口与停靠（约 40 秒）

1. 点击右下角 76px 头像按钮，面板从右侧停靠打开（约 460px）；**全局头部导航、搜索和用户菜单仍完整可见**。
2. 拖动面板左边缘，宽度在 400–560px 之间变化；刷新后宽度保留。
3. 点击“展开”，进入约 1100px 的宽阅读态；按 Esc 回到停靠态，消息与草稿仍在。
4. 结论：全站只有这一个 AI 入口，页面不再各自维护聊天。

截图：`screenshots/layout-dock-1440.png`、`screenshots/expanded-1440.png`

## 2. 真实问答与追问（约 1 分钟）

1. 在首页面板输入：`请用一句话解释函数依赖与候选键的关系，并说明我下一步该练什么。`
2. 等待真实模型返回；观察：
   - 顶部有模型名 `deepseek-flash`；
   - 若检索不可用，显示“降级回答（未使用语义检索）”；
   - 回答末尾有“来源”可展开，展示真实检索到的资料块。
3. 继续输入：`我刚才问的主题是什么？请直接复述我的问题关键词。`
4. 第二轮应引用本会话历史（上一轮问题），而不是把历史当新问题重复发送。
5. 结论：跨轮上下文来自服务端 `ai_tutor_turn` 最近成功轮次，前端不缓存消息。

截图：`screenshots/qa-1-1440.png`、`screenshots/qa-followup-1440.png`

## 3. 刷新恢复与草稿保护（约 40 秒）

1. 在输入框输入 `这是一段未发送的草稿`，**不要发送**。
2. 按 F5 刷新页面，再打开面板：
   - 之前的对话从服务端恢复；
   - 草稿仍在输入框内。
3. 结论：会话以服务端为准，localStorage 只存 `userId + courseCode -> sessionId` 指针；草稿按用户/课程存 sessionStorage。

截图：`screenshots/restore-1440.png`

## 4. 节点工作台入口（约 50 秒）

1. 打开 `http://localhost:8081/knowledge/concept/os.overview.operating_system`。
2. 右侧“向导师提问”点击后：
   - 打开统一面板，副标题显示“节点辅导”；
   - 输入框预填当前节点草稿，**不会自动调用模型**；
   - 页面内没有第二套聊天记录。
3. 可点击“解释当前节点”或直接发送。
4. 结论：节点页只提交 `TutorLaunchRequest` 上下文，不持有会话。

截图：`screenshots/node-tutor-1440.png`

## 5. 题目提示（真实帮助记录，约 1 分钟）

1. 打开 `/assessments`，在第一题下点击“我需要提示”。
2. 观察网络/界面顺序（演示时可口头说明）：
   - 先建立练习尝试并记录 `HINT` 帮助等级；
   - 再打开导师，轮次携带 `attemptContextId`；
   - 服务端把已发布题面注入模型输入（不含答案/解析）。
3. 回答应围绕当前题（属性闭包），而不是无关知识点。
4. 结论：提示不是“嘴上说记录了帮助”，而是先落库再调用模型；整组自测进行中的题会被服务端守卫阻断。

截图：`screenshots/question-hint-1440.png`

## 6. 实验导师与附件（约 1 分钟）

1. 打开 `/view/question/2107444264927186945`（LRU 编程实验）。
2. 切到“向导师提问”页签：
   - 默认不附带任何代码/报错；
   - 勾选“附带当前编辑器代码”后可编辑附件；
   - 输入问题并发送。
3. 观察面板显示“当前代码（c）”附件；回答只基于题面 + 你明确附带的内容。
4. 把附件填到 8001 字，界面显示“附带内容超出长度上限，请手动精简后再发送（不会静默截断）”，发送按钮禁用。
5. 结论：实验材料只在学生明确选择时上传，超限显式拒绝。

截图：`screenshots/experiment-tutor-answer-1440.png`、`screenshots/experiment-attachment-over-limit.png`

## 7. 最近对话、新话题与只读（约 40 秒）

1. 面板右上“最近对话”：只列出当前账号、当前课程的真实会话。
2. 点击“⋯ → 新话题”：确认后当前会话被服务端关闭，对话区清空。
3. 再次打开“最近对话”，刚关闭的会话显示“已关闭”；点开后：
   - 顶部出现“这是已关闭的历史会话，只可查看”；
   - 输入框禁用；
   - 可点“开始新话题”继续。
4. 结论：关闭/只读不是前端假状态，`PATCH` 已真正落库。

截图：`screenshots/history-1440.png`、`screenshots/history-readonly-1440.png`

## 8. 响应式收尾（约 30 秒）

依次将窗口缩小到 1024、390、320，或浏览器缩放 200%：

- ≥1180：停靠，正文让位；
- 900–1180：浮层，可关闭；
- <900：全屏面板，输入区在视口内；
- 320px：范围按钮不换行，“展开”隐藏，底部提示可见。

截图：`screenshots/layout-float-1024.png`、`screenshots/layout-full-390.png`、`screenshots/layout-full-320.png`、`screenshots/layout-zoom200-720.png`

## 9. 演示时主动说明的限制

- 当前回答可能显示 `FALLBACK`：模型真实调用成功，但语义检索/embedding 未配置；前端如实标注，不伪造 AI 全链路。
- `ai_model_profile` 为空，模型菜单只有“平台默认模型”；没有可切换的“已测试模型”。
- 全量后端测试中 8 个失败来自共享种子数据（详见 `validation.md` §1.2），A 的 54 个 AI 契约测试全绿。
