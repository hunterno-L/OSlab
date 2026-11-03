package com.yupi.springbootinit.ai.evidence;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningEvidenceContextMapper;
import com.yupi.springbootinit.model.entity.LearningEvidenceContext;
import com.yupi.springbootinit.model.vo.ai.EvidenceSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 学习证据上下文服务（P0-6 / 任务书 §4）。
 *
 * <p>核心顺序要求：帮助等级必须在答题事务<b>之前</b>落库。
 * 因此本服务的写入方法一律用 {@code REQUIRES_NEW} 独立提交，
 * 答题事务随后通过 {@link #lockAndQualify} 以行锁读取判定结果——
 * 不存在"先更新 BKT 再补标签"的路径。
 *
 * <p>并发处理：帮助记录与提交都先 {@code SELECT ... FOR UPDATE} 锁住同一行，
 * 再配合 rowVersion 乐观校验；帮助等级只允许单调升级
 * （NONE → HINT → ANSWER_REVEALED），晚到的低级别写入不会把已揭示答案改回去。
 *
 * <p>幂等：{@code (userId, contextKey)} 唯一，重复 start 返回同一上下文，
 * 已消费的上下文不会再变成一次新的首次观测。
 */
@Slf4j
@Service
public class LearningEvidenceContextService {

    @Resource
    private LearningEvidenceContextMapper contextMapper;

    /**
     * 开始一次评估活动：创建（或复用）证据上下文，帮助等级从 NONE 起。
     * 独立事务提交，保证后续答题事务一定能读到。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public LearningEvidenceContext begin(Long userId, String contextKey, String activityKind,
                                        String activityCode, String courseCode, Integer questionRevision,
                                        String skillCode, Long sessionId, Long turnId) {
        if (userId == null || StringUtils.isBlank(contextKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "证据上下文缺少用户或幂等键");
        }
        LearningEvidenceContext existing = findByKey(userId, contextKey);
        if (existing != null) {
            return existing;
        }
        LearningEvidenceContext context = new LearningEvidenceContext();
        context.setUserId(userId);
        context.setContextKey(contextKey);
        context.setActivityKind(StringUtils.defaultIfBlank(activityKind, LearningEvidenceContext.KIND_QUESTION));
        context.setActivityCode(StringUtils.defaultString(activityCode));
        context.setCourseCode(courseCode);
        context.setQuestionRevision(questionRevision);
        context.setSkillCode(skillCode);
        context.setSessionId(sessionId);
        context.setTurnId(turnId);
        context.setHelpLevel(EvidenceQualification.HELP_NONE);
        context.setSelfReportedHelp(EvidenceQualification.SELF_HELP_NONE);
        EvidenceQualification qualification = EvidenceQualification.of(null, EvidenceQualification.HELP_NONE, null);
        context.setEligibleForObservation(qualification.isEligibleForObservation() ? 1 : 0);
        context.setEligibilityReason(qualification.getEligibilityReason());
        context.setConsumed(0);
        context.setRowVersion(0);
        context.setIsDelete(0);
        try {
            contextMapper.insert(context);
            return context;
        } catch (DuplicateKeyException e) {
            // 并发 start：唯一键冲突即表示另一线程已建好，直接复用
            LearningEvidenceContext concurrent = findByKey(userId, contextKey);
            if (concurrent == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "证据上下文创建失败");
            }
            return concurrent;
        }
    }

    /**
     * 记录帮助（提示 / 揭示答案 / 自报外部帮助）。独立事务，答题前生效。
     *
     * @return 记录后的资格判定
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public EvidenceQualification recordHelp(Long userId, Long contextId, String helpLevel,
                                            String selfReportedHelp, String sourceNote) {
        LearningEvidenceContext context = lockOwn(userId, contextId);
        String target = StringUtils.upperCase(StringUtils.trimToEmpty(helpLevel));
        if (!EvidenceQualification.HELP_NONE.equals(target)
                && !EvidenceQualification.HELP_HINT.equals(target)
                && !EvidenceQualification.HELP_ANSWER_REVEALED.equals(target)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法帮助等级：" + helpLevel);
        }
        String current = context.getHelpLevel();
        String merged = EvidenceQualification.rank(target) >= EvidenceQualification.rank(current)
                ? target : current;
        String mergedSelf = StringUtils.isBlank(selfReportedHelp)
                ? context.getSelfReportedHelp() : StringUtils.upperCase(selfReportedHelp.trim());
        EvidenceQualification qualification = EvidenceQualification.of(contextId, merged, mergedSelf);
        int updated = contextMapper.updateHelpWithVersion(contextId, merged, mergedSelf,
                appendHelpSource(context.getHelpSourceJson(), target, sourceNote),
                qualification.isEligibleForObservation() ? 1 : 0,
                qualification.getEligibilityReason(),
                context.getRowVersion());
        if (updated == 0) {
            // 与另一次帮助记录或提交并发：让调用方重读，绝不静默丢弃
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "帮助记录并发冲突，请重试");
        }
        log.info("[ai-evidence] help recorded context={} helpLevel={} self={} eligible={}",
                contextId, merged, mergedSelf, qualification.isEligibleForObservation());
        return qualification;
    }

    /**
     * 答题事务内调用：加行锁读取并给出资格判定。
     *
     * <p>行锁保证"提示刚写下、提交同时发生"时提交读到的是最新帮助等级，
     * 两者被串行化，不会出现帮助标签晚于掌握度更新。
     */
    public EvidenceQualification lockAndQualify(Long userId, Long contextId) {
        if (contextId == null) {
            return EvidenceQualification.unknown();
        }
        LearningEvidenceContext context = contextMapper.selectByIdForUpdate(contextId);
        if (context == null) {
            return EvidenceQualification.unknown();
        }
        if (!context.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该证据上下文");
        }
        EvidenceQualification qualification = EvidenceQualification.of(contextId,
                context.getHelpLevel(), context.getSelfReportedHelp());
        if (context.getConsumed() != null && context.getConsumed() == 1) {
            // 已消费：同一上下文不能刷新出第二次"首次观测"
            qualification.setEligibleForObservation(false);
            qualification.setEligibilityReason("该评估活动已提交过，重复提交不计新的独立观测");
        }
        return qualification;
    }

    /** 提交完成后回写结果与事件引用（不改变成绩事实源，只作回放索引）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void consume(Long userId, Long contextId, String result, Long attemptId, List<Long> eventIds) {
        if (contextId == null) {
            return;
        }
        LearningEvidenceContext context = lockOwn(userId, contextId);
        context.setConsumed(1);
        context.setResult(result);
        context.setAttemptId(attemptId);
        context.setEventIdsJson(AiJson.write(eventIds == null ? new ArrayList<Long>() : eventIds));
        contextMapper.updateById(context);
    }

    /** 组装冻结契约 EvidenceSnapshot。 */
    public EvidenceSnapshot snapshot(EvidenceQualification qualification, String skillCode,
                                     String result, List<Long> eventIds) {
        EvidenceSnapshot snapshot = new EvidenceSnapshot();
        snapshot.setEventIds(AiJson.ids(eventIds));
        snapshot.setSkillCode(skillCode);
        snapshot.setResult(result);
        snapshot.setHelpLevel(qualification.getHelpLevel());
        snapshot.setSelfReportedHelp(qualification.getSelfReportedHelp());
        snapshot.setEligibleForObservation(qualification.isEligibleForObservation());
        snapshot.setEligibilityReason(qualification.getEligibilityReason());
        return snapshot;
    }

    /** 只读：取本人证据上下文（越权一律拒绝）。 */
    public LearningEvidenceContext findOwn(Long userId, Long contextId) {
        if (contextId == null) {
            return null;
        }
        LearningEvidenceContext context = contextMapper.selectById(contextId);
        if (context == null || !context.getUserId().equals(userId)) {
            return null;
        }
        return context;
    }

    public LearningEvidenceContext findByKey(Long userId, String contextKey) {
        if (userId == null || StringUtils.isBlank(contextKey)) {
            return null;
        }
        List<LearningEvidenceContext> found = contextMapper.selectList(
                new LambdaQueryWrapper<LearningEvidenceContext>()
                        .eq(LearningEvidenceContext::getUserId, userId)
                        .eq(LearningEvidenceContext::getContextKey, contextKey)
                        .last("limit 1"));
        return found.isEmpty() ? null : found.get(0);
    }

    /** 活动幂等键：AI 动作流用 {@code action:<actionId>}。 */
    public static String actionContextKey(String actionId) {
        return "action:" + actionId;
    }

    /** 直接提交流（无 start）用 {@code question:<questionId>:<submissionKey>}。 */
    public static String submissionContextKey(Long questionId, String submissionKey) {
        return "question:" + questionId + ":" + StringUtils.defaultIfBlank(submissionKey, "live");
    }

    private LearningEvidenceContext lockOwn(Long userId, Long contextId) {
        if (contextId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "contextId 不能为空");
        }
        LearningEvidenceContext context = contextMapper.selectByIdForUpdate(contextId);
        if (context == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "证据上下文不存在");
        }
        if (!context.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该证据上下文");
        }
        return context;
    }

    /** 帮助来源明细：追加而不覆盖，保留完整帮助轨迹供回放与答辩核对。 */
    private static String appendHelpSource(String existingJson, String helpLevel, String note) {
        JSONObject root;
        try {
            root = StringUtils.isBlank(existingJson) ? new JSONObject() : JSONUtil.parseObj(existingJson);
        } catch (RuntimeException e) {
            root = new JSONObject();
        }
        List<Object> entries = root.getBeanList("entries", Object.class);
        if (entries == null) {
            entries = new ArrayList<>();
        }
        JSONObject entry = new JSONObject();
        entry.set("helpLevel", helpLevel);
        entry.set("note", StringUtils.abbreviate(StringUtils.defaultString(note), 200));
        entry.set("at", new Date().getTime());
        entries.add(entry);
        root.set("entries", entries);
        return root.toString();
    }
}
