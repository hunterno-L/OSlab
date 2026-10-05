package com.yupi.springbootinit.learning;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则错误归因（Phase 7 契约 §11.1）：统一处理普通题作答与实验判题终态，
 * 区分普通题错误、WA、RE、TLE、编译错误、系统错误。纯函数、确定性、可复算。
 *
 * <p>AI 只将本结构转成自然语言；节点状态仍由事件与 BKT 决定，归因不改变掌握度。
 */
public final class LearningAttributionService {

    public static final String ERROR_WRONG_ANSWER = "WRONG_ANSWER";
    public static final String ERROR_RUNTIME = "RUNTIME_ERROR";
    public static final String ERROR_TIME_LIMIT = "TIME_LIMIT";
    public static final String ERROR_COMPILE = "COMPILE_ERROR";
    public static final String ERROR_SYSTEM = "SYSTEM_ERROR";

    private LearningAttributionService() {
    }

    /**
     * 归因结果
     */
    public static final class AttributionResult {
        private final String nodeName;
        private final String errorType;
        private final double confidence;
        private final List<String> evidence;
        private final String recommendedAction;
        /** 是否计入学生知识失误（编译错误/系统错误为 false，不降低掌握度） */
        private final boolean knowledgeImpact;

        public AttributionResult(String nodeName, String errorType, double confidence,
                                 List<String> evidence, String recommendedAction,
                                 boolean knowledgeImpact) {
            this.nodeName = nodeName;
            this.errorType = errorType;
            this.confidence = confidence;
            this.evidence = evidence;
            this.recommendedAction = recommendedAction;
            this.knowledgeImpact = knowledgeImpact;
        }

        public String getNodeName() {
            return nodeName;
        }

        public String getErrorType() {
            return errorType;
        }

        public double getConfidence() {
            return confidence;
        }

        public List<String> getEvidence() {
            return evidence;
        }

        public String getRecommendedAction() {
            return recommendedAction;
        }

        public boolean getKnowledgeImpact() {
            return knowledgeImpact;
        }
    }

    /**
     * 规则归因入口。
     *
     * @param eventType    事件类型 ATTEMPT/JUDGE/SIMULATION
     * @param judgeMessage 判题消息（ATTEMPT 为 null）
     * @param nodeName     知识节点业务键
     */
    public static AttributionResult attribute(String eventType, String judgeMessage,
                                              String nodeName) {
        String errorType = classify(eventType, judgeMessage);
        // 阶段 5 §5.3：编译错误与系统错误一样是诊断，不计学生知识失误、不降低掌握度
        boolean knowledgeImpact = !ERROR_SYSTEM.equals(errorType) && !ERROR_COMPILE.equals(errorType);
        List<String> evidence = buildEvidence(eventType, judgeMessage, nodeName, errorType);
        return new AttributionResult(nodeName, errorType, confidenceOf(errorType),
                evidence, recommendedAction(errorType, nodeName), knowledgeImpact);
    }

    private static String classify(String eventType, String judgeMessage) {
        if ("ATTEMPT".equals(eventType)) {
            return ERROR_WRONG_ANSWER;
        }
        String msg = judgeMessage == null ? "" : judgeMessage;
        String lower = msg.toLowerCase();
        if (lower.contains("system") || lower.contains("系统") || lower.contains("sandbox")) {
            return ERROR_SYSTEM;
        }
        if (lower.contains("compile") || lower.contains("编译")) {
            return ERROR_COMPILE;
        }
        if (lower.contains("runtime") || lower.contains("运行时")) {
            return ERROR_RUNTIME;
        }
        if (lower.contains("time") || lower.contains("超时") || lower.contains("tle")) {
            return ERROR_TIME_LIMIT;
        }
        return ERROR_WRONG_ANSWER;
    }

    private static List<String> buildEvidence(String eventType, String judgeMessage,
                                              String nodeName, String errorType) {
        List<String> evidence = new ArrayList<>();
        String source = "ATTEMPT".equals(eventType) ? "普通题作答"
                : "SIMULATION".equals(eventType) ? "模拟器预测" : "编程实验判题";
        evidence.add(source + "映射到知识节点「" + nodeName + "」");
        if (judgeMessage != null && !judgeMessage.isBlank()) {
            evidence.add("本次结果消息：" + judgeMessage);
        }
        evidence.add("归因类型：" + errorType);
        return evidence;
    }

    private static double confidenceOf(String errorType) {
        switch (errorType) {
            case ERROR_WRONG_ANSWER:
            case ERROR_TIME_LIMIT:
            case ERROR_COMPILE:
                return 0.9;
            case ERROR_RUNTIME:
                return 0.8;
            case ERROR_SYSTEM:
                return 0.3;
            default:
                return 0.5;
        }
    }

    private static String recommendedAction(String errorType, String nodeName) {
        switch (errorType) {
            case ERROR_COMPILE:
                return "先检查代码能否编译通过，再重新提交「" + nodeName + "」关联实验";
            case ERROR_RUNTIME:
                return "检查空指针与数组越界等运行时问题，再重做「" + nodeName + "」关联实验";
            case ERROR_TIME_LIMIT:
                return "优化算法复杂度后重做「" + nodeName + "」关联实验";
            case ERROR_SYSTEM:
                return "系统故障与你的解答无关，请稍后重新提交";
            default:
                return "先完成「" + nodeName + "」的基础题练习，再进入实验验证";
        }
    }
}
