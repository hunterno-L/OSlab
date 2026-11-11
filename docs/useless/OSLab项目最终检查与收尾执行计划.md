# OSLab 项目最终检查与收尾执行计划

> 编制日期：2026-10-06。
> 执行对象：接手当前工作区的实施 agent。
> 目标：修复头像昵称显示和已确认的收尾缺口，逐项检查现有产品，形成可复现的最终验收结果。
> 本文是执行任务，不是已通过验收的声明；本轮不再安排开发排期，也不扩展新一轮产品功能。

## 1. 最终交付目标与工作方式

本轮要回答三个问题：平台现有功能是否真实可用；换账号、换页面、换视口后是否仍然一致；从新的环境启动时能否复现。

最终交付应包括：

1. 头像、昵称、角色与个人信息页使用同一套真实用户数据。
2. 知识图谱、节点工作台、题目练习、编程实验、交互模拟、学习记录、教师端的主流程通过检查。
3. 验收脚本可重复执行，失败时返回非零退出码，结果有实际接口和记录 ID 支撑。
4. 数据库初始化覆盖现有全部功能所需的表、列和素材。
5. 前后端构建和产品相关测试通过；真实判题、人工视觉、线上状态分别记录。
6. 一份最新验收报告、一份人工审核清单、一套可执行的启动和检查命令。

执行约定保持简短：

- 直接修改当前工作区，保留此前阶段和其他人的改动。
- 沿用已确认的 OpenDesign 风格和导航逻辑；修复问题，不重新设计全站。
- 使用命令行、接口和渲染测试检查；浏览器操作和截图由用户手动完成。
- 本轮不提交、推送或部署到服务器；本地测试数据与本地验收环境可以按任务创建。

发现问题后直接完成修复和相关验证，不只整理一份问题列表。P0/P1 已有能力中的缺陷纳入本轮；新的 P2 扩展单独列为后续事项。

## 2. 已核实的当前问题

以下问题来自本次源码和文档检查，执行 agent 应先复核，再按后文实施。

| 编号 | 已确认的事实 | 本轮处理 |
| --- | --- | --- |
| F01 | `GlobalHeader.vue` 的头像仅显示昵称首字，没有读取 `userAvatar`；空昵称回退为“未登录” | 改为真实头像优先、统一昵称回退 |
| F02 | `StudentProfileView.vue` 同样只显示首字；`UserCenterView.vue` 使用固定 `assets/avatar.png` | 三处复用相同头像组件和资料规则 |
| F03 | 用户 store 在获取登录信息失败时保留旧用户字段，仅改角色 | 真正失效时清空旧身份，避免残留头像、昵称和用户 ID |
| F04 | `/account` 把 `userName` 标为“账号名称”；头像菜单对管理员也写“学生信息” | 改为“昵称”“个人信息”，保留 `/account` 路径 |
| F05 | `scripts/smoke-learning.ps1` 当前是混入 `.ps1` 的 Python 历史日志恢复片段，并包含覆写自身逻辑 | 重写为规范 PowerShell 检查入口，不执行恢复片段 |
| F06 | `verify-learning.ps1` 固定本地地址和账号、只有打印结果，没有失败退出码；创建题目和学习记录但没有运行清单 | 参数化、记录生成 ID、失败返回非零退出码 |
| F07 | 该脚本仍断言“答错不升掌握度”，与当前包含学习转移项的 BKT 契约不一致 | 按公式与实际证据检查，取消单向增减断言 |
| F08 | 该脚本 LRU 判断题的题干问“命中后是否需要更新”，标准答案却是 false，解析又说必须更新 | 修正题干、答案与解析，使测试内容本身正确 |
| F09 | Docker MySQL 加载器及挂载只覆盖基础库、v3、v3 种子，未接入 v4/v5 及 v4 内容种子 | 补齐全新环境初始化与已有库增量路径 |
| F10 | 旧图谱交付记录仍描述 `labelLayout.hideOverlap` 和播种包围盒适配；当前已改成实际布局适配、固定屏幕节点尺寸及两个自定义 action | 最新验收以当前组件和渲染测试为准，更新维护文档 |
| F11 | 旧交付记录里的本地、Docker、线上和人工验收结果不在同一完成层级 | 本轮重新记录各层结果，不复制旧的“全部通过”结论 |

还有三项旧记录中的小问题需要本轮顺带核查：AI 助手首次打开的位置、`/noAuth` 的助手可见性、编程实验全局助手上下文缺少最近判题结果。若当前仍存在，按阶段 6 修复。

当前角色事实：产品使用 `user` 与 `admin` 权限；教师管理功能目前由 `admin` 承担。管理员浏览个人图谱时看到的是管理员本人的学习数据。不能把管理员无学习记录判断为学生侧故障。

## 3. 阶段 0：读取资料，建立本轮检查基线

### 3.1 必读资料

工作区根目录：`E:/web/web/源程序（包含数据库）`。

下文 `F/` 表示 `前端代码/osLab-frontend/`，`B/` 表示 `后端代码/osLab-backend/`。相对路径均从工作区根目录开始。

| 资料 | 本轮用途 |
| --- | --- |
| `知识图谱智能学习闭环方案.md` | 对照核心能力、P0/P1 和原始验收目标 |
| `OSLab第三阶段执行计划-学习流程与UI重构.md` | 对照学习流程、路由和证据契约 |
| `docs/log2-OpenDesign视觉与交互设计.md` | 沿用视觉令牌和信息层级 |
| `docs/log3-收尾执行计划.md` | 对照知识详解、实验页签、助手、回放和教师图谱 |
| `docs/log3-收尾交付记录-2026-10-05.md` | 查旧验证结果、限制及 BKT 裁决 L3-R12 |
| `F/src/router/routes.ts`、`F/src/navigation/appNavigation.ts` | 路由、权限、默认首页和导航的真实来源 |
| `F/src/api/learning.ts`、`F/generated/services/UserControllerService.ts` | 当前前端 API 的名称和响应结构 |
| `B/src/main/java/com/yupi/springbootinit/controller/` 下相关 controller | 对照请求参数、权限和后端返回值 |
| `deploy/README.md`、`deploy/docker-compose.yml`、数据库升级 SQL | 检查全新环境和现有库启动 |

历史文档只作为线索。与后续用户决定或最新源码不符的旧设计，不直接重新搬回代码。例如不能为恢复旧图谱描述重新加入 `labelLayout.hideOverlap`。

### 3.2 当前可复用的接口与方法

执行前读取表内真实定义；新增实现优先使用这些接口。

| 事项 | 现有接口 / 方法 | 源码依据 |
| --- | --- | --- |
| 当前用户 | `GET /api/user/get/login`；`getLoginUserUsingGet()` | `UserController.java:158`；`UserControllerService.ts:92` |
| 更新本人资料 | `POST /api/user/update/my`；`updateMyUserUsingPost({ userName, userAvatar, userProfile })` | `UserController.java:321`；`UserUpdateMyRequest.java`；`UserControllerService.ts:276` |
| 身份字段 | `LoginUserVO.id/userName/userAvatar/userProfile/userRole` | 后端与 generated 的 `LoginUserVO` |
| Vuex 身份 | `user/getLoginUser`；`user/updateUser` | `F/src/store/user.ts` |
| 目录与题目 | `POST /api/learning/questions/catalog`、`/list/page`；`GET /get?questionId=` | `LearningQuestionController.java:83` 起；`learning.ts:428` 起 |
| 作答 | `POST /api/learning/questions/answer`；`questionId/answer/assessmentRunId/submissionKey` | `LearningAnswerRequest.java`；`learning.ts:447` |
| 测评会话 | `POST /api/learning/assessment/start`、`/finish`；`GET /run?runId=` | `LearningAssessmentRunController.java`；`learning.ts:522` 起 |
| 个人图谱与工作台 | `GET /api/learning/mastery/graph`、`/node?nodeName=` | `LearningMasteryController.java:59/68` |
| 补救路径 | `GET /api/learning/mastery/path?targetNodeName=` | `LearningMasteryController.java:49` |
| 时间线与快照 | `GET /api/learning/mastery/timeline`；`/snapshot?atEventId=` | `LearningMasteryController.java:88/104` |
| 图谱导师 | `POST /api/learning/tutor/ask`；`{ nodeName, question }` | `TutorAskRequest.java`；`learning.ts:717` |
| 全局 AI 助手 | 复用现有 `/api/chat/ask` 单文本契约 | `useAiConversation.ts`、generated Chat 服务 |
| 教师端 | 前缀 `/api/learning/teacher`；五个 GET 端点：`/classes`、`/dashboard`、`/timeline`、`/node/detail`、`/chapters/coverage` | `LearningTeacherController.java`；`learning.ts:726` 起；按定义传 `className` 等参数 |
| 模拟器 | `POST /api/learning/simulation/page`、`/cpu` | `LearningSimulationController.java`；`learning.ts:848/864` |
| 提交与终态 | `POST /api/question/question_submit/do`；`GET /api/question/question_submit/get?id=` | `QuestionController.java`、`ViewQuestionView.vue`、验收脚本 |
| 进程存活 | `GET /api/health/liveness` | `HealthController.java:26`，只证明后端能响应，不证明所有依赖健康 |

generated 用户服务直接返回 `BaseResponse`；`learning.ts` 返回 AxiosResponse，应读取 `response.data.code/data`。不要混用两套取值方式。

### 3.3 基线记录

1. 检查根目录及前后端各自的 Git 状态，记录本轮开始时的改动范围。
2. 查看当前服务、端口和启动脚本；需要运行时优先复用正常的本地服务，记录本轮自己启动的进程。
3. 建立 `docs/最终验收报告-2026-10-06.md`，先写基线、检查矩阵和待验证项。
4. 建立 `artifacts/final-check/` 保存测试摘要、接口检查 JSON 和人工截图索引。
5. 建立原方案能力对照表：能力 → 页面 → 接口 → 测试 / 人工步骤 → 当前状态。

本阶段验收：列出实际可用接口和执行入口；把 F01–F11 标入任务表。没有跑过的检查标为“待执行”。

## 4. 阶段 1：修复头像、昵称与身份显示

这是明确的实施任务，不是仅检查现状。

### 4.1 文件与实现结构

重点文件：

- `F/src/components/GlobalHeader.vue`：顶栏头像、昵称、角色、下拉菜单。
- `F/src/views/user/StudentProfileView.vue`：`/account` 的本人资料。
- `F/src/views/user/UserCenterView.vue`：教学用户管理页上方的本人摘要。
- `F/src/store/user.ts`：用户数据获取与清空。
- `F/src/views/user/UserLoginView.vue`、`F/src/plugins/axios.ts`：登录成功及会话失效链路。
- `B/src/main/java/com/yupi/springbootinit/controller/UserController.java`、同包根目录下的 `model/vo/LoginUserVO.java` 和 `model/dto/user/UserUpdateMyRequest.java`。

建议新增一个小型 `F/src/components/UserAvatar.vue`，统一头像渲染；再新增 `F/src/utils/userPresentation.ts` 或等价纯函数模块统一昵称处理。组件名可调整，但不要让三个页面继续分别实现。

### 4.2 统一显示规则

| 数据情况 | 显示要求 |
| --- | --- |
| 已登录且 `userAvatar` 有有效图片地址 | 显示真实图片，圆形裁切、`object-fit: cover` |
| 没有头像，或图片加载失败 | 显示昵称首个 Unicode 字符；没有昵称时显示“用” |
| `userName` 有值 | 去掉前后空白后显示，保留真实昵称 |
| 已登录但昵称为空、null 或纯空白 | 显示“用户”，不显示“未登录” |
| 真正未登录 | 显示登录入口，不残留上一账号头像与昵称 |
| 昵称过长 | 单行省略，hover / 可访问名称保留完整昵称 |
| 管理员账号 | 显示实际管理员昵称与“管理员”，不能替换成示例学生或教师姓名 |
| 学生账号 | 显示实际昵称与“学生”；教学名册中的姓名仍使用名册字段 |

尺寸沿用现有布局：顶栏 36px，个人资料摘要 52px；用户管理摘要按所在布局设置尺寸。颜色、边框和字重使用现有主题变量，不新增头像光晕或装饰徽章。

图片失败状态与 `userId + userAvatar` 绑定；更换地址或切换账号时重置，避免 A 的坏图片让 B 的正常头像也进入回退状态。

`LoginUserVO` 当前不提供 `userAccount`，不要把昵称标签继续写成登录账号，也不要为了兜底昵称虚构 DTO 字段。

### 4.3 资料读取、更新与同步

1. 所有“当前用户”摘要以 `store.state.user.loginUser` 为主要数据源。用户管理表里的学生资料仍按表格行数据展示。
2. 顶栏、个人资料和用户管理摘要去掉固定头像、各自默认昵称与重复的独立身份请求。
3. `/account` 和头像菜单统一使用“个人信息”；字段改为“昵称”，保留角色、用户编号、注册时间和简介。
4. 增加一个克制的“编辑资料”入口：只编辑昵称和头像地址，使用现有 `updateMyUserUsingPost`。本轮先支持图片地址，不新增对象存储或上传服务。
5. 保存成功后 `await store.dispatch('user/getLoginUser')`，让顶栏和个人资料同步更新；保存失败保留输入并给出错误反馈。
6. 清除头像时确认后端能保存空字符串；不能只在前端隐藏图片，刷新后又恢复旧地址。
7. 昵称长度和头像地址长度遵循实际数据库字段与请求校验；若后端缺少必要校验，在现有 DTO / controller / service 上补齐。
8. 后端 `getLoginUser()` 已按 session 中的 ID 重新读取数据库，`getLoginUserVO()` 已复制头像昵称。先验证更新→重新获取；不要额外引入缓存或新个人资料接口。

### 4.4 登录态失效与账号切换

1. `getLoginUser` 收到明确未登录结果时，完整重置 `id/userName/userAvatar/userProfile/userRole`，不再展开旧用户对象。
2. 退出登录成功或服务端明确会话已失效时，复用同一重置操作，并清理用于用户隔离的当前 ID。
3. 临时网络失败显示加载 / 重试反馈；不把“未获取到昵称”当成“未登录”。
4. 登录、资料刷新和账号切换的异步响应增加简单的请求序号或身份检查，旧请求不能覆盖新登录账号。
5. 头像错误回退、本地草稿和 AI 会话的隔离继续使用真实用户 ID，不以昵称作为缓存键。

### 4.5 验收案例

| 编号 | 操作 | 预期 |
| --- | --- | --- |
| U01 | 学生 A 配正常头像、中文昵称后登录 | 顶栏和个人信息显示相同图片、昵称 |
| U02 | 昵称只有空白、头像为空 | 仍识别为登录用户；显示“用户”和字母回退 |
| U03 | 头像地址返回 404 | 回退正常，页面无破图；换正常地址后恢复 |
| U04 | 使用长英文昵称或首字符为 emoji | 不挤压导航、不显示半个字符 |
| U05 | 编辑昵称 / 头像后保存，切页，再刷新 | 三处摘要一致；刷新后仍是保存值 |
| U06 | 保存请求失败 | 不显示成功，不丢表单，顶栏不提前变更 |
| U07 | A 退出→B 登录→管理员登录 | 无头像、昵称、角色或迟到请求串号 |
| U08 | 会话失效后重新加载资料 | 旧身份被清空，登录入口可用 |
| U09 | 管理员访问 `/account` 和用户管理 | 标题与菜单语义适用管理员，管理表学生姓名不被昵称替换 |

给统一昵称函数、store 清空及迟到响应处理增加小型回归测试，复用 `tests/node-workbench-cache.js` 的 Node / TypeScript 裸测模式即可。图片实际裁切与失败显示列入人工清单，不为此引入整套新测试框架。

阶段交付：上述显示和同步修复、`tests/user-identity.js` 或等价测试、U01–U09 的执行记录。

## 5. 阶段 2：修复最终验收脚本

参考：`scripts/dev/verify-learning.ps1`、`LearningQuestionGrader.java`、`BktModel.java`、`LearningEventObservationTest.java` 和当前 API DTO。

### 5.1 统一入口与运行结果

1. 以 `verify-learning.ps1` 为主实现；把损坏的 `scripts/smoke-learning.ps1` 改为调用它的薄入口，或让其明确指向规范入口。删除 Python 日志恢复逻辑。
2. 参数至少支持 `BaseUrl`、教师账号、学生 A/B 账号、`ClassName`、`JudgeMode`、`RunKey`、`ReportPath`；口令从运行参数或本地环境读取。
3. `JudgeMode` 明确区分 `Stub`、`Real`、`None`。无真实沙箱时执行其余全部检查；真实判题项标为未执行，不能生成 AC/WA 的假通过。
4. 结果包括用例编号、是否通过、预期 / 实际、生成的题目 / 会话 / 提交 / 事件 ID、时间和环境类型。
5. 失败计数大于 0 时 `exit 1`，请求或解析异常同样失败；只有所有已执行断言通过才能打印 `ALL PASSED`。
6. 每次运行使用唯一 `RunKey` 标记新增数据，并写运行清单。不要再用固定题目数量、固定历史事件总数或固定掌握状态判断结果。
7. 每个请求检查业务 `code`；HTTP 200 本身不代表操作成功。后续失败不能被一次成功登录掩盖。
8. PowerShell 文件按本项目 Windows 兼容约定保存；先做 Parser 静态解析，清理开头重复 BOM 等编码问题。

### 5.2 修正测试内容和 BKT 断言

LRU 判断测试改成语义一致的内容，例如：

```text
题干：LRU 命中一个页面后，无需更新该页的最近访问顺序。
标准答案：false
用于制造错误的学生答案：true
解析：每次访问都更新最近访问顺序，包括命中。
```

当前 BKT 默认参数是 `pInit=0.10, pLearn=0.10, pGuess=0.20, pSlip=0.10`。答错后的更新仍包含学习转移：

```text
posterior = p * pSlip / (p * pSlip + (1-p) * (1-pGuess))
updated   = posterior + (1-posterior) * pLearn
after     = before + mappingWeight * (updated-before)
```

对新节点、权重为 1 的首次错误，综合值约为 `0.1123288`，仍为薄弱状态。验收应对照 `BktModel` 和 `LearningEventServiceImpl` 的计算、分项及证据数，不再要求“答错必降”“一次答对必绿”。

增加这些实质断言：

- 第一次独立作答确实写入 ATTEMPT 和对应节点变化。
- 同一 `submissionKey` 重发不新增第二份有效观测；主动再次作答使用新 key，按现有重复证据规则处理。
- CE 和 SYSTEM_ERROR 可记录诊断，但掌握度、分项证据计数及快照保持不变。
- 真实 AC / WA 的有效观测和归因可追溯到本次提交 ID。
- 读取题面检查答案隔离；查看解析前后的行为遵循现有契约。

阶段验收：Parser 无错误；主动制造一个失败用例时进程退出非零；同一环境重复运行仍能按本次数据判断，结果不会依赖旧账号已积累了多少次作答。

## 6. 阶段 3：补齐数据库与新环境初始化

参考：`deploy/mysql-init/10-load-oslab-db.sh`、`deploy/docker-compose.yml`、`数据库文件/` 下基础库及升级 / 种子 SQL。

### 6.1 必须补齐的初始化顺序

```text
基础 oslab_db.sql
  → v3 学习表
  → v4 测评会话、作答幂等与事件快照字段
  → v5 教学素材表
  → v3 题目种子
  → v4 内容种子
```

1. 在 Compose 加入缺失 SQL 的只读挂载，并在加载器按上述顺序执行。
2. 基础库已有 `uk_userAccount`，不要在新库重复执行 v2 的同名索引增加语句。
3. 验证 `learning_assessment_run`、`learning_node_content` 存在。
4. 验证 `learning_attempt.assessmentRunId/submissionKey` 和 `uk_user_submission` 存在。
5. 验证 `learning_event.snapshotBefore/snapshotAfter/eventGroupKey` 及相关索引存在。
6. 内容种子的 `updateUserId` 从管理员取得；加载前确认基础库已有有效管理员，避免 NOT NULL 失败。
7. 核对种子中的节点名与 Neo4j 当前图谱匹配；不能生成只有标题、跳转却找不到节点的演示素材。

### 6.2 已有环境与演示账号

1. 提供明确的已有库增量命令 / 脚本，检查已有列和索引后补缺失项。v4 当前的 ALTER TABLE 不能简单重复执行并忽略所有错误。
2. 本轮不强制引入数据库迁移框架；一个版本明确、可重复检查的增量脚本即可。
3. 检查当前演示账号真实存在：`teacher_demo`、`student_demo1`、`student_demo2`，并核对两学生的 `students.userId/className`。
4. 基础 SQL 和当前种子不自动提供这三个账号。新测试环境应提供独立、可复现的演示账号准备步骤，使用现有后端密码编码方式，不依赖某个 `tmp/` 文件。
5. 演示账号资料与口令通过测试环境配置；已有账号保留既有资料。最终报告记录用户 ID、角色与测试班级，口令不进入日志。
6. 从新的测试数据库或独立 Compose 项目证明初始化，不通过删除当前数据卷制造“新环境”。

阶段验收：新环境能登录、加载图谱、启动测评、提交作答、读取快照和节点素材；已有环境升级后仍能读取历史事件。给出表 / 列检查与接口结果。

## 7. 阶段 4：构建、测试与源码一致性检查

本轮先做阶段 1–3 的修复，再执行统一检查。修复了哪个模块，就增加或调整能复现该缺陷的测试；不要求给每处文案写测试。

### 7.1 前端

在 `F/` 下执行：

```powershell
npx vue-cli-service lint --no-fix
node tests/node-workbench-cache.js
node tests/knowledge-graph-render.js
node tests/user-identity.js
npm run build
git diff --check
```

`user-identity.js` 为阶段 1 要交付的测试名；若命名不同，报告和统一脚本同步真实名称。

验收要求：0 个 lint error；两项现有测试和本轮身份测试通过；生产构建成功。记录 warnings 的数量与本轮新增项，修复本轮新引入的无用变量、错误类型等问题，不用机械全库格式化制造无关改动。

### 7.2 图谱修复专项保护

当前 `knowledge-graph-render.js` 已覆盖 7 项场景：少量路径重置、150→23→5→1 的过滤适配、缩放和关系标签、视觉更新保留布局、零尺寸初始化、窄视口、异步力导向中的提前重置。

检查 `KnowledgeGraph.vue` 中实际布局适配、`nodeScaleRatio: 0`、`oslabGraphFit`、`oslabGraphStyle` 保持有效。教师图谱和时间线回放也复用该组件，不能只验证学生图谱。

ECharts 当前安装版本为 5.6；自定义 action 使用其视图 / 控制器行为。不要在本轮无关地升级 ECharts。若必须动这部分，补原生 `graphRoam` 缩放、老师状态填色及空图转非空图的渲染验证。

### 7.3 后端

在 `B/` 下用项目 Maven wrapper 运行，使用当前环境实际可用的 JDK：

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
git diff --check
```

重点套件按已有类核查：

| 模块 | 已有测试入口 |
| --- | --- |
| 密码与用户 | `UserServiceImplTest`、`UserServiceTest` |
| 四题型判分 / 进度 | `LearningQuestionGraderTest`、`LearningQuestionProgressTest`、`LearningQuestionAssessmentTest` |
| 目录 / 章节 / 候选池 | `LearningQuestionCatalogTest`、`LearningChapterPredicateTest`、`LearningAssessmentCandidatePoolTest` |
| 测评会话 | `LearningAssessmentRunControllerTest`、`LearningQuestionBatchTest` |
| BKT / 事件 / 快照 | `BktModelTest`、`LearningEventObservationTest`、`LearningEventServiceTest`、`MasterySnapshotCodecTest` |
| 图谱 / 工作台 / 路径 | `KnowledgeGraphChapterTest`、`LearningMasteryServiceTest`、`LearningWorkbenchQueryTest`、`LearningPathServiceTest` |
| 归因 / 失败状态 | `LearningAttributionServiceTest`、`FailureStateClassifierTest` |
| 回放 / 教师 / Tutor | `LearningRecordsServiceTest`、`LearningTeacherServiceTest`、`LearningTutorServiceTest` |
| 模拟器 | `PageReplacerTest`、`CpuSchedulerTest` |

从 `target/surefire-reports/TEST-*.xml` 汇总实际 tests/failures/errors/skipped，不照抄旧报告的 175/0/6。

旧 Post / COS 模板测试要在本轮分类收敛：仍属于产品的测试修好；与现有教学平台无关、需要外部云凭据的模板，明确移入单独的 legacy / integration profile 或带理由标记。报告保留原始失败和分类依据，不能通过全局 `testFailureIgnore` 或跳过整个产品套件获得绿色结果。

手工初始化 MyBatis TableInfo 的测试继续与生产 `mapUnderscoreToCamelCase=false` 保持一致，防止再出现单独通过、全量失败的缓存污染。

### 7.4 文案、接口与资源

1. 执行 `scripts/dev/check-ui-terminology.ps1`。用户界面统一使用“题目练习”“知识测评”“编程实验”等现有术语。
2. 源码显示映射与历史数据字段分开检查；为不可变历史报告添加精确的文件级规则，不能整体排除所有 docs 或源码。
3. 新计划、最终报告、演示脚本与当前用户可见字符串须检查通过。
4. 检查 generated 请求、`learning.ts`、controller 的路径与字段一致，尤其是 `questionId/runId/nodeName/targetNodeName/atEventId`。
5. 检查资源目录中的本地 HTML 实际存在，并随 `dist` 打包；HTML 响应不能是 SPA 的 `index.html`。

阶段交付：静态 / 构建 / 测试结果，实际测试数量、遗留测试处理说明及本轮修改文件清单。

## 8. 阶段 5：用真实接口走通完整学习闭环

使用测试班级的管理员、学生 A、学生 B 分别登录并保留独立会话。先快照记录学生 A/B 的状态；本轮数据使用 RunKey 与生成 ID 标识。

### 8.1 核心用例矩阵

| 编号 | 执行步骤 | 必须验证的结果 |
| --- | --- | --- |
| L01 | 管理员创建 JUDGE/SINGLE/MULTIPLE/FILL_BLANK 各一题，走提交、审核、发布 | 四种题型能在对应节点 / 章节出现；未发布题不进入学生候选 |
| L02 | 学生按节点与章节取同一题 | ID、题面、进度和计数一致；作答前不暴露标准答案 |
| L03 | 四题型各提交正确 / 错误答案；多选错集、填空等价空白单独覆盖 | 服务端判分正确；解析、误区和关联节点返回一致 |
| L04 | LRU 判断题首次答错→重发同 key→再以新 key 纠错 | 事件、isFirst、有效观测和重复计数符合契约，不重复抬高可信度 |
| L05 | 调目录、个人图谱、工作台和时间线 | 证据、掌握值、状态、来源 ID 可互相追溯，没有一页刷新另一页停留在旧值 |
| L06 | 生成到 LRU 的补救路径，依次执行实际行动 | 路径遵循前置关系；每步有有效页面 / 资源和推荐原因 |
| L07 | 启动 5 题测评→答题→重读会话→完成 | 队列固定；已答、题号状态、完成汇总正确；归属校验有效 |
| L08 | 章节、类型、难度、学习状态与“未归类”筛选 | catalog、列表、测评候选口径一致；超过 50 道候选时早期题仍可被选中 |
| L09 | 页面置换固定样例运行 FIFO/LRU/OPT | 3 个物理块、序列 `7,0,1,2,0,3,0,4,2,3,0,3,2` 缺页分别为 10/9/7 |
| L10 | 页面置换和 CPU 模拟预测 | 正误判定正确；有 eventId 才显示计入学习证据；OPT 对比预测不写学习事件 |
| L11 | 请求图谱导师，检查来源与下一步动作 | 当前节点、本人失败记录、推荐动作可追溯；actionType/targetId 可跳转 |
| L12 | 使用没有 AI key 的测试配置请求导师 | 标明 FALLBACK，题目判分、路径和图谱更新仍可用 |
| L13 | 提交错误代码→终态→补救→正确代码→终态 | 按实际判题 message 判断 WA/AC；事件能找到本次提交，工作台失误状态及时更新 |
| L14 | 提交 CE、触发沙箱系统错误，再读取快照 | 保留诊断，掌握值与概念 / 实践证据计数不变 |
| L15 | 时间线按事件组查看多个时点快照 | before/after 数值与事件一致；旧无快照数据说明清晰；回放无写操作 |
| L16 | 管理员读取班级列表、看板、节点下钻、班级时间线和章节覆盖 | 各结果属于同一班级；人数、分母和薄弱比例自洽；列表与热力图口径一致 |
| L17 | 学生 B 读取本人图谱，尝试查看 A 的会话 / 提交 / 快照及教师接口 | 本人结果独立；越权请求拒绝；A 的作答不改变 B 的状态 |
| L18 | 新学生无学习记录、空班级、资源缺失或节点无关联题 | 显示正确空态，核心导航可继续；不填示例分数和假通过率 |

实际判题中 `status=2` 表示判题过程结束，并不自动表示 Accepted。必须同时检查 `judgeInfo.message`。`status=3` 及请求失败按真实含义记录。

### 8.2 两条内容链

首条完整链使用现有实际节点名：

```text
内存管理 / 虚拟内存管理 → 页表 → 缺页中断 → 页面置换 → LRU 页面置换算法
```

第二条至少抽查：

```text
进程 → 进程状态 → CPU 调度 → 时间片轮转算法 / 进程切换
```

每条链检查简介、前置关系、题目映射、实验 / 模拟入口和资料。可选资源缺失时展示真实空态；主演示节点必须能完成学习和反馈，不用海量静态讲义替代现有通用工作台。

### 8.3 一个完整的主演示事务

保存这条链路的 ID 清单和前后快照：学生 A 登录 → LRU 题目答错 → 图谱证据与补救路径 → 工作台学习 / 预测 → 编程实验 WA → 带来源导师指导 → 修正 AC → 学习记录回放 → 管理员查看班级变化 → 学生 B 对照。

结果是否变绿按实际证据与阈值判断，不要求人为设置颜色。一个已纠正的历史错误不应永远表现为当前未解决失误。

阶段验收：L01–L18 给出通过 / 失败 / 未执行和证据；一次有效提交能走到可解释状态变化。不能只用“接口 code=0”作为闭环证明。

## 9. 阶段 6：全站行为、路由和已知小问题收尾

参考：`appNavigation.ts`、`routes.ts`、`LearningLayout.vue`、`TeachingLayout.vue` 和对应页面源码。

### 9.1 路由和身份

| 入口 | 检查 |
| --- | --- |
| `/user/login`、`/user/register` | 真实登录 / 注册结果、表单反馈、站内回跳、正确角色默认首页 |
| `/home`、`/graph`、`/knowledge/node/:nodeName` | 标题 / 所属导航正确；直接访问、刷新和后退能恢复上下文 |
| `/assessments`、`/assessments/run/:runId` | 筛选 query、会话续做、结束汇总，题目练习不重复渲染课程侧栏 |
| `/experiments`、`/experiments/code/:id`、`/experiments/simulation` | 章节过滤、实验 ID、模拟上下文正确 |
| `/resources`、`/records`、`/account` | 入口可达；账户 / 学习记录来自头像菜单；无错误导航高亮 |
| `/teaching/*` | 管理员可用，学生直达拒绝；学习侧预览与班级聚合区分 |
| 旧 `/knowledgeGraph`、`/question`、`/view/question/:id` 等 | 跳到规范路由；旧链接需要保留的 ID/query/hash 不丢失 |

逐页列出可见按钮、点击行为、接口 / 导航目标和状态反馈。功能已实现却没绑定的按钮直接修好；同一动作的重复入口按既有设计合并，不新增一排按钮补漏洞。

### 9.2 页面重点

- 图谱：类别底色和掌握状态区分；筛选、选中、学习路径、关系标签和重置都有效；详情“进入学习”为唯一主入口。
- 工作台：知识内容 / 知识详解 / 题目练习 / 实践四页签正常；`tab=resources` 真实打开详解；首次进页不提前加载隐藏 iframe；两份 HTML 和失效反馈可用。
- 编程实验：实验说明 / 关联图谱 / 向导师提问三页签；编辑器草稿按用户、实验、语言隔离；提交中状态可辨，轮询到终态，错误码和输出没有被成功提示覆盖。
- 实验导师：根据有效关联节点调用图谱 Tutor；带题目 / 代码 / 判题上下文；失败后预填由用户点发送；切题不会带入上题回复。
- 模拟器：改配置后旧运行明确失效，重新运行前不能提交旧预测；播放 / 暂停 / 单步 / 重置动作符合当前结果。
- 学习记录：分页、筛选、待巩固分类、事件组回放、播放暂停及关闭清理正常。
- 教学管理：题目创建 / 修改 / 审核 / 下线 / 导入预览提交、素材维护、班级下钻、用户分页 / 添加 / 重置操作使用真实服务。

### 9.3 全局 AI 助手顺带修复

核查并处理仍存在的旧小问题：

1. 未手动放置面板时，持久化只保存按钮位置或明确标记 panelPlaced；首次打开落在按钮附近，不能把未初始化的 `(0,0)` 当作有效面板坐标。
2. `/noAuth` 与登录 / 注册页面一起纳入不显示助手的页面。
3. 编程实验注册的全局上下文增加真实的最近判题结果，取当前 `submitDetail/judgeInfo` 等现有数据，不引入不存在的字段。
4. 切换用户、页面、实验时，请求和会话不会串号；超时 / 重试 / 清空 / Escape 可用。
5. 当前按钮源码为 76px，旧交付记录写 56px。以用户实际确认的效果和当前组件为基线，只修交互错误，不依据旧报告擅自再次缩小。

阶段验收：完成全站按钮 / 路由表；已修问题有对应验证；留存的空态是产品允许状态而非死按钮。

## 10. 阶段 7：真实判题与发布环境检查

### 10.1 检测实际运行能力

先检查：

```powershell
docker version
docker compose version
```

有可用 Docker Engine 时，用独立本地验收 Compose 项目启动当前代码。引用 `deploy/README.md`、两个 Dockerfile 和 `代码沙箱/server.py/runner.py` 的真实配置。

没有 Docker 时，完成配置 / 初始化脚本 / 打包检查和 Stub 协议检查，真实判题明确标“未执行”。继续完成其他阶段，不把环境缺失当作整个收尾停止的理由。

### 10.2 真实沙箱样例

使用 `scripts/dev/samples/` 的正确 LRU、错误 LRU、编译错误、运行错误、超时程序，并核对输入格式与本轮实验用例一致。当前 Stub 回显用例和真实 LRU 用例不能直接混用。

| 用例 | 预期结果 | 额外检查 |
| --- | --- | --- |
| 正确 LRU | Accepted | 输出真实来自程序；产生有效 PASS 证据 |
| 错误 LRU | Wrong Answer | 失败样例 / 归因可追溯；产生有效失败观测 |
| 编译错误 | Compile Error | 诊断保存；不改变掌握状态 |
| 运行错误 | Runtime Error | 判题终态和错误类型正确；知识影响按既有归因规则 |
| 超时 | Time Limit Exceeded | 请求能结束，超时信息正确，无永久“运行中” |
| 沙箱不可达 | 系统 / 服务错误 | 不伪造 WA 或 AC；不写有效掌握观测 |

Java / C / C++ / Go 各跑一个真实 AC 与一项失败样例，检查四语言的编译命令和返回协议。大量性能压测不作为本轮默认任务。

### 10.3 发布包与运行配置

1. 构建前端 `dist` 和后端 jar，确认静态资源、头像站内路径和深链接刷新在 Nginx 下工作。
2. 保持同源 `/api` 代理；检查新环境里不存在前端直连某个开发机地址的问题。
3. 用各自服务健康和真实业务接口验证 MySQL / Redis / Neo4j / 沙箱，不能只看 liveness。
4. 核对 prod 配置、环境变量名、启动顺序和日志位置；配置检查输出不含口令和 AI key。
5. 测试本地容器重启后的资料、测评会话、学习证据和素材仍可读取。
6. 发布资料列明源码 / 包版本、数据库版本、启动和检查命令；临时日志、缓存和 `.env` 不混入发布清单。
7. 现网 `http://39.106.136.15/` 只允许只读检查页面响应 / 存活接口，记录其版本是否与本轮一致；不在现网跑写数据的验收脚本，不发布本轮包。

阶段验收：真实判题结果独立于 Stub 结果；有新环境启动证据或明确的待验证项；可交付包与部署文档一致。

## 11. 阶段 8：人工 UI 审核清单

agent 完成自动检查和代码修复后，把服务留在明确可访问的地址，交付下面的手动步骤。无需在中途等待用户替 agent 完成接口 / 构建检查。

agent 不使用浏览器自动化；人工结果只有收到真实审核结论后才写“通过”。

### 11.1 截图与交互矩阵

| 页面 | 角色 | 视口 / 场景 | 核查重点 |
| --- | --- | --- | --- |
| 顶栏与个人信息 | 学生 A/B、管理员 | 1440×900、390×844 | 真实头像、长昵称、失败图片、角色、切账号和保存同步 |
| 图谱 | 学生、管理员预览 | 1920×1080、1440×900、1024×768、390×844 | 首屏可读；全图→章节→5 节点路径→重置不空白 |
| 图谱标签 | 学生 | 缩小 / 放大、标签开关、鼠标悬停 | 文字与节点不分离，关系字号正常，选中不打乱布局 |
| 节点工作台 | 学生 | 1440×900、390×844 | 四页签、就地练习、知识详解加载、导师与资料入口 |
| 题目练习与测评过程 | 学生 | 1440×900、390×844 | 四题型、长题干、题号状态、刷新续做、结束汇总 |
| 编程实验 | 学生 | 1440×900、1024×768、390×844 | 编辑器、三页签、提交等待 / 失败 / 通过、草稿保存 |
| 交互模拟 | 学生 | 1440×900、390×844 | 配置、步骤、播放控制、改输入失效、预测反馈 |
| AI 助手 | 学生、管理员 | 四角拖动、窄屏、退出再登录 | 面板不越界，不遮住主要操作，首次位置正常 |
| 学习记录回放 | 学生 | 1440×900、390×844 | 时间轴、滑块、播放暂停、数值与图谱一致 |
| 教师热力图及管理页 | 管理员 | 1440×900、1024×768 | 聚合颜色、下钻、切班级、长表格、管理按钮 |

人工记录每项的角色、路由、数据前置、预期 / 实际和截图路径。无需截图每个空态，但用户已经反馈过的图谱空白、标签异常、缩小错位和头像昵称必须检查。

## 12. 阶段 9：文档同步与最终结论

### 12.1 必须交付的文档

1. `docs/最终验收报告-2026-10-06.md`：原方案能力矩阵、本轮修复、各项验证、实际数量与限制。
2. `docs/最终人工审核清单.md`：整理阶段 8 的操作步骤、账号角色和留空的审核结果。
3. `artifacts/final-check/`：测试摘要、接口结果、生成 ID 清单、新环境表列结果、真实判题记录和截图索引。
4. 更新根 `README.md`、`deploy/README.md` 和 `演示脚本.md`：指向最新入口，给出有效命令，纠正旧 BKT 断言和“脚本同时复位数据”等不实说明。

旧交付记录保留为历史，不覆盖当时结果。最新报告说明旧图谱适配 / 标签机制已被本轮前的实际修复替代，不继续引用已失效的实现描述。

### 12.2 报告必填表

| 检查层 | 状态 | 证据 / 限制 |
| --- | --- | --- |
| 源码与接口一致性 | 通过 / 失败 | 文件与接口 |
| 前端 lint / 裸测 / 构建 | 通过 / 失败 | 数量、命令和退出码 |
| 后端产品测试 / 打包 | 通过 / 失败 | tests/failures/errors/skipped，模板分类 |
| 头像昵称修复 | 已实现 / 待验证 / 通过 | U01–U09 |
| 完整学习闭环 | 通过 / 失败 | L01–L18、本次证据 ID |
| Stub 判题协议 | 通过 / 失败 / 未执行 | 仅协议与事件链 |
| 真实沙箱判题 | 通过 / 失败 / 未执行 | AC/WA/CE/RE/TLE 和语言样例 |
| 新环境初始化 | 通过 / 失败 / 未执行 | 数据库版本、业务接口与启动 |
| 人工 UI 审核 | 通过 / 待用户审核 | 截图和实际反馈 |
| 线上状态 | 只读已检查 / 未检查 | 当前版本；未部署本轮改动 |

### 12.3 收尾判定

- **可本地交付**：头像昵称及本轮已知缺陷完成修复；构建和产品测试通过；主学习闭环有可复现证据；没有未处理的功能阻断问题。
- **可进入部署验收**：在以上基础上，新环境初始化和真实沙箱通过，部署包与文档完整。此结论不是已经授权或完成服务器部署。
- **整体已验收**：再加用户人工视觉审核通过，涉及线上时还有实际对应版本的线上结果。

没有对应证据的层级保留为待验证。自动工作全部做完后，只剩用户人工审核可以明确交接；不能因“计划执行完了”把待审核项目全部勾选。

代码行到知识点连线、自动生成图谱关系、风险预测、更多模拟主题等新 P2 功能放在后续清单，不阻塞本轮最终检查。

## 13. 可直接交给 agent 的任务拆分

单个 agent 可以按阶段 0→1→2→3→4→5→6→7→8→9 顺序完成。若使用多个 agent，可分为以下任务，主 agent 负责合并结论和统一验证；共享 store、主题、路由与生成客户端由同一个负责人协调。

| 任务 | 范围 | 必交结果 |
| --- | --- | --- |
| A：身份与资料 | 阶段 1 | 三处头像昵称统一、资料编辑同步、会话清空、身份回归测试 |
| B：验收脚本与闭环 | 阶段 2、5 | 规范检查脚本、非零失败退出、BKT / LRU 测试内容修正、L01–L18 结果 |
| C：数据库与真实运行 | 阶段 3、7 | v4/v5 初始化、演示账号准备、已有库增量、新环境 / 沙箱证据 |
| D：全站与测试收尾 | 阶段 4、6 | 图谱保护、全站按钮和路由表、小问题修复、测试模板分类 |
| 主 agent：最终集成 | 阶段 0、8、9 | 原方案能力矩阵、统一重验、人工审核清单、最终报告与最新 README |

每个任务的报告写明：实际修改文件、复现的问题、修改后的行为、执行命令与结果、剩余未验证项。修复完成后只重跑受影响检查和一次最终统一检查，不反复跑全库构建占用时间。

## 14. 给实施 agent 的完整开工提示词

```text
请执行 docs/OSLab项目最终检查与收尾执行计划.md。

直接在 E:/web/web/源程序（包含数据库）当前工作区工作，保留既有改动。
这是最终检查与收尾实施任务：检查发现的产品缺陷要直接修复。

先读阶段 0 引用的资料和实际接口。优先完成头像昵称的统一显示、
个人资料保存同步、旧登录态清空；然后修正验收脚本的 BKT 断言、
LRU 题干答案和失败退出码，补齐 v4/v5 数据库初始化。

继续验证全部现有学习流程、管理流程及图谱渲染回归。
图谱的最新实际布局适配和标签机制已经修复，不能按旧记录退回。
沿用当前 OpenDesign 风格，沿用“题目练习”“知识测评”等现行文案。

使用命令行、实际接口、现有测试与 ECharts 渲染检查。
不用浏览器自动化；人工视觉审核交给用户，但先完成所有独立的自动工作。
不提交、不推送、不部署到服务器。

有 Docker 时验证真实沙箱及新环境；没有时如实记录未执行，继续其余检查。
最终提供最新验收报告、人工审核清单、测试数据 ID 清单与有效启动命令。
不要只回报“实现完毕”或复用旧报告的 ALL PASSED，要给本轮实际证据。
```
