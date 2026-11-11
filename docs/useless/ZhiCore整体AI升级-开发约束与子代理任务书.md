# ZhiCore 整体 AI 升级：开发约束与子代理任务书

版本：2026-11-03。执行约束文档。范围与设计的权威来源是 [ZhiCore整体AI升级-交付计划.md](ZhiCore整体AI升级-交付计划.md)（下称《计划》）；本文规定执行方式、冻结契约、硬性约束与完成定义。二者冲突时：范围与验收以《计划》为准，执行规则以本文为准。

## 1. 执行模式

- 两个开发会话**顺序**执行（资源约束，禁止并行重构建）：S1 后端（DeepSeek flash）→ 集成检查 → S2 前端与内容（Qwen flash）→ 集成检查 → 修复轮。
- 统一在分支 `feature/ai-upgrade` 上工作（自当前 HEAD 切出）。每完成一个可验证里程碑提交一次；提交信息沿用仓库风格（中文说明 + feat/fix/docs/test 前缀）。**禁止 push、禁止部署、禁止改 git 配置。**
- 仓库现存未跟踪文件（artifacts/、docs/useless/ 等）一律不得 add、修改或删除。
- 每个会话结束时输出报告：修改文件清单、实际执行过的检查命令及结果、未完成项、对契约的变更（如有）。"代码已生成"不算完成。
- 集成检查由协调者（主会话）执行：跑《计划》9.1 的检查命令、审阅 diff、必要时派修复轮。

## 2. 文件所有权

| 会话 | 独占目录/文件 | 禁止触碰 |
| --- | --- | --- |
| S1 后端 | `后端代码/osLab-backend/**`、`数据库文件/oslab_upgrade_v11_ai_session.sql`、`数据库文件/oslab_upgrade_v12_ai_evidence.sql`、`scripts/dev/prepare-ai-demo.ps1`、`scripts/dev/smoke-ai-demo.ps1` | `前端代码/**`、`content/**` |
| S2 前端与内容 | `前端代码/osLab-frontend/**`、`content/**`（新增数据库课程包与诊断活动）、`前端代码/osLab-frontend/tests/*.js` | `后端代码/**`、`数据库文件/**` |

迁移编号 v11/v12 若已被占用，先报告再由协调者重新分配，不得自行挪用。

## 3. 冻结契约

API 沿用 `BaseResponse { code, data, message }` 与既有登录鉴权；路径相对 `/api`；长整型 ID 在 JSON 中为字符串。

公共对象（字段名冻结）：

```json
SourceRef   { "sourceId","ownerScope":"COURSE|PERSONAL","courseCode","revision","blockId","locator","excerpt" }
EvidenceSnapshot { "eventIds":[],"skillCode","result","helpLevel":"NONE|HINT|ANSWER_REVEALED|UNKNOWN","selfReportedHelp","eligibleForObservation":true,"eligibilityReason":"" }
NextAction  { "actionId","type":"READ|QUESTION|PREDICT|EXPLAIN|RETEST","targetRef":{"kind":"UNIT|QUESTION|SIMULATION|MATERIAL","code","anchor":null},"reason","evidenceIds":[],"estimatedMinutes":3,"status":"PENDING","returnRef":null }
Diagnosis   { "hypotheses":[{"code","label"}],"status":"TENTATIVE|SUPPORTED|INSUFFICIENT","supportingEventIds":[],"conflictingEventIds":[],"verificationActionIds":[] }
```

接口与最小响应（S1 实现，S2 按此消费；S2 可先按本样例 mock）：

```text
POST /learning/ai/sessions            {courseCode,goal,focus:LEARN|REVIEW,timeBudgetMinutes,modelProfileId?}
     -> {sessionId,courseCode,goal,focus,timeBudgetMinutes,currentAction,modelProfileId,rowVersion}
POST /learning/ai/sessions/{id}/turns {clientRequestId,message,context:{unitCode?,nodeCodes[],resourceRef?,selectedSourceIds[]}}
     -> {turnId,status:RUNNING|SUCCEEDED|FAILED_RETRYABLE|FAILED,answer,mode:AI|FALLBACK,
         sources:[SourceRef],toolTrace:[{tool,status,ms,summary}],diagnosis:Diagnosis?,nextActions:[NextAction],
         usage:{model,latencyMs,tokens?}}
GET  /learning/ai/turns/{turnId}      同上（轮询至终态，间隔≥1.5s）
POST /learning/ai/actions/{id}/start  -> {actionId,status,activityRef}
POST /learning/ai/actions/{id}/submit {submissionKey, choiceKey?|predictedProcess?|text?}
     -> 委托原有判分/PageReplacer：{result,correct?,judgeMessage?,evidence:EvidenceSnapshot}
GET  /learning/ai/next-actions?courseCode=&limit=5 -> {actions:[NextAction],generatedAt}
POST /learning/ai/materials           {courseCode,title,text|contentMd} -> {materialId,chunkCount,revision}
GET  /ai/model-profiles               -> [{profileId,name,baseUrlMasked,model,capabilities:{chat,structured,nativeTools},testedAt,active}]
POST /ai/model-profiles               {name,serviceType,baseUrl,model,apiKey} -> {profileId,status:CANDIDATE}
POST /ai/model-profiles/test          {profileId} -> {ok,latencyMs,capabilities,error?}
PATCH /ai/model-profiles/{id}         {active:true} 仅测试通过的候选可激活
```

保留 `/chat/ask`、`/learning/tutor/ask` 旧响应兼容，内部委托统一服务。契约变更必须先改本文与《计划》样例，再改调用方。

## 4. 硬性技术约束

- **不引入 Elasticsearch、向量数据库、消息队列、新中间件。** embedding 存 MySQL（模型名、维度、文本哈希、版本、向量），应用内算相似度；不同 embedding 模型的向量不得混用；换生成模型不触发重建索引。
- 部署目标 2 核 2GB（无 Docker、无 ES）：不得新增常驻重型依赖；后端保持可 `-Xmx256m -XX:+UseSerialGC` 启动。
- 构建资源：前端构建使用 `NODE_OPTIONS=--max-old-space-size=2048`；一次只跑一个构建/测试套件。
- 导师轮次预算：每轮最多 3 次工具执行、2 次模型调用；超时与重试后状态可解释；相同 `clientRequestId` 返回同一轮次。
- 模型不写分数、不写掌握度；活动提交一律走原有判分/`PageReplacer`；模型生成的题目不得作为正式评估证据。
- 帮助等级（NONE/HINT/ANSWER_REVEALED）必须在答题事务**之前**落库并参与证据资格；重复提交/换会话/换模型不得新增首次观测；系统错误与编译错误不降低掌握度。
- API Key 后端加密保存（主密钥来自环境变量），响应与日志只出现脱敏值；自定义 baseUrl 校验协议与目标，默认禁止访问本机内部服务。
- 检索/工具降级（embedding 或模型不可用）必须在响应 `mode` 与界面明确标注，不得把关键词回退包装成语义检索。
- 前端不新建独立"学习页/复习页"替代原入口；只做公共上下文、引用卡、工具轨迹、下一步卡、模型设置等组件并嵌入原页面。新增接口调用按 `src/api/learning.ts` 模式手写服务模块，不要求重新生成 OpenAPI client。
- 内容包遵循 `oslab-authoring/1.0`；新增 OS 诊断活动 ≤4 个；数据库课程包 = 3 知识点 + 3 段讲义 + 6 道客观题 + 误区映射，答案人工按闭包法核验；`node scripts/validate-course-content.mjs` 与 `verify-page-replacement-content.mjs` 必须通过。

## 5. 完成定义（P0 逐条对应《计划》§4）

S1 结束须满足：会话/轮次/动作/资料/模型配置接口真实可用；联合检索（语义+关键词+图谱）对已发布内容与个人资料生效且课程隔离；帮助与证据资格进入答题事务；诊断与下一步服务返回冻结结构；旧助手/导师走网关；后端 `.\mvnw.cmd test` 全绿。
S2 结束须满足：原首页/课程/图谱/题卡/模拟/记录/助手接入统一会话与下一步卡；预测先于轨迹揭示；帮助标识可见；模型设置页可测试并切换；数据库课程在同一套组件下可用；前端 lint、build、`tests/*.js` 全绿。
两者之后由协调者按《计划》§9 表格与 §10 演示脚本做端到端检查。

## 6. 禁止事项

禁止：push/部署/改 git 配置；伪造 AI 回复或判题结果通过验收；用录像/静态数据冒充现场能力；读取他人会话/资料/密钥；跨课程混用记录；在学生作答前向前端泄露答案；为展示组件机械堆测试；修改《计划》范围而不报告。

## 7. 记录

执行裁决与集成结果记入 `artifacts/ai-upgrade/ledger.md`（协调者维护）。演示数据与 smoke 结果写入 `artifacts/ai-platform-demo/`，不得包含密钥。
