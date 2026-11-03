package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 冻结契约 EvidenceSnapshot（任务书 §3）。
 *
 * <p>{@code helpLevel} 取 NONE / HINT / ANSWER_REVEALED / UNKNOWN；
 * {@code eligibleForObservation} 表示这次表现能否作为独立掌握观测。
 * 历史与非 AI 入口缺少帮助信息时标 UNKNOWN，不倒推成"未获得帮助"。
 */
@Data
public class EvidenceSnapshot implements Serializable {

    /** 本次观测产生的 learning_event id（字符串化长整型）。 */
    private List<String> eventIds = new ArrayList<>();

    private String skillCode;

    /** PASS / FAIL / SYSTEM_ERROR / COMPILE_ERROR 等，沿用原有 LearningResultEnum。 */
    private String result;

    private String helpLevel;

    /** 用户自报的外部帮助；平台只能确认平台内提示与自报，不声称知道全部情况。 */
    private String selfReportedHelp;

    private Boolean eligibleForObservation;

    private String eligibilityReason;
}
