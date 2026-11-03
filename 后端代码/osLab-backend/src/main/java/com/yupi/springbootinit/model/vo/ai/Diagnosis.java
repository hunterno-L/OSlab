package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 冻结契约 Diagnosis（任务书 §3）。
 *
 * <p>状态语义（计划 §5.4）：
 * <ul>
 *   <li>{@code TENTATIVE}：候选原因有依据，但还缺区分性证据；</li>
 *   <li>{@code SUPPORTED}：区分任务已执行且结果与假设一致；</li>
 *   <li>{@code INSUFFICIENT}：证据不足，保留不确定性，<b>不强判唯一原因</b>。</li>
 * </ul>
 * 一次错误不能直接断言唯一原因；系统/编译错误不得归为知识薄弱。
 */
@Data
public class Diagnosis implements Serializable {

    public static final String STATUS_TENTATIVE = "TENTATIVE";
    public static final String STATUS_SUPPORTED = "SUPPORTED";
    public static final String STATUS_INSUFFICIENT = "INSUFFICIENT";

    private List<Hypothesis> hypotheses = new ArrayList<>();

    /** TENTATIVE / SUPPORTED / INSUFFICIENT */
    private String status;

    private List<String> supportingEventIds = new ArrayList<>();

    private List<String> conflictingEventIds = new ArrayList<>();

    /** 用于区分候选原因的核验动作 id（指向本次返回的 NextAction）。 */
    private List<String> verificationActionIds = new ArrayList<>();

    /** 面向学生的一句话结论；证据不足时明确说明不足，不编造原因。 */
    private String explanation;

    @Data
    public static class Hypothesis implements Serializable {
        private String code;
        private String label;

        public Hypothesis() {
        }

        public Hypothesis(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }
}
