package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 学习证据上下文（v12）：当前活动 + 帮助记录 + 资格判定。
 *
 * <p>硬性顺序（任务书 §4）：帮助等级必须在答题事务**之前**落库，
 * 由答题事务读取本行决定证据资格；不允许先更新 BKT 再补标签。
 *
 * <p>资格规则：NONE / UNKNOWN 可作为独立观测；HINT / ANSWER_REVEALED 不可。
 * UNKNOWN 用于缺少帮助信息的历史与非 AI 入口，不倒推成"未获得帮助"。
 *
 * <p>本表不写掌握度：掌握度只由 learning_event 经原有 BKT 链路驱动。
 *
 * @TableName learning_evidence_context
 */
@TableName(value = "learning_evidence_context")
@Data
public class LearningEvidenceContext implements Serializable {

    public static final String HELP_NONE = "NONE";
    public static final String HELP_HINT = "HINT";
    public static final String HELP_ANSWER_REVEALED = "ANSWER_REVEALED";
    public static final String HELP_UNKNOWN = "UNKNOWN";

    public static final String KIND_QUESTION = "QUESTION";
    public static final String KIND_PREDICT = "PREDICT";
    public static final String KIND_RETEST = "RETEST";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 幂等键：{@code action:<actionId>} 或 {@code question:<id>:<submissionKey>}。 */
    private String contextKey;

    private String courseCode;

    /** QUESTION / PREDICT / RETEST */
    private String activityKind;

    private String activityCode;

    private Integer questionRevision;

    private String skillCode;

    private Long sessionId;

    private Long turnId;

    private Long attemptId;

    /** NONE / HINT / ANSWER_REVEALED / UNKNOWN —— 只允许单调升级。 */
    private String helpLevel;

    /** 用户自报的外部帮助（NONE / EXTERNAL_AI / OTHER / PEER）。 */
    private String selfReportedHelp;

    /** 帮助来源明细 JSON：平台提示轮次、揭示答案的动作等。 */
    private String helpSourceJson;

    private Integer eligibleForObservation;

    private String eligibilityReason;

    /** 提交后的结果（PASS/FAIL），仅用于回放，不作为另一份成绩事实源。 */
    private String result;

    /** 本次观测产生的 learning_event id 列表 JSON。 */
    private String eventIdsJson;

    /** 已被一次提交消费；消费后不得再作为新的首次观测。 */
    private Integer consumed;

    /** 乐观锁：帮助记录与提交并发时串行化。 */
    private Integer rowVersion;

    private Integer isDelete;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public boolean isEligible() {
        return eligibleForObservation != null && eligibleForObservation == 1;
    }
}
