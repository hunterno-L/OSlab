package com.yupi.springbootinit.ai.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.ai.diagnosis.DiagnosisService;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 导师工具（计划 §5.3）。
 *
 * <p>工具列表与会话权限由后端决定；课程资料被当作待学习内容，
 * 不作为改变系统行为的指令（提示注入不构成越权）。
 *
 * <p>模型只能在这五个工具里选择，且每轮最多执行 3 次；
 * 工具结果决定建议，模型不写分数、不写掌握度。
 */
public interface TutorTool {

    String name();

    String description();

    /** JSON Schema（对象），用于原生工具协议或结构化协议的参数校验。 */
    Map<String, Object> parameterSchema();

    Outcome execute(Context context, JsonNode arguments);

    /** 一次工具执行的结果：状态 + 可展示摘要 + 供模型消费的文本。 */
    @Data
    class Outcome implements Serializable {
        private String status;
        private String summary;
        private String payload;

        public static Outcome ok(String summary, String payload) {
            Outcome outcome = new Outcome();
            outcome.setStatus("OK");
            outcome.setSummary(summary);
            outcome.setPayload(payload);
            return outcome;
        }

        public static Outcome empty(String summary) {
            Outcome outcome = new Outcome();
            outcome.setStatus("EMPTY");
            outcome.setSummary(summary);
            outcome.setPayload(summary);
            return outcome;
        }

        public static Outcome failed(String summary) {
            Outcome outcome = new Outcome();
            outcome.setStatus("FAILED");
            outcome.setSummary(summary);
            outcome.setPayload(summary);
            return outcome;
        }

        public static Outcome skipped(String summary) {
            Outcome outcome = new Outcome();
            outcome.setStatus("SKIPPED");
            outcome.setSummary(summary);
            outcome.setPayload(summary);
            return outcome;
        }
    }

    /** 工具执行的共享上下文；工具把产出写回这里，供后续工具与回答使用。 */
    @Data
    class Context implements Serializable {
        private User user;
        private AiLearningSession session;
        private AiCourseContext courseContext;
        private String message;

        /** 最近成功轮次（后端受限读取；只用于提示，不信任客户端历史）。 */
        private java.util.List<TutorOrchestrator.ConversationTurn> history = new ArrayList<>();
        /** 本轮场景与意图（GLOBAL/UNIT/NODE/QUESTION/EXPERIMENT；ASK/EXPLAIN/HINT/DEBUG/PLAN）。 */
        private String scene;
        private String intent;
        /** 学生明确附带的材料（已校验长度）。 */
        private java.util.List<TutorOrchestrator.Attachment> attachments = new ArrayList<>();

        /** 工具产出（累积）。 */
        private List<SourceRef> sources = new ArrayList<>();
        private EvidenceReadService.EvidenceSummary evidence;
        private DiagnosisService.Result diagnosisResult;
        private List<NextAction> nextActions = new ArrayList<>();
        private List<String> selectedQuestionIds = new ArrayList<>();
        private List<String> selectedPredictCases = new ArrayList<>();
        private String pageTraceSummary;

        /** 检索是否真的用上了语义（决定整轮 mode）。 */
        private boolean semanticUsed;
        private String semanticSkipReason;
        private List<String> retrievalTrace = new ArrayList<>();

        public String courseCode() {
            return session == null ? null : session.getCourseCode();
        }
    }
}
