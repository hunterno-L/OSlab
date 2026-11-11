package com.yupi.springbootinit.ai.session;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiLearningSessionMapper;
import com.yupi.springbootinit.mapper.AiTutorTurnMapper;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.AiTutorTurn;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiHistoryVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 课程会话列表与轮次历史分页（01 契约 §3.1）：
 * 账号/课程隔离、真实持久轮次、分页游标、关闭会话只读、上下文摘要不泄露隐私。
 */
class AiHistoryContractTest {

    private final AiLearningSessionMapper sessionMapper = mock(AiLearningSessionMapper.class);
    private final AiTutorTurnMapper turnMapper = mock(AiTutorTurnMapper.class);
    private final AiSessionService sessionService = mock(AiSessionService.class);
    private final AiTurnService turnService = mock(AiTurnService.class);
    private final AiHistoryService history = new AiHistoryService();

    private final User owner = new User();

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "ai-history-test");
        TableInfoHelper.initTableInfo(assistant, AiLearningSession.class);
        TableInfoHelper.initTableInfo(assistant, AiTutorTurn.class);

        owner.setId(7L);
        ReflectionTestUtils.setField(history, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(history, "turnMapper", turnMapper);
        ReflectionTestUtils.setField(history, "sessionService", sessionService);
        ReflectionTestUtils.setField(history, "turnService", turnService);
        when(turnMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void sessionListRequiresCourseScope() {
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> history.listSessions(owner, " ", null, 10)).getCode());
    }

    @Test
    void sessionListIsScopedAndPaginatesWithOpaqueCursor() {
        List<AiLearningSession> rows = new ArrayList<>();
        rows.add(session(31L, "搞懂页面置换", AiSessionService.STATUS_ACTIVE, new Date(1000L)));
        rows.add(session(32L, "", AiSessionService.STATUS_CLOSED, new Date(900L)));
        when(sessionMapper.selectList(any())).thenReturn(rows);
        AiTutorTurn firstMessage = new AiTutorTurn();
        firstMessage.setId(1L);
        firstMessage.setMessage("为什么 LRU 换出的是 2？");
        when(turnMapper.selectList(any())).thenReturn(List.of(firstMessage));

        AiHistoryVO.SessionPage page = history.listSessions(owner,
                "course.operating-systems", null, 1);

        assertEquals(1, page.getItems().size());
        assertEquals("31", page.getItems().get(0).getSessionId());
        assertEquals("搞懂页面置换", page.getItems().get(0).getTitle());
        assertNotNull(page.getNextCursor());
        assertFalse(page.getNextCursor().contains("course"));
    }

    @Test
    void closedSessionsAreReadableByOwnerOnly() {
        AiLearningSession session = session(31L, "旧话题", AiSessionService.STATUS_CLOSED, new Date(1000L));
        when(sessionService.requireOwned(owner, 31L)).thenReturn(session);
        AiTutorTurn turn = turn(900L, 31L, "为什么？", "因为最近最少使用。");
        when(turnMapper.selectList(any())).thenReturn(List.of(turn));
        when(turnService.toVo(turn)).thenReturn(new com.yupi.springbootinit.model.vo.ai.AiTurnVO());

        AiHistoryVO.TurnPage page = history.listTurns(owner, "31", null, 20);

        assertEquals(1, page.getItems().size());
        assertEquals("为什么？", page.getItems().get(0).getUserMessage());
        assertNull(page.getNextCursor());
        assertNotNull(page.getItems().get(0).getTurn());
    }

    @Test
    void otherUsersSessionIsRejectedByOwnershipCheck() {
        when(sessionService.requireOwned(owner, 31L))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该学习会话"));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> history.listTurns(owner, "31", null, 20)).getCode());
    }

    @Test
    void turnPageReturnsChronologicalOrderAndNextCursor() {
        AiLearningSession session = session(31L, "追问", AiSessionService.STATUS_ACTIVE, new Date(1000L));
        when(sessionService.requireOwned(owner, 31L)).thenReturn(session);
        // 服务层按 id 倒序取 limit+1：901、900、899
        List<AiTutorTurn> rows = Arrays.asList(
                turn(901L, 31L, "第三问", "第三答"),
                turn(900L, 31L, "第二问", "第二答"),
                turn(899L, 31L, "第一问", "第一答"));
        when(turnMapper.selectList(any())).thenReturn(rows);
        when(turnService.toVo(any())).thenReturn(new com.yupi.springbootinit.model.vo.ai.AiTurnVO());

        AiHistoryVO.TurnPage page = history.listTurns(owner, "31", "902", 2);

        assertEquals(2, page.getItems().size());
        // 返回时改为时间正序
        assertEquals("900", page.getItems().get(0).getTurnId());
        assertEquals("901", page.getItems().get(1).getTurnId());
        assertEquals("900", page.getNextCursor());
    }

    @Test
    void invalidCursorOrTurnIdIsRejected() {
        AiLearningSession session = session(31L, "追问", AiSessionService.STATUS_ACTIVE, new Date(1000L));
        when(sessionService.requireOwned(owner, 31L)).thenReturn(session);
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> history.listTurns(owner, "31", "not-a-number", 20)).getCode());
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> history.listSessions(owner, "course.operating-systems", "%%%", 20)).getCode());
    }

    @Test
    void contextSummaryOnlyContainsLocatorsAndAttachmentCount() {
        String json = "{\"courseCode\":\"course.os\",\"unitCode\":\"unit.1\","
                + "\"nodeCodes\":[\"n.1\",\"n.2\",\"n.3\",\"n.4\"],"
                + "\"resourceRef\":\"question:55\","
                + "\"tutor\":{\"attachments\":[{\"text\":\"secret code\"},{\"text\":\"x\"}]}}";
        String summary = AiHistoryService.contextSummary(json);
        assertTrue(summary.contains("单元 unit.1"));
        assertTrue(summary.contains("知识点 n.1、n.2、n.3"));
        assertTrue(summary.contains("资源 question:55"));
        assertTrue(summary.contains("附带材料 2 项"));
        assertFalse(summary.contains("secret code"), "摘要不得包含附带材料正文");
        assertFalse(summary.contains("n.4"), "摘要最多展示 3 个知识点");
    }

    // region fixtures

    private AiLearningSession session(Long id, String goal, String status, Date updatedAt) {
        AiLearningSession session = new AiLearningSession();
        session.setId(id);
        session.setUserId(7L);
        session.setCourseCode("course.operating-systems");
        session.setGoal(goal);
        session.setStatus(status);
        session.setFocus(AiSessionService.FOCUS_LEARN);
        session.setRowVersion(0);
        session.setIsDelete(0);
        session.setCreateTime(updatedAt);
        session.setUpdateTime(updatedAt);
        return session;
    }

    private AiTutorTurn turn(Long id, Long sessionId, String message, String answer) {
        AiTutorTurn turn = new AiTutorTurn();
        turn.setId(id);
        turn.setUserId(7L);
        turn.setSessionId(sessionId);
        turn.setClientRequestId("req-" + id);
        turn.setStatus(AiTutorTurn.STATUS_SUCCEEDED);
        turn.setMode(AiTutorTurn.MODE_AI);
        turn.setMessage(message);
        turn.setAnswer(answer);
        turn.setCreateTime(new Date(1000L + id));
        turn.setIsDelete(0);
        return turn;
    }

    // endregion
}
