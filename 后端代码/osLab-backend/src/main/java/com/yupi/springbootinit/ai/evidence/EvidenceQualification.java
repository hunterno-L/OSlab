package com.yupi.springbootinit.ai.evidence;

import lombok.Data;

import java.io.Serializable;

/**
 * 一次作答的证据资格判定结果（计划 §5.5）。
 *
 * <p>该对象在答题事务<b>之前</b>就已确定：帮助等级先落库，
 * 事务内只读取判定结果，绝不先更新 BKT 再补帮助标签。
 *
 * <p>资格规则（可解释、可复算）：
 * <ul>
 *   <li>NONE —— 未获得平台帮助，可作为独立观测；</li>
 *   <li>UNKNOWN —— 缺少帮助记录（历史数据或非 AI 入口），仍可作为独立观测，
 *       但不倒推成"未获得帮助"；</li>
 *   <li>HINT —— 作答前已获得平台提示，<b>不</b>计为独立掌握观测；</li>
 *   <li>ANSWER_REVEALED —— 作答前已揭示答案，<b>不</b>计为独立掌握观测。</li>
 * </ul>
 * 平台只能确认平台内提示与用户自报，不声称知道学生是否用过外部工具。
 */
@Data
public class EvidenceQualification implements Serializable {

    /** NONE / HINT / ANSWER_REVEALED / UNKNOWN */
    public static final String HELP_NONE = "NONE";
    public static final String HELP_HINT = "HINT";
    public static final String HELP_ANSWER_REVEALED = "ANSWER_REVEALED";
    public static final String HELP_UNKNOWN = "UNKNOWN";

    public static final String SELF_HELP_NONE = "NONE";

    private Long contextId;

    private String helpLevel = HELP_UNKNOWN;

    private String selfReportedHelp = SELF_HELP_NONE;

    private boolean eligibleForObservation = true;

    private String eligibilityReason = "";

    /** 历史数据与非 AI 入口：没有证据上下文，按 UNKNOWN 处理并保持原有观测行为。 */
    public static EvidenceQualification unknown() {
        EvidenceQualification qualification = new EvidenceQualification();
        qualification.setHelpLevel(HELP_UNKNOWN);
        qualification.setEligibleForObservation(true);
        qualification.setEligibilityReason("缺少帮助记录（历史数据或非 AI 入口），不倒推为未获得帮助");
        return qualification;
    }

    /** 帮助等级的严重度序：帮助记录只能单调升级，晚到的低级别不得覆盖已揭示答案。 */
    public static int rank(String helpLevel) {
        if (HELP_ANSWER_REVEALED.equals(helpLevel)) {
            return 3;
        }
        if (HELP_HINT.equals(helpLevel)) {
            return 2;
        }
        if (HELP_UNKNOWN.equals(helpLevel)) {
            return 1;
        }
        return 0;
    }

    /** 由帮助等级推导资格；单一出处，避免各调用点各判一套。 */
    public static EvidenceQualification of(Long contextId, String helpLevel, String selfReportedHelp) {
        EvidenceQualification qualification = new EvidenceQualification();
        qualification.setContextId(contextId);
        String level = helpLevel == null ? HELP_UNKNOWN : helpLevel;
        qualification.setHelpLevel(level);
        qualification.setSelfReportedHelp(selfReportedHelp == null ? SELF_HELP_NONE : selfReportedHelp);
        if (HELP_ANSWER_REVEALED.equals(level)) {
            qualification.setEligibleForObservation(false);
            qualification.setEligibilityReason("本次作答前已揭示答案，不计为独立掌握观测");
        } else if (HELP_HINT.equals(level)) {
            qualification.setEligibleForObservation(false);
            qualification.setEligibilityReason("本次作答前已获得平台提示，不计为独立掌握观测");
        } else if (HELP_UNKNOWN.equals(level)) {
            qualification.setEligibleForObservation(true);
            qualification.setEligibilityReason("缺少帮助记录，不倒推为未获得帮助");
        } else {
            qualification.setEligibleForObservation(true);
            qualification.setEligibilityReason("未获得平台帮助，可作为独立观测");
        }
        return qualification;
    }
}
