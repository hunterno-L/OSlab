package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 冻结契约：{@code POST /learning/ai/actions/{id}/submit} 响应
 * {@code {result, correct?, judgeMessage?, evidence:EvidenceSnapshot}}。
 *
 * <p>{@code correct} 一律来自原有判分服务或 {@code PageReplacer}，
 * 模型不参与判分；{@code evidence} 说明这次表现是否计入独立掌握观测。
 */
@Data
public class ActionSubmitResultVO implements Serializable {

    /** PASS / FAIL / RECORDED / SYSTEM_ERROR / COMPILE_ERROR / NOT_ASSESSED */
    private String result;

    /** 客观题为 true/false；非评估类动作为 null（不伪装成通过评估）。 */
    private Boolean correct;

    private String judgeMessage;

    private EvidenceSnapshot evidence;

    /** 提交后允许展示的解析（仅在本人已提交后下发，永不含未作答前的答案）。 */
    private String analysis;

    /** 预测类活动：服务端计算器核对出的差异步骤说明。 */
    private List<String> diffSteps = new ArrayList<>();

    /** 本次提交产生的掌握度变化（沿用原有 MasteryChangeVO，只读展示）。 */
    private List<com.yupi.springbootinit.model.vo.learning.MasteryChangeVO> masteryChanges = new ArrayList<>();

    /** 提交后安排的下一阶梯复习到期时间（带时区）。 */
    private String nextReviewDueAt;
}
