package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.vo.learning.MasteryChangeVO;

import java.util.List;

/**
 * 学习事件账本服务（契约 §3.4）
 *
 * <p>职责：普通题/实验/模拟器三类事件统一写入（幂等），并驱动掌握状态更新。
 * 分项规则：ATTEMPT 更新 conceptScore；JUDGE/SIMULATION 更新 practiceScore；
 * pKnown 作为综合状态由两类事件按映射权重共同驱动。
 */
public interface LearningEventService extends IService<LearningEvent> {

    /**
     * 普通题作答后记录事件并更新掌握状态（每个映射节点一条事件）
     *
     * @param userId    学生 id
     * @param question  普通题
     * @param attemptId 作答记录 id（幂等键组成部分）
     * @param correct   是否答对
     * @param isFirst   是否该题首次作答（首次才是独立观测）
     * @return 每个关联节点的掌握度变化；题目未映射节点时返回空列表
     */
    List<MasteryChangeVO> recordAttemptEvents(Long userId, LearningQuestion question,
                                              Long attemptId, boolean correct, boolean isFirst);

    /**
     * 带证据资格的作答事件写入（AI 升级 §5.5）。
     *
     * <p>{@code qualification} 必须在答题事务之前就已确定（帮助等级先落库）：
     * 帮助等级为 HINT / ANSWER_REVEALED 时，即使这是该题首次作答也<b>不</b>作为独立观测，
     * 不更新 BKT、不增加证据数、不改可信度；事件仍完整写入并把帮助情况记进 evidence JSON，
     * 因此"看过答案"与"能够完成新题"在账本上是可区分的。
     *
     * @param isFirst      是否该题首次作答（终身口径）
     * @param qualification 证据资格判定；为 null 时按 UNKNOWN 处理（保持升级前行为）
     */
    List<MasteryChangeVO> recordAttemptEvents(Long userId, LearningQuestion question,
                                              Long attemptId, boolean correct, boolean isFirst,
                                              com.yupi.springbootinit.ai.evidence.EvidenceQualification qualification);

    /**
     * 某次作答产生的学习事件 id（按事件组键回读），供证据快照引用真实事件。
     * 只读当前用户的事件，绝不返回他人事件。
     */
    List<Long> eventIdsOfAttempt(Long userId, Long attemptId);

    /**
     * 编程实验判题终态接入（幂等：提交 ID + 节点为幂等键）。
     * 由 JudgeServiceImpl 在终态后调用；系统故障（FAILED）记录 SYSTEM_ERROR 事件，不计学生知识失误。
     * 题目标签映射不到图谱节点时记录诊断日志并跳过，不随意创造节点。
     *
     * @param questionSubmitId 提交 id
     * @param terminalStatus   终态：QuestionSubmitStatusEnum 的 SUCCEED/FAILED
     * @param judgeMessage     判题结果消息（如 Accepted / Wrong Answer）
     */
    void recordJudgeEvents(long questionSubmitId, int terminalStatus, String judgeMessage);

    /**
     * 模拟器预测练习事件（每次预测视为一次独立观测）
     *
     * @param userId        学生 id
     * @param nodeName      知识节点业务键（必须为图谱真实节点，否则跳过）
     * @param simulationKey 模拟练习唯一键（如 cpu:{hash}），幂等键组成部分
     * @param correct       预测是否正确
     * @param evidence      证据描述（JSON 字符串：算法、输入摘要、预测与真值）
     * @return 事件 id；节点无效时返回 null
     */
    Long recordSimulationEvent(Long userId, String nodeName, String simulationKey,
                               boolean correct, String evidence);

    /**
     * 校验节点是否为图谱真实节点
     */
    boolean isValidNode(String nodeName);
}
