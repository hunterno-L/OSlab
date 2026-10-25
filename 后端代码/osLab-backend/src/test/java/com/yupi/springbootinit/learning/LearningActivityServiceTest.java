package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.course.CourseRepository;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningActivityRecordMapper;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.model.dto.learning.LearningSimulationRunRequest;
import com.yupi.springbootinit.model.entity.LearningActivityRecord;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningActivityPageVO;
import com.yupi.springbootinit.model.vo.learning.LearningActivityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动记录契约：按 eventGroupKey 归并、保留全部节点证据、任务/单元/普通演示接入、
 * 用户隔离与“纯演示不写掌握事件”。
 */
@ExtendWith(MockitoExtension.class)
class LearningActivityServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private LearningEventMapper learningEventMapper;

    @Mock
    private LearningActivityRecordMapper activityRecordMapper;

    private LearningActivityService service;

    private boolean taskRowsEnabled;
    private boolean progressRowsEnabled;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        taskRowsEnabled = false;
        progressRowsEnabled = false;
        service = new LearningActivityService();
        ReflectionTestUtils.setField(service, "learningEventMapper", learningEventMapper);
        ReflectionTestUtils.setField(service, "activityRecordMapper", activityRecordMapper);
        ReflectionTestUtils.setField(service, "db", new CourseRepository() {
            @Override
            public List<Map<String, Object>> rows(String sql, Object... args) {
                return testRows(sql);
            }

            @Override
            public Map<String, Object> one(String sql, Object... args) {
                Map<String, Object> row = new HashMap<>();
                row.put("publishedJson",
                        "{\"activities\":[{\"activityCode\":\"activity.lru\",\"title\":\"LRU 页面置换演示\"}]}");
                return row;
            }
        });
    }

    @Test
    void groupsEventsByGroupKeyAndKeepsAllNodeEvidence() {
        LearningEvent first = event(101L, "ATTEMPT", "PASS", 1, 0.2, 0.4,
                "attempt:900", "普通题作答", "LRU 访问序列判断");
        first.setNodeCode("os.memory.lru");
        first.setNodeName("LRU 页面置换算法");
        first.setSourceUnitCode("unit.os.memory");
        first.setSourceActivityCode("activity.lru");
        first.setCreateTime(new Date(1000));
        LearningEvent second = event(102L, "ATTEMPT", "PASS", 1, 0.2, 0.4,
                "attempt:900", "普通题作答", "LRU 访问序列判断");
        second.setNodeCode("os.memory.fifo");
        second.setNodeName("FIFO 页面置换算法");
        second.setSourceUnitCode("unit.os.memory");
        second.setSourceActivityCode("activity.lru");
        second.setCreateTime(new Date(2000));
        when(learningEventMapper.selectList(any())).thenReturn(Arrays.asList(second, first));

        LearningActivityPageVO page = service.activities(
                user, null, null, null, null, null, null, 1, 10);

        assertEquals(1L, page.getTotal(), "同一 eventGroupKey 只能产生一条主活动");
        LearningActivityVO activity = page.getRecords().get(0);
        assertEquals("ATTEMPT", activity.getActivityType());
        assertEquals("LRU 访问序列判断", activity.getTitle());
        assertEquals("course.os", activity.getCourseCode());
        assertEquals("unit.os.memory", activity.getUnitCode());
        assertEquals(2, activity.getNodeRefs().size(), "多节点证据必须全部保留");
        assertEquals(2, activity.getEvidenceRefs().size());
        assertEquals(Long.valueOf(102L), activity.getReplayEventId());
        assertTrue(activity.getNodeRefs().stream().allMatch(ref -> Boolean.TRUE.equals(ref.getIndependent())));
        assertEquals("/assessments?questionId=9000", activity.getReturnRoute());
    }

    @Test
    void nodeFilterKeepsWholeEventGroupButExcludesNonMatchingGroups() {
        LearningEvent a = event(201L, "ATTEMPT", "FAIL", 1, 0.3, 0.2,
                "attempt:901", "普通题作答", "进程调度判断题");
        a.setNodeCode("os.cpu.fcfs");
        a.setNodeName("FCFS");
        a.setCreateTime(new Date(1000));
        LearningEvent b = event(202L, "ATTEMPT", "FAIL", 1, 0.1, 0.05,
                "attempt:901", "普通题作答", "进程调度判断题");
        b.setNodeCode("os.cpu.sjf");
        b.setNodeName("SJF");
        b.setCreateTime(new Date(1100));
        LearningEvent unrelated = event(203L, "ATTEMPT", "PASS", 1, 0.4, 0.6,
                "attempt:902", "普通题作答", "内存分配判断题");
        unrelated.setNodeCode("os.memory.first_fit");
        unrelated.setNodeName("首次适应");
        unrelated.setCreateTime(new Date(1200));
        when(learningEventMapper.selectList(any()))
                .thenReturn(Arrays.asList(unrelated, b, a));

        LearningActivityPageVO page = service.activities(
                user, null, null, null, "os.cpu.sjf", null, null, 1, 10);
        assertEquals(1L, page.getTotal());
        assertEquals(2, page.getRecords().get(0).getNodeRefs().size(),
                "命中节点的活动必须展开同组全部节点证据");

        LearningActivityPageVO empty = service.activities(
                user, null, null, null, "os.not.exists", null, null, 1, 10);
        assertEquals(0L, empty.getTotal());
    }

    @Test
    void includesTaskSubmissionUnitCompletionAndSimulationRun() {
        taskRowsEnabled = true;
        progressRowsEnabled = true;
        when(learningEventMapper.selectList(any())).thenReturn(new ArrayList<>());
        LearningActivityRecord run = new LearningActivityRecord();
        run.setId(301L);
        run.setUserId(7L);
        run.setActivityType("SIMULATION_RUN");
        run.setTitle("CPU 先来先服务（FCFS）");
        run.setAlgorithmKey("cpu.fcfs");
        run.setCourseCode("course.os");
        run.setUnitCode("unit.os.cpu");
        run.setInputJson("{\"processes\":[{\"name\":\"P1\",\"arrival\":0,\"burst\":2}]}");
        run.setResultJson("{\"stepCount\":3,\"metrics\":[]}");
        run.setOccurredAt(new Date(4000));
        when(activityRecordMapper.selectList(any())).thenReturn(Collections.singletonList(run));

        LearningActivityPageVO page = service.activities(
                user, null, null, null, null, null, null, 1, 20);
        Map<String, LearningActivityVO> byType = new HashMap<>();
        page.getRecords().forEach(item -> byType.put(item.getActivityType(), item));
        assertEquals(4L, page.getTotal(), "任务接受+提交、单元完成、普通演示运行各一条");
        assertNotNull(byType.get("TASK_ACCEPTED"));
        assertNotNull(byType.get("TASK_SUBMISSION"));
        assertNotNull(byType.get("UNIT_COMPLETED"));
        LearningActivityVO runVo = byType.get("SIMULATION_RUN");
        assertNotNull(runVo);
        assertEquals("cpu.fcfs", runVo.getAlgorithmKey());
        assertNotNull(runVo.getInputSnapshot());
        assertEquals("/experiments/simulation?algorithmKey=cpu.fcfs", runVo.getReturnRoute());
        assertNotNull(byType.get("TASK_SUBMISSION").getReturnRoute());
        assertTrue(byType.get("TASK_SUBMISSION").getReturnRoute().startsWith("/tasks/"));
        assertTrue(byType.get("UNIT_COMPLETED").getReturnRoute().contains("activity="));
    }

    @Test
    void recordsSimulationRunWithoutTouchingMasteryEvents() {
        LearningSimulationRunRequest request = new LearningSimulationRunRequest();
        request.setAlgorithmKey("cpu.fcfs");
        request.setTitle("CPU 先来先服务（FCFS）");
        request.setCourseCode("course.os");
        request.setUnitCode("unit.os.cpu");
        request.setInput(MAPPER.createObjectNode().put("quantum", 2));
        request.setResultSummary(MAPPER.createObjectNode().put("stepCount", 3));
        when(activityRecordMapper.insert(any())).thenAnswer(invocation -> {
            LearningActivityRecord record = invocation.getArgument(0);
            record.setId(999L);
            return 1;
        });

        Long id = service.recordSimulationRun(user, request);

        ArgumentCaptor<LearningActivityRecord> captor = ArgumentCaptor.forClass(LearningActivityRecord.class);
        verify(activityRecordMapper).insert(captor.capture());
        assertEquals("SIMULATION_RUN", captor.getValue().getActivityType());
        assertEquals("cpu.fcfs", captor.getValue().getAlgorithmKey());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(Long.valueOf(999L), id);
        verify(learningEventMapper, never()).insert(any());
    }

    @Test
    void missingRunTableDoesNotBreakOtherActivitySources() {
        taskRowsEnabled = true;
        progressRowsEnabled = true;
        when(learningEventMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(activityRecordMapper.selectList(any()))
                .thenThrow(new RuntimeException("Table 'learning_activity_record' doesn't exist"));

        LearningActivityPageVO page = service.activities(
                user, null, null, null, null, null, null, 1, 20);

        assertEquals(3L, page.getTotal(), "运行记录表不可用时，任务接受/提交与单元活动仍需返回");
        assertTrue(page.getRecords().stream()
                .noneMatch(item -> "SIMULATION_RUN".equals(item.getActivityType())));
    }

    @Test
    void schemaInitializerCreatesTableAndNeverBlocksStartup() {
        List<String> ddl = new ArrayList<>();
        LearningActivityService initialized = new LearningActivityService();
        ReflectionTestUtils.setField(initialized, "db", new CourseRepository() {
            @Override
            public int update(String sql, Object... args) {
                ddl.add(sql);
                return 1;
            }
        });
        initialized.ensureActivityRecordTable();
        assertEquals(1, ddl.size());
        assertTrue(ddl.get(0).contains("CREATE TABLE IF NOT EXISTS learning_activity_record"));

        LearningActivityService failing = new LearningActivityService();
        ReflectionTestUtils.setField(failing, "db", new CourseRepository() {
            @Override
            public int update(String sql, Object... args) {
                throw new IllegalStateException("ddl denied");
            }
        });
        failing.ensureActivityRecordTable();
    }

    @Test
    void rejectsOversizedSimulationSnapshot() {
        LearningSimulationRunRequest request = new LearningSimulationRunRequest();
        request.setAlgorithmKey("cpu.fcfs");
        request.setInput(MAPPER.createObjectNode().put("big", "x".repeat(20 * 1024)));
        assertThrows(BusinessException.class, () -> service.recordSimulationRun(user, request));
        verify(activityRecordMapper, never()).insert(any());
    }

    private LearningEvent event(long id, String eventType, String result, int isFirst,
                                double before, double after, String groupKey,
                                String source, String title) {
        LearningEvent event = new LearningEvent();
        event.setId(id);
        event.setUserId(7L);
        event.setEventType(eventType);
        event.setResult(result);
        event.setIsFirst(isFirst);
        event.setMasteryBefore(before);
        event.setMasteryAfter(after);
        event.setEventGroupKey(groupKey);
        event.setResourceType("LEARNING_QUESTION");
        event.setResourceId(9000L);
        event.setEvidence("{\"source\":\"" + source + "\",\"title\":\"" + title
                + "\",\"resultText\":\"答对\",\"note\":\"首次作答\"}");
        event.setSourceUnitCode("unit.os.memory");
        return event;
    }

    private List<Map<String, Object>> testRows(String sql) {
        if (sql.contains("SELECT unitCode,courseCode FROM learning_unit")) {
            Map<String, Object> unit = new HashMap<>();
            unit.put("unitCode", "unit.os.memory");
            unit.put("courseCode", "course.os");
            return Collections.singletonList(unit);
        }
        if (sql.contains("SELECT taskCode,courseCode,unitCode FROM learning_task")) {
            return new ArrayList<>();
        }
        if (sql.contains("FROM learning_task_recipient") && taskRowsEnabled) {
            Map<String, Object> row = new HashMap<>();
            row.put("recipientId", 11L);
            row.put("recipientStatus", "REVIEWED");
            row.put("acceptedAt", new Date(1000));
            row.put("taskCode", "task.cpu.1");
            row.put("taskTitle", "进程调度任务");
            row.put("courseCode", "course.os");
            row.put("unitCode", "unit.os.memory");
            row.put("submissionId", 12L);
            row.put("roundNo", 1);
            row.put("submissionStatus", "REVIEWED");
            row.put("isLate", 0);
            row.put("totalScore", 92.5);
            row.put("submittedAt", new Date(3000));
            row.put("reviewedAt", new Date(3500));
            return Collections.singletonList(row);
        }
        if (sql.contains("FROM learning_activity_progress") && progressRowsEnabled) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 21L);
            row.put("unitCode", "unit.os.memory");
            row.put("activityCode", "activity.lru");
            row.put("completed", 1);
            row.put("courseCode", "course.os");
            row.put("unitTitle", "页面置换");
            row.put("updateTime", new Date(2000));
            return Collections.singletonList(row);
        }
        return new ArrayList<>();
    }
}
