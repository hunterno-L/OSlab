package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 课程会话列表与轮次历史分页 VO（01 契约 §3.1）。
 *
 * <p>仅返回学生可读的对话数据：不含系统提示、密钥、模型内部载荷或他人记录。
 */
public final class AiHistoryVO {

    private AiHistoryVO() {
    }

    @Data
    public static class SessionItem implements Serializable {
        private String sessionId;
        private String courseCode;
        private String title;
        private String status;
        private String updatedAt;
        private String focus;
    }

    @Data
    public static class SessionPage implements Serializable {
        private List<SessionItem> items = new ArrayList<>();
        /** 不透明游标；null 表示没有更早的会话。 */
        private String nextCursor;
    }

    @Data
    public static class TurnItem implements Serializable {
        private String turnId;
        private String clientRequestId;
        private String userMessage;
        private String createdAt;
        /** 发送时上下文的短摘要（单元/知识点/资源/附带材料数量），不含隐私内容。 */
        private String contextSummary;
        private AiTurnVO turn;
    }

    @Data
    public static class TurnPage implements Serializable {
        private List<TurnItem> items = new ArrayList<>();
        /** 更早一页的游标（最早一条 turnId）；null 表示没有更早记录。 */
        private String nextCursor;
    }
}
