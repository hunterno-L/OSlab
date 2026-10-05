package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.bkt.BktModel;
import com.yupi.springbootinit.learning.MasterySnapshotCodec;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.enums.JudgeInfoMessageEnum;
import com.yupi.springbootinit.model.enums.QuestionSubmitStatusEnum;
import com.yupi.springbootinit.model.enums.learning.LearningEventTypeEnum;
import com.yupi.springbootinit.model.enums.learning.LearningResourceTypeEnum;
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import com.yupi.springbootinit.model.enums.learning.MasteryStatusEnum;
import com.yupi.springbootinit.model.vo.learning.MasteryChangeVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningEventService;
import com.yupi.springbootinit.service.QuestionService;
import com.yupi.springbootinit.service.QuestionSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 学习事件账本服务实现（契约 §3.4）
 *
 * <p>幂等策略：learning_event.idempotencyKey 唯一索引 + 插入捕获 DuplicateKeyException，
 * 判题异步重试或重复读取提交不会重复写事件、不会重复更新掌握状态。
 *
 * <p>分项规则（契约 §3.3）：ATTEMPT 更新 conceptScore；JUDGE/SIMULATION 更新 practiceScore；
 * pKnown 为综合 BKT 后验，由两类事件按映射权重共同驱动。
 * masteryBefore/After 在事件插入前计算并随事件一并落库，事件写入后不再修改（不可变账本）。
 */
@Slf4j
@Service
public class LearningEventServiceImpl extends ServiceImpl<LearningEventMapper, LearningEvent>
        implements LearningEventService {

    @Resource
    private LearningEventMapper learningEventMapper;

    @Resource
    private LearningMasteryMapper learningMasteryMapper;

    @Resource
    private LearningQuestionNodeMapper learningQuestionNodeMapper;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Resource
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    /**
     * 判题事件写入模板（Phase 7 §11.2 事务边界修正）：
     * recordJudgeEvents 由判题主链路异步调用且自带兜底 catch，不能用 @Transactional
     * 依赖异常穿透回滚（异常在方法内部被吞掉会导致部分提交）。
     * 这里用编程式事务包裹全部节点的事件+状态写入，任一节点失败整体回滚后由外层记录日志。
     */
    private TransactionTemplate judgeTxTemplate;

    @PostConstruct
    void initJudgeTxTemplate() {
        this.judgeTxTemplate = new TransactionTemplate(transactionManager);
    }

    // region 普通题作答事件

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MasteryChangeVO> recordAttemptEvents(Long userId, LearningQuestion question,
                                                     Long attemptId, boolean correct, boolean isFirst) {
        List<MasteryChangeVO> changes = new ArrayList<>();
        List<LearningQuestionNode> mappings = learningQuestionNodeMapper.selectList(
                new LambdaQueryWrapper<LearningQuestionNode>()
                        .eq(LearningQuestionNode::getQuestionId, question.getId()));
        if (CollectionUtils.isEmpty(mappings)) {
            return changes;
        }
        String groupKey = "attempt:" + attemptId;
        for (LearningQuestionNode mapping : mappings) {
            String nodeName = mapping.getNodeName();
            double weight = mapping.getWeight() == null ? 1.0 : Math.max(0.0, Math.min(1.0, mapping.getWeight()));
            String idempotencyKey = "attempt:" + attemptId + ":" + nodeName;
            LearningEvent event = buildEvent(userId, nodeName,
                    LearningEventTypeEnum.ATTEMPT, LearningResourceTypeEnum.LEARNING_QUESTION,
                    question.getId(), correct, isFirst, idempotencyKey, groupKey,
                    JSONUtil.toJsonStr(new Evidence(
                            "普通题作答", question.getTitle(), correct ? "答对" : "答错",
                            isFirst ? "首次作答（独立观测）" : "重复作答（不计独立观测）")));
            // U15：重复作答也写入行为事件（before==after，不更新模型）；
            // 仅首次（独立观测）才执行 BKT 更新。
            boolean counted;
            if (isFirst) {
                counted = insertWithMasteryUpdate(userId, nodeName, event, correct, weight, true);
            } else {
                counted = insertRepeatEvent(userId, nodeName, event);
            }
            changes.add(toChangeVO(userId, nodeName, event, counted));
        }
        return changes;
    }

    // endregion

    // region 实验判题终态事件

    @Override
    public void recordJudgeEvents(long questionSubmitId, int terminalStatus, String judgeMessage) {
        try {
            QuestionSubmit submit = questionSubmitService.getById(questionSubmitId);
            if (submit == null) {
                log.warn("[learning] judge event skipped, submit not found: {}", questionSubmitId);
                return;
            }
            Question question = questionService.getById(submit.getQuestionId());
            if (question == null || StringUtils.isBlank(question.getTags())) {
                return;
            }
            // 阶段 5 §5.3 观测分离：结果分类统一经 LearningResultEnum 的观测性质判定，
            // 掌握度更新与否由 isKnowledgeObservation() 决定，不得仅靠结果字符串判断。
            // 编译错误保留为诊断事件（供查看代码问题），但不更新 BKT/独立证据/置信度。
            LearningResultEnum result = classifyJudgeResult(terminalStatus, judgeMessage);
            boolean accepted = result == LearningResultEnum.PASS;

            // §11.2 事务边界：一次提交的全部节点事件+状态更新同事务（编程式事务，
            // 异常向外抛给本方法 catch，保证回滚而非部分提交）
            judgeTxTemplate.executeWithoutResult(txStatus -> {
                List<String> candidateNodes = JSONUtil.toList(question.getTags(), String.class);
                for (String nodeName : candidateNodes) {
                    if (StringUtils.isBlank(nodeName) || !isValidNode(nodeName)) {
                        // 标签映射不上真实节点：诊断日志，不创造节点
                        log.info("[learning] judge tag not mapped to graph node, skip. submitId={}, tag={}",
                                questionSubmitId, nodeName);
                        continue;
                    }
                    String idempotencyKey = "judge:" + questionSubmitId + ":" + nodeName;
                    // 事件详情保留：来源/实验（title）、错误类型（note=judgeMessage）、
                    // 语言（language）、时间（createTime 由落库补齐），供 Tutor 与归因使用
                    LearningEvent event = buildEvent(submit.getUserId(), nodeName,
                            LearningEventTypeEnum.JUDGE, LearningResourceTypeEnum.QUESTION_SUBMIT,
                            questionSubmitId, accepted, result.isKnowledgeObservation(), idempotencyKey,
                            "judge:" + questionSubmitId,
                            JSONUtil.toJsonStr(new Evidence(
                                    "编程实验判题", question.getTitle(), result.getText(),
                                    StringUtils.defaultIfBlank(judgeMessage, result.getText()),
                                    submit.getLanguage())));
                    // 编译错误/系统错误不能被记成 FAIL：诊断事件不计学生知识失误
                    event.setResult(result.name());
                    if (result.isKnowledgeObservation()) {
                        insertWithMasteryUpdate(submit.getUserId(), nodeName, event, accepted, 1.0, false);
                    } else {
                        // 诊断事件：前后快照相同，不调用 BKT、不增加证据数、不改置信度
                        insertFrozenEvent(submit.getUserId(), nodeName, event);
                    }
                }
            });
        } catch (Exception e) {
            // 学习事件写入失败绝不能影响判题主链路
            log.error("[learning] recordJudgeEvents failed, submitId={}", questionSubmitId, e);
        }
    }

    /**
     * 判题终态 -> 学习结果分类（阶段 5 §5.3）：
     * FAILED 为系统错误（平台故障）；编译错误为诊断事件（不计知识失误）；
     * 其余终态按 Accepted 与否归为 PASS/FAIL 真实知识观测。
     *
     * <p>fix round 1：判题消息可能来自沙箱原文（英文 "Compile Error"，即
     * JudgeInfoMessageEnum.COMPILE_ERROR 的 text），而 getEnumByValue 只匹配
     * value（中文"编译错误"）——此前 CE 分支对英文消息永不命中，CE 被误归为
     * FAIL 知识观测并更新掌握度。现按 value/text/关键字三重匹配。
     */
    static LearningResultEnum classifyJudgeResult(int terminalStatus, String judgeMessage) {
        if (terminalStatus == QuestionSubmitStatusEnum.FAILED.getValue()) {
            return LearningResultEnum.SYSTEM_ERROR;
        }
        if (isCompileErrorMessage(judgeMessage)) {
            return LearningResultEnum.COMPILE_ERROR;
        }
        boolean accepted = terminalStatus == QuestionSubmitStatusEnum.SUCCEED.getValue()
                && JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeMessage);
        return accepted ? LearningResultEnum.PASS : LearningResultEnum.FAIL;
    }

    /**
     * 编译错误消息判定：value（"编译错误"）/ text（"Compile Error"）/
     * 沙箱原文关键字（compile/编译）三重匹配，不依赖单一字符串形态。
     */
    private static boolean isCompileErrorMessage(String judgeMessage) {
        if (StringUtils.isBlank(judgeMessage)) {
            return false;
        }
        if (JudgeInfoMessageEnum.COMPILE_ERROR.getValue().equals(judgeMessage)
                || JudgeInfoMessageEnum.COMPILE_ERROR.getText().equalsIgnoreCase(judgeMessage.trim())) {
            return true;
        }
        String lower = judgeMessage.toLowerCase();
        return lower.contains("compile") || judgeMessage.contains("编译");
    }

    // endregion

    // region 模拟器预测事件

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordSimulationEvent(Long userId, String nodeName, String simulationKey,
                                      boolean correct, String evidence) {
        if (StringUtils.isBlank(nodeName) || !isValidNode(nodeName)) {
            log.info("[learning] simulation node invalid, skip event. userId={}, node={}", userId, nodeName);
            return null;
        }
        String idempotencyKey = "sim:" + userId + ":" + simulationKey + ":" + nodeName;
        LearningEvent event = buildEvent(userId, nodeName,
                LearningEventTypeEnum.SIMULATION, LearningResourceTypeEnum.SIMULATION,
                0L, correct, true, idempotencyKey,
                "sim:" + userId + ":" + simulationKey, evidence);
        insertWithMasteryUpdate(userId, nodeName, event, correct, 1.0, false);
        return event.getId();
    }

    // endregion

    // region 公共工具

    @Override
    public boolean isValidNode(String nodeName) {
        if (StringUtils.isBlank(nodeName)) {
            return false;
        }
        try {
            return CollectionUtils.isNotEmpty(knowledgeGraphService.findByName(nodeName));
        } catch (Exception e) {
            // Neo4j 不可用时保守处理：视为无效节点，不产生事件
            log.warn("[learning] validate node failed: {}", nodeName, e);
            return false;
        }
    }

    /**
     * 构建事件（不可变记录；masteryBefore/After 与快照由插入路径补齐）
     */
    private LearningEvent buildEvent(Long userId, String nodeName, LearningEventTypeEnum eventType,
                                     LearningResourceTypeEnum resourceType, Long resourceId,
                                     boolean correct, boolean isFirst, String idempotencyKey,
                                     String eventGroupKey, String evidence) {
        LearningEvent event = new LearningEvent();
        event.setUserId(userId);
        event.setNodeName(nodeName);
        event.setEventType(eventType.name());
        event.setResourceType(resourceType.name());
        event.setResourceId(resourceId);
        event.setResult(correct ? LearningResultEnum.PASS.name() : LearningResultEnum.FAIL.name());
        event.setIsFirst(isFirst ? 1 : 0);
        event.setEvidence(evidence);
        event.setIdempotencyKey(idempotencyKey);
        event.setEventGroupKey(eventGroupKey);
        return event;
    }

    /**
     * 非独立观测（重复作答）：写入行为事件，before==after 取当前掌握度，
     * 不更新掌握模型（pKnown/证据数/状态/lastEventId 均不变）。
     * §11.2：非独立观测同样保存完整前后快照（before==after）。
     */
    private boolean insertRepeatEvent(Long userId, String nodeName, LearningEvent event) {
        LearningMastery mastery = learningMasteryMapper.selectOne(
                new LambdaQueryWrapper<LearningMastery>()
                        .eq(LearningMastery::getUserId, userId)
                        .eq(LearningMastery::getNodeName, nodeName));
        double current = mastery == null ? BktModel.DEFAULT_P_INIT : mastery.getPKnown();
        MasterySnapshotCodec.MasterySnapshot snapshot = MasterySnapshotCodec.snapshotOf(mastery);
        ensureBaseline(userId, nodeName, snapshot);
        event.setMasteryBefore(current);
        event.setMasteryAfter(current);
        String snapshotJson = MasterySnapshotCodec.toJson(snapshot);
        event.setSnapshotBefore(snapshotJson);
        event.setSnapshotAfter(snapshotJson);
        return insertIdempotent(event);
    }

    /**
     * 不更新模型的事件写入（§11.2 系统问题等）：完整前后快照（before==after 当前真实状态），
     * 不调用 BKT、不增加证据数、不改 lastEventId。
     */
    private void insertFrozenEvent(Long userId, String nodeName, LearningEvent event) {
        LearningMastery mastery = learningMasteryMapper.selectOne(
                new LambdaQueryWrapper<LearningMastery>()
                        .eq(LearningMastery::getUserId, userId)
                        .eq(LearningMastery::getNodeName, nodeName));
        double current = mastery == null ? BktModel.DEFAULT_P_INIT : mastery.getPKnown();
        MasterySnapshotCodec.MasterySnapshot snapshot = MasterySnapshotCodec.snapshotOf(mastery);
        ensureBaseline(userId, nodeName, snapshot);
        event.setMasteryBefore(current);
        event.setMasteryAfter(current);
        String snapshotJson = MasterySnapshotCodec.toJson(snapshot);
        event.setSnapshotBefore(snapshotJson);
        event.setSnapshotAfter(snapshotJson);
        insertIdempotent(event);
    }

    /**
     * 幂等插入：唯一键冲突视为已写入，返回 false
     */
    private boolean insertIdempotent(LearningEvent event) {
        try {
            learningEventMapper.insert(event);
            return true;
        } catch (DuplicateKeyException e) {
            log.info("[learning] event already recorded (idempotent skip): {}", event.getIdempotencyKey());
            return false;
        }
    }

    /**
     * 掌握度行级锁：同一 (userId, nodeName) 的读-改-写必须串行。
     * 异步判题（CompletableFuture）可能并发触发同一学生同一节点的事件写入，
     * 无锁时会互相覆盖 pKnown/证据计数（E2E 已复现：两次判题都读到相同 before，
     * 后写者丢失先写者的更新）。
     */
    private static final int MASTERY_LOCK_STRIPES = 64;
    private final Object[] masteryLocks = new Object[MASTERY_LOCK_STRIPES];

    {
        for (int i = 0; i < MASTERY_LOCK_STRIPES; i++) {
            masteryLocks[i] = new Object();
        }
    }

    /**
     * 计算一次独立观测的掌握状态变化，将 masteryBefore/After 随事件一并插入，
     * 再落库掌握状态。事件插入冲突（幂等重复）时不动掌握状态。
     *
     * @param weight      该观测对综合状态的影响权重（普通题来自映射 weight，其余为 1）
     * @param conceptSide true=概念观测（ATTEMPT），false=实践观测（JUDGE/SIMULATION）
     * @return 事件是否为新写入（true=本次观测生效）
     */
    private boolean insertWithMasteryUpdate(Long userId, String nodeName, LearningEvent event,
                                            boolean correct, double weight, boolean conceptSide) {
        Object lock = masteryLocks[Math.floorMod(
                (userId + ":" + nodeName).hashCode(), MASTERY_LOCK_STRIPES)];
        synchronized (lock) {
            return doInsertWithMasteryUpdate(userId, nodeName, event, correct, weight, conceptSide);
        }
    }

    private boolean doInsertWithMasteryUpdate(Long userId, String nodeName, LearningEvent event,
                                              boolean correct, double weight, boolean conceptSide) {
        LearningMastery mastery = getOrCreateMastery(userId, nodeName);
        double before = mastery.getPKnown();
        // §11.2 完整快照：事件前 7 字段状态（分项/证据数/状态一并记录）
        MasterySnapshotCodec.MasterySnapshot beforeSnapshot = MasterySnapshotCodec.snapshotOf(mastery);

        // 综合状态：BKT 更新；权重不足 1 时按比例趋近目标（可解释、可复算）。
        // fix round 3（裁决 L3-R12）：对错两种观测均按阶段一冻结契约走标准 5 参
        // BktModel.update（对错都叠加 pLearn 学习转移）；round 2 的"失败取纯后验"
        // 与冻结契约及历史验收（0.10→0.112，验收记录-2026-09-29 §3）冲突，已回退。
        double fullUpdate = BktModel.update(before, correct,
                BktModel.DEFAULT_P_LEARN, BktModel.DEFAULT_P_GUESS, BktModel.DEFAULT_P_SLIP);
        double after = before + weight * (fullUpdate - before);

        // fix round 1（阶段 5 §5.3 幂等硬要求）：事件后的新值先算在局部变量，
        // 掌握状态行在事件插入成功前零突变——重复回调（幂等键冲突）不得产生任何增量。
        double conceptBefore = hasConceptEvidence(mastery)
                ? mastery.getConceptScore() : BktModel.DEFAULT_P_INIT;
        double newConceptScore = BktModel.update(conceptBefore, correct,
                BktModel.DEFAULT_P_LEARN, BktModel.DEFAULT_P_GUESS, BktModel.DEFAULT_P_SLIP);
        int newConceptCount = conceptSide ? conceptCount(mastery) + 1 : conceptCount(mastery);
        double practiceBefore = hasPracticeEvidence(mastery)
                ? mastery.getPracticeScore() : BktModel.DEFAULT_P_INIT;
        double newPracticeScore = BktModel.update(practiceBefore, correct,
                BktModel.DEFAULT_P_LEARN, BktModel.DEFAULT_P_GUESS, BktModel.DEFAULT_P_SLIP);
        int newPracticeCount = conceptSide ? practiceCount(mastery) : practiceCount(mastery) + 1;

        // §11.2 状态基线：该节点存在无快照的历史事件时，先以事件前真实状态补基线
        ensureBaseline(userId, nodeName, beforeSnapshot);

        // 事件后完整状态（含本次观测的新证据数；新来源一并计入可信度来源数）
        Set<String> sources = distinctSources(userId, nodeName);
        sources.add(event.getResourceType() + ":" + event.getResourceId());
        double newConfidence = BktModel.confidence(sources.size());
        String newStatus = MasteryStatusEnum.derive(after, newConceptCount, newPracticeCount).name();

        // 事件在插入时即携带变化前后值与完整快照，落库后不再修改（不可变账本）
        event.setMasteryBefore(before);
        event.setMasteryAfter(after);
        event.setSnapshotBefore(MasterySnapshotCodec.toJson(beforeSnapshot));
        MasterySnapshotCodec.MasterySnapshot afterSnapshot = MasterySnapshotCodec.snapshotOf(mastery);
        afterSnapshot.pKnown = after;
        afterSnapshot.confidence = newConfidence;
        afterSnapshot.status = newStatus;
        afterSnapshot.conceptScore = conceptSide ? newConceptScore : afterSnapshot.conceptScore;
        afterSnapshot.conceptEvidenceCount =
                conceptSide ? newConceptCount : afterSnapshot.conceptEvidenceCount;
        afterSnapshot.practiceScore = conceptSide ? afterSnapshot.practiceScore : newPracticeScore;
        afterSnapshot.practiceEvidenceCount =
                conceptSide ? afterSnapshot.practiceEvidenceCount : newPracticeCount;
        event.setSnapshotAfter(MasterySnapshotCodec.toJson(afterSnapshot));
        if (!insertIdempotent(event)) {
            // 幂等冲突：掌握状态行保持事件前状态（零突变），仅本次事件不落库
            return false;
        }

        // 事件插入成功后才允许改写掌握状态行并落库
        if (conceptSide) {
            mastery.setConceptScore(newConceptScore);
            mastery.setConceptEvidenceCount(newConceptCount);
        } else {
            mastery.setPracticeScore(newPracticeScore);
            mastery.setPracticeEvidenceCount(newPracticeCount);
        }
        mastery.setPKnown(after);
        mastery.setConfidence(newConfidence);
        mastery.setStatus(newStatus);
        mastery.setLastEventId(event.getId());
        if (mastery.getId() == null) {
            learningMasteryMapper.insert(mastery);
        } else {
            learningMasteryMapper.updateById(mastery);
        }
        return true;
    }

    /**
     * §11.2 状态基线：该 (userId, nodeName) 存在无快照的历史事件且尚无基线时，
     * 以事件前真实掌握状态写一条 STATE_BASELINE 事件（携带完整 after 快照）。
     * 这不是学习观测：不调用 BKT、不增加证据数、不改 lastEventId、不计入学习活动或班级统计。
     * 全新节点（无历史事件）首事件自带完整 before 快照，无需基线。
     */
    private void ensureBaseline(Long userId, String nodeName,
                                MasterySnapshotCodec.MasterySnapshot preState) {
        try {
            Long baselineCount = learningEventMapper.selectCount(
                    new LambdaQueryWrapper<LearningEvent>()
                            .eq(LearningEvent::getUserId, userId)
                            .eq(LearningEvent::getNodeName, nodeName)
                            .eq(LearningEvent::getEventType, LearningEventTypeEnum.STATE_BASELINE.name()));
            if (baselineCount != null && baselineCount > 0) {
                return;
            }
            Long legacyCount = learningEventMapper.selectCount(
                    new LambdaQueryWrapper<LearningEvent>()
                            .eq(LearningEvent::getUserId, userId)
                            .eq(LearningEvent::getNodeName, nodeName)
                            .isNull(LearningEvent::getSnapshotAfter));
            if (legacyCount == null || legacyCount == 0) {
                return;
            }
            MasterySnapshotCodec.MasterySnapshot state = preState == null
                    ? MasterySnapshotCodec.snapshotOf(null) : preState;
            double pKnown = state.pKnown == null ? BktModel.DEFAULT_P_INIT : state.pKnown;
            String snapshotJson = MasterySnapshotCodec.toJson(state);
            String groupKey = "baseline:" + userId + ":" + nodeName;
            LearningEvent baseline = new LearningEvent();
            baseline.setUserId(userId);
            baseline.setNodeName(nodeName);
            baseline.setEventType(LearningEventTypeEnum.STATE_BASELINE.name());
            baseline.setResourceType(LearningResourceTypeEnum.STATE_BASELINE.name());
            baseline.setResourceId(0L);
            baseline.setResult(LearningResultEnum.BASELINE.name());
            baseline.setIsFirst(0);
            baseline.setMasteryBefore(pKnown);
            baseline.setMasteryAfter(pKnown);
            baseline.setSnapshotBefore(snapshotJson);
            baseline.setSnapshotAfter(snapshotJson);
            baseline.setEventGroupKey(groupKey);
            baseline.setIdempotencyKey(groupKey);
            baseline.setEvidence(JSONUtil.toJsonStr(new Evidence(
                    "状态基线", nodeName, "状态基线",
                    "记录当前完整掌握状态，作为可信回放起点（非学习观测，不计证据与掌握度）")));
            insertIdempotent(baseline);
        } catch (Exception e) {
            // 基线补写失败不阻塞主事件写入
            log.warn("[learning] ensure baseline failed, user={}, node={}", userId, nodeName, e);
        }
    }

    private LearningMastery getOrCreateMastery(Long userId, String nodeName) {
        LearningMastery mastery = learningMasteryMapper.selectOne(new LambdaQueryWrapper<LearningMastery>()
                .eq(LearningMastery::getUserId, userId)
                .eq(LearningMastery::getNodeName, nodeName));
        if (mastery == null) {
            mastery = new LearningMastery();
            mastery.setUserId(userId);
            mastery.setNodeName(nodeName);
            mastery.setPKnown(BktModel.DEFAULT_P_INIT);
            mastery.setConfidence(0.0);
            mastery.setConceptScore(0.0);
            mastery.setPracticeScore(0.0);
            mastery.setConceptEvidenceCount(0);
            mastery.setPracticeEvidenceCount(0);
            mastery.setStatus(MasteryStatusEnum.GRAY.name());
        }
        return mastery;
    }

    private boolean hasConceptEvidence(LearningMastery mastery) {
        return mastery.getConceptEvidenceCount() != null && mastery.getConceptEvidenceCount() > 0
                && mastery.getConceptScore() != null;
    }

    private boolean hasPracticeEvidence(LearningMastery mastery) {
        return mastery.getPracticeEvidenceCount() != null && mastery.getPracticeEvidenceCount() > 0
                && mastery.getPracticeScore() != null;
    }

    private int conceptCount(LearningMastery mastery) {
        return mastery.getConceptEvidenceCount() == null ? 0 : mastery.getConceptEvidenceCount();
    }

    private int practiceCount(LearningMastery mastery) {
        return mastery.getPracticeEvidenceCount() == null ? 0 : mastery.getPracticeEvidenceCount();
    }

    /**
     * 可信度来源集合（§11.2）：该学生该节点下不同来源资源数（跨题型/实验/模拟）。
     * 只统计有效独立观测：状态基线、编译错误、系统故障、重复作答事件不能通过后续计数间接提高可信度。
     */
    private Set<String> distinctSources(Long userId, String nodeName) {
        List<LearningEvent> events = learningEventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .eq(LearningEvent::getNodeName, nodeName));
        Set<String> sources = new LinkedHashSet<>();
        for (LearningEvent e : events) {
            if (LearningEventTypeEnum.STATE_BASELINE.name().equals(e.getEventType())
                    || !isKnowledgeObservationResult(e.getResult())
                    || e.getIsFirst() == null || e.getIsFirst() != 1) {
                continue;
            }
            sources.add(e.getResourceType() + ":" + e.getResourceId());
        }
        return sources;
    }

    /**
     * 事件 result 是否为独立知识观测（PASS/FAIL）：诊断（编译/系统错误）与基线都不是
     */
    private static boolean isKnowledgeObservationResult(String result) {
        LearningResultEnum parsed = LearningResultEnum.parse(result);
        return parsed != null && parsed.isKnowledgeObservation();
    }

    private int countDistinctSources(Long userId, String nodeName) {
        return distinctSources(userId, nodeName).size();
    }

    private MasteryChangeVO toChangeVO(Long userId, String nodeName, LearningEvent event, boolean counted) {
        MasteryChangeVO vo = new MasteryChangeVO();
        vo.setNodeName(nodeName);
        vo.setMasteryBefore(event.getMasteryBefore());
        vo.setMasteryAfter(event.getMasteryAfter());
        LearningMastery mastery = learningMasteryMapper.selectOne(new LambdaQueryWrapper<LearningMastery>()
                .eq(LearningMastery::getUserId, userId)
                .eq(LearningMastery::getNodeName, nodeName));
        vo.setStatus(mastery == null ? MasteryStatusEnum.GRAY.name() : mastery.getStatus());
        vo.setCountedAsObservation(counted);
        return vo;
    }

    // endregion

    /**
     * 证据 JSON 结构
     */
    public static class Evidence {
        public String source;
        public String title;
        public String resultText;
        public String note;
        /** 提交语言（判题事件保留，供 Tutor 与归因使用；其他事件为空） */
        public String language;

        public Evidence(String source, String title, String resultText, String note) {
            this(source, title, resultText, note, null);
        }

        public Evidence(String source, String title, String resultText, String note, String language) {
            this.source = source;
            this.title = title;
            this.resultText = resultText;
            this.note = note;
            this.language = language;
        }
    }
}
