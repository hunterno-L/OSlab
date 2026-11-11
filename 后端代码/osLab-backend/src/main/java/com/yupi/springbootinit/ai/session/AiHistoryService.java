package com.yupi.springbootinit.ai.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiLearningSessionMapper;
import com.yupi.springbootinit.mapper.AiTutorTurnMapper;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.AiTutorTurn;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiHistoryVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 课程会话与轮次历史读取（01 契约 §3.1）。
 *
 * <p>只读取当前用户自己的数据；按课程限定会话列表；轮次按 turnId 倒序分页、
 * 返回时按时间正序。分页游标不透明（Base64），不暴露内部排序细节。
 * 读取行为不创建轮次、不调用模型、不改变任何学习状态。
 */
@Slf4j
@Service
public class AiHistoryService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;
    private static final int TITLE_MAX_CHARS = 60;

    @Resource
    private AiLearningSessionMapper sessionMapper;

    @Resource
    private AiTutorTurnMapper turnMapper;

    @Resource
    private AiSessionService sessionService;

    @Resource
    private AiTurnService turnService;

    public AiHistoryVO.SessionPage listSessions(User user, String courseCode, String cursor, Integer limit) {
        if (StringUtils.isBlank(courseCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "courseCode 不能为空");
        }
        int size = normalizeLimit(limit);
        LambdaQueryWrapper<AiLearningSession> wrapper = new LambdaQueryWrapper<AiLearningSession>()
                .eq(AiLearningSession::getUserId, user.getId())
                .eq(AiLearningSession::getCourseCode, courseCode.trim())
                .eq(AiLearningSession::getIsDelete, 0)
                .orderByDesc(AiLearningSession::getUpdateTime)
                .orderByDesc(AiLearningSession::getId)
                .last("limit " + (size + 1));
        SessionCursor decoded = decodeSessionCursor(cursor);
        if (decoded != null) {
            wrapper.and(w -> w.lt(AiLearningSession::getUpdateTime, decoded.date)
                    .or(nested -> nested.eq(AiLearningSession::getUpdateTime, decoded.date)
                            .lt(AiLearningSession::getId, decoded.id)));
        }
        List<AiLearningSession> rows = sessionMapper.selectList(wrapper);
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        AiHistoryVO.SessionPage page = new AiHistoryVO.SessionPage();
        for (AiLearningSession session : rows) {
            AiHistoryVO.SessionItem item = new AiHistoryVO.SessionItem();
            item.setSessionId(AiJson.id(session.getId()));
            item.setCourseCode(session.getCourseCode());
            item.setTitle(sessionTitle(session));
            item.setStatus(session.getStatus());
            item.setUpdatedAt(AiJson.iso(session.getUpdateTime() == null
                    ? session.getCreateTime() : session.getUpdateTime()));
            item.setFocus(session.getFocus());
            page.getItems().add(item);
        }
        if (hasMore && !rows.isEmpty()) {
            AiLearningSession last = rows.get(rows.size() - 1);
            page.setNextCursor(encodeSessionCursor(last.getUpdateTime() == null
                    ? last.getCreateTime() : last.getUpdateTime(), last.getId()));
        }
        return page;
    }

    public AiHistoryVO.TurnPage listTurns(User user, String sessionId, String before, Integer limit) {
        AiLearningSession session = sessionService.requireOwned(user, parseId(sessionId));
        int size = normalizeLimit(limit);
        Long beforeId = null;
        if (StringUtils.isNotBlank(before)) {
            beforeId = parseId(before);
        }
        LambdaQueryWrapper<AiTutorTurn> wrapper = new LambdaQueryWrapper<AiTutorTurn>()
                .eq(AiTutorTurn::getUserId, user.getId())
                .eq(AiTutorTurn::getSessionId, session.getId())
                .eq(AiTutorTurn::getIsDelete, 0)
                .orderByDesc(AiTutorTurn::getId)
                .last("limit " + (size + 1));
        if (beforeId != null) {
            wrapper.lt(AiTutorTurn::getId, beforeId);
        }
        List<AiTutorTurn> rows = turnMapper.selectList(wrapper);
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        // 数据库按 id 倒序取"最近 N 条"，返回给前端时改为时间正序，方便直接追加渲染
        Collections.reverse(rows);
        AiHistoryVO.TurnPage page = new AiHistoryVO.TurnPage();
        for (AiTutorTurn turn : rows) {
            AiHistoryVO.TurnItem item = new AiHistoryVO.TurnItem();
            item.setTurnId(AiJson.id(turn.getId()));
            item.setClientRequestId(turn.getClientRequestId());
            item.setUserMessage(turn.getMessage());
            item.setCreatedAt(AiJson.iso(turn.getCreateTime()));
            item.setContextSummary(contextSummary(turn.getContextJson()));
            item.setTurn(turnService.toVo(turn));
            page.getItems().add(item);
        }
        if (hasMore && !rows.isEmpty()) {
            page.setNextCursor(AiJson.id(rows.get(0).getId()));
        }
        return page;
    }

    // region 内部

    private String sessionTitle(AiLearningSession session) {
        String goal = StringUtils.trimToEmpty(session.getGoal());
        if (StringUtils.isNotBlank(goal)) {
            return truncate(goal, TITLE_MAX_CHARS);
        }
        AiTutorTurn first = firstTurn(session.getUserId(), session.getId());
        String message = first == null ? "" : StringUtils.trimToEmpty(first.getMessage());
        return truncate(StringUtils.defaultIfBlank(message, "新的学习对话"), TITLE_MAX_CHARS);
    }

    /** 每个会话最多一次单行查询；列表页最多 20 条，不预取整个用户的历史。 */
    private AiTutorTurn firstTurn(Long userId, Long sessionId) {
        List<AiTutorTurn> rows = turnMapper.selectList(new LambdaQueryWrapper<AiTutorTurn>()
                .eq(AiTutorTurn::getUserId, userId)
                .eq(AiTutorTurn::getSessionId, sessionId)
                .eq(AiTutorTurn::getIsDelete, 0)
                .orderByAsc(AiTutorTurn::getId)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    static String contextSummary(String contextJson) {
        JsonNode node = AiJson.read(contextJson, JsonNode.class);
        if (node == null || !node.isObject()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        text(node, "unitCode").ifPresent(value -> parts.add("单元 " + value));
        JsonNode nodeCodes = node.path("nodeCodes");
        if (nodeCodes.isArray() && nodeCodes.size() > 0) {
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < nodeCodes.size() && i < 3; i++) {
                String code = nodeCodes.get(i).asText("");
                if (StringUtils.isNotBlank(code)) {
                    codes.add(code);
                }
            }
            if (!codes.isEmpty()) {
                parts.add("知识点 " + String.join("、", codes));
            }
        }
        text(node, "resourceRef").ifPresent(value -> parts.add("资源 " + value));
        JsonNode attachments = node.path("tutor").path("attachments");
        if (attachments.isArray() && attachments.size() > 0) {
            parts.add("附带材料 " + attachments.size() + " 项");
        }
        return truncate(String.join(" · ", parts), 200);
    }

    private static java.util.Optional<String> text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return StringUtils.isBlank(value)
                ? java.util.Optional.empty() : java.util.Optional.of(truncate(value, 80));
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static int normalizeLimit(Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        return Math.min(size, MAX_LIMIT);
    }

    private static Long parseId(String value) {
        try {
            Long parsed = AiJson.parseId(value);
            if (parsed == null) {
                throw new NumberFormatException("blank");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "id 非法");
        }
    }

    private static String encodeSessionCursor(Date updatedAt, Long id) {
        long millis = updatedAt == null ? 0L : updatedAt.getTime();
        String raw = millis + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static SessionCursor decodeSessionCursor(String cursor) {
        if (StringUtils.isBlank(cursor)) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int split = raw.indexOf(':');
            if (split <= 0) {
                throw new IllegalArgumentException("bad cursor");
            }
            long millis = Long.parseLong(raw.substring(0, split));
            long id = Long.parseLong(raw.substring(split + 1));
            return new SessionCursor(new Date(millis), id);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "cursor 非法");
        }
    }

    private static final class SessionCursor {
        private final Date date;
        private final Long id;

        private SessionCursor(Date date, Long id) {
            this.date = date;
            this.id = id;
        }
    }

    // endregion
}
