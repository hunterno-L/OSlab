package com.yupi.springbootinit.model.dto.ai;

import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 会话与轮次请求 DTO（任务书 §3 冻结样例）。
 */
public final class AiRequests {

    private AiRequests() {
    }

    /** {@code POST /learning/ai/sessions} */
    @Data
    public static class CreateSession implements Serializable {
        private String courseCode;
        private String goal;
        /** LEARN / REVIEW */
        private String focus;
        private Integer timeBudgetMinutes;
        /** 个人配置 id 字符串；缺省用平台默认。 */
        private String modelProfileId;
        private String unitCode;
    }

    /** {@code PATCH /learning/ai/sessions/{id}} —— 写操作必须带 rowVersion。 */
    @Data
    public static class UpdateSession implements Serializable {
        private String goal;
        private String focus;
        private Integer timeBudgetMinutes;
        private String modelProfileId;
        private String unitCode;
        /** 把某个动作设为当前任务位置（补学后返回原任务）。 */
        private String currentActionId;
        /** ACTIVE / CLOSED */
        private String status;
        private Integer rowVersion;
    }

    /** {@code POST /learning/ai/sessions/{id}/turns} */
    @Data
    public static class CreateTurn implements Serializable {
        /** 幂等键：相同请求返回同一轮次。 */
        private String clientRequestId;
        private String message;
        private AiCourseContext context;
        /** 统一导师场景/意图/作用域与用户明确附带的材料（本轮新增，旧客户端省略即按旧行为）。 */
        private TutorDirective tutor;
    }

    /**
     * 统一导师指令（01 契约 §2/§3.1）。
     *
     * <p>只描述本轮辅导方式与用户明确选择的材料；服务端会重新校验，
     * 不接受客户端传来的掌握度、正确性或工具结论。
     */
    @Data
    public static class TutorDirective implements Serializable {
        /** GLOBAL / UNIT / NODE / QUESTION / EXPERIMENT */
        private String scene;
        /** ASK / EXPLAIN / HINT / DEBUG / PLAN */
        private String intent;
        /** PAGE / COURSE */
        private String scope;
        private List<TutorAttachment> attachments;
    }

    /** 用户明确附带的一段材料（单选段/代码/报错）。 */
    @Data
    public static class TutorAttachment implements Serializable {
        /** SELECTION / CODE / ERROR */
        private String kind;
        private String label;
        private String text;
        private String sourceId;
    }

    /** {@code POST /learning/ai/actions/{id}/submit} */
    @Data
    public static class SubmitAction implements Serializable {
        private String submissionKey;
        /** 选择题选项键，例如 "A"。 */
        private String choiceKey;
        /** 预测提交，例如 "2"（换出页）或 "1,2,3"（内存快照）。 */
        private String predictedProcess;
        /** 解释类任务的文本作答。 */
        private String text;
        /** 自报外部帮助：NONE / EXTERNAL_AI / PEER / OTHER。 */
        private String selfReportedHelp;
    }

    /** {@code POST /learning/ai/materials} */
    @Data
    public static class CreateMaterial implements Serializable {
        private String courseCode;
        private String title;
        /** 粘贴纯文本。 */
        private String text;
        /** UTF-8 Markdown。 */
        private String contentMd;
        /** 逗号分隔的知识点编码，用于图谱约束召回。 */
        private String nodeCodes;
    }
}
