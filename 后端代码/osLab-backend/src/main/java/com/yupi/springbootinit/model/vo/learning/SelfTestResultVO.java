package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 整组自测交卷后的最终结果：逐题复盘 + 有效统计 + 无效/待自评项。
 *
 * <p>统计只在交卷时由服务端从草稿重算一次并冻结在 run 上；
 * 读取结果不产生新的 attempt/学习事件。
 */
@Data
public class SelfTestResultVO implements Serializable {

    public static final String STATUS_CORRECT = "CORRECT";
    public static final String STATUS_WRONG = "WRONG";
    public static final String STATUS_UNANSWERED = "UNANSWERED";
    public static final String STATUS_PENDING_SELF_ASSESSMENT = "PENDING_SELF_ASSESSMENT";
    public static final String STATUS_INVALID_OFFLINE = "INVALID_OFFLINE";
    public static final String STATUS_INVALID_VERSION = "INVALID_VERSION";

    private String runId;
    private String mode = "SELF_TEST";
    private String status;
    private String finishedAt;

    /** 队列总题数（含未答、无效、待自评）。 */
    private Integer totalQuestions;
    /** 有效题数 = 已发布且版本未变，作为统计与正确率分母。 */
    private Integer validQuestions;
    /** 有效题中已作答题数。 */
    private Integer answeredQuestions;
    /** 有效客观题中已作答题数（正确率分母）。 */
    private Integer objectiveQuestions;
    private Integer correctCount;
    private Integer wrongCount;
    /** correctCount / objectiveQuestions；无客观作答时为 null。 */
    private Double accuracy;
    private Integer unansweredCount;
    private Integer pendingSelfAssessmentCount;
    private Integer invalidCount;
    private Integer markedUnsureCount;

    private List<QuestionResult> questions = new ArrayList<>();
    /** 错题与不确定题入口（字符串 id，禁止前端 Number 处理）。 */
    private List<String> wrongQuestionIds = new ArrayList<>();
    private List<String> unsureQuestionIds = new ArrayList<>();

    private String summaryText;

    @Data
    public static class QuestionResult implements Serializable {
        private String questionId;
        private String title;
        private String type;
        private String status;
        private Boolean markedUnsure;
        private Object submittedAnswer;
        private Boolean correct;
        private String analysis;
        private String misconception;
        private String invalidReason;
        private List<MasteryChangeVO> masteryChanges = new ArrayList<>();
        private String attemptId;
        private List<String> eventIds = new ArrayList<>();
    }
}
