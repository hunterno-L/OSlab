package com.yupi.springbootinit.service;

import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.aop.AuthInterceptor;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.controller.KnowledgeGraphController;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.UpdateNodeChapterRequest;
import com.yupi.springbootinit.model.entity.EntityNode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.repository.KnowledgeGraphRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Values;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 节点章节写入边界测试：全部使用 mock，不连接或修改图数据库。 */
class KnowledgeGraphChapterTest {
    private final Driver driver = mock(Driver.class);
    private final Session session = mock(Session.class);
    private final Transaction transaction = mock(Transaction.class);
    private final Result result = mock(Result.class);
    private final Record record = mock(Record.class);
    private final KnowledgeGraphRepository repository = mock(KnowledgeGraphRepository.class);
    private final KnowledgeGraphService service = new KnowledgeGraphService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "driver", driver);
        ReflectionTestUtils.setField(service, "knowledgeGraphRepository", repository);
        when(driver.session()).thenReturn(session);
        when(session.writeTransaction(any())).thenAnswer(invocation -> {
            TransactionWork<?> work = invocation.getArgument(0);
            return work.execute(transaction);
        });
        when(transaction.run(anyString(), anyMap())).thenReturn(result);
        when(result.single()).thenReturn(record);
        when(record.get("matched")).thenReturn(Values.value(1));
    }

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void creationKeepsRealChapterSeparateFromCategory() {
        NodeDTO node = new NodeDTO();
        node.setName("时间片轮转算法");
        node.setCategory("算法");
        node.setChapter(" 进程管理 ");
        node.setDescription("按时间片分配 CPU");
        EntityNode entity = service.convertToEntity(node);
        assertEquals("进程管理", entity.getChapter());
        assertEquals("算法", entity.getLabel());
        assertEquals(node.getName(), entity.getName());
        assertEquals(node.getDescription(), entity.getDescription());
    }

    @Test
    void creationWithoutChapterRemainsHonestlyUnclassified() {
        NodeDTO node = new NodeDTO();
        node.setCategory("算法");
        assertEquals("未分类", service.convertToEntity(node).getChapter());
    }

    @Test
    void chapterUpdateBindsValuesAndChangesOnlyChapter() {
        service.updateNodeChapter(request(" 进程调度 ", " 进程管理 "));
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(transaction).run(query.capture(), parameters.capture());
        assertEquals(Map.of("nodeName", "进程调度", "chapter", "进程管理"), parameters.getValue());
        assertTrue(query.getValue().contains("SET n.chapter = $chapter"));
        assertFalse(query.getValue().contains("SET n.name"));
        assertFalse(query.getValue().contains("n.description ="));
        assertFalse(query.getValue().contains("n.label ="));
        assertFalse(query.getValue().contains("DELETE"));
        assertFalse(query.getValue().contains("MERGE"));
        verifyNoInteractions(repository);
        verify(session).close();
    }

    @Test
    void blankFieldsAreRejectedBeforeDatabaseAccess() {
        assertParamsError(null);
        assertParamsError(request(" ", "进程管理"));
        assertParamsError(request("进程调度", " "));
        verifyNoInteractions(driver, repository);
    }

    @Test
    void excessiveFieldLengthsAreRejectedBeforeDatabaseAccess() {
        assertParamsError(request("n".repeat(201), "进程管理"));
        assertParamsError(request("进程调度", "c".repeat(101)));
        verifyNoInteractions(driver, repository);
    }

    @Test
    void missingNodeReturnsNotFound() {
        when(record.get("matched")).thenReturn(Values.value(0));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateNodeChapter(request("不存在", "进程管理")));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), error.getCode());
    }

    @Test
    void duplicateNamesCannotUpdateMultipleNodes() {
        when(record.get("matched")).thenReturn(Values.value(2));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateNodeChapter(request("进程调度", "进程管理")));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), error.getCode());
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(transaction).run(query.capture(), anyMap());
        assertTrue(query.getValue().contains("CASE WHEN size(nodes) = 1 THEN nodes ELSE [] END"));
    }

    @Test
    void studentCannotCallChapterUpdate() throws Throwable {
        AuthInterceptor interceptor = interceptorForRole(UserConstant.DEFAULT_ROLE);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        BusinessException error = assertThrows(BusinessException.class,
                () -> interceptor.doInterceptor(point, chapterAuthorization()));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), error.getCode());
        verify(point, never()).proceed();
    }

    @Test
    void administratorCanReachChapterUpdateController() throws Throwable {
        AuthInterceptor interceptor = interceptorForRole(UserConstant.ADMIN_ROLE);
        KnowledgeGraphService controllerService = mock(KnowledgeGraphService.class);
        KnowledgeGraphController controller = new KnowledgeGraphController();
        ReflectionTestUtils.setField(controller, "knowledgeGraphService", controllerService);
        UpdateNodeChapterRequest request = request("进程调度", "进程管理");
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenAnswer(invocation -> controller.updateNodeChapter(request));
        Object response = interceptor.doInterceptor(point, chapterAuthorization());
        assertEquals(0, ((BaseResponse<?>) response).getCode());
        verify(controllerService).updateNodeChapter(request);
    }

    private AuthInterceptor interceptorForRole(String role) {
        User user = new User();
        user.setUserRole(role);
        UserService userService = mock(UserService.class);
        when(userService.getLoginUser(any())).thenReturn(user);
        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "userService", userService);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        return interceptor;
    }

    private AuthCheck chapterAuthorization() throws NoSuchMethodException {
        AuthCheck authorization = KnowledgeGraphController.class
                .getMethod("updateNodeChapter", UpdateNodeChapterRequest.class)
                .getAnnotation(AuthCheck.class);
        assertNotNull(authorization);
        assertEquals(UserConstant.ADMIN_ROLE, authorization.mustRole());
        return authorization;
    }

    private void assertParamsError(UpdateNodeChapterRequest request) {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateNodeChapter(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), error.getCode());
    }

    private UpdateNodeChapterRequest request(String nodeName, String chapter) {
        UpdateNodeChapterRequest request = new UpdateNodeChapterRequest();
        request.setNodeName(nodeName);
        request.setChapter(chapter);
        return request;
    }
}
