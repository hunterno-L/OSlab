package com.yupi.springbootinit.task;

import com.yupi.springbootinit.mapper.StudentsMapper;
import com.yupi.springbootinit.mapper.UserMapper;
import com.yupi.springbootinit.model.dto.task.TaskAudienceRequest;
import com.yupi.springbootinit.service.ClassManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskAudienceServiceTest {
    private final StudentsMapper studentsMapper = mock(StudentsMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final ClassManagementService classManagementService = mock(ClassManagementService.class);
    private final TaskAudienceService service = new TaskAudienceService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "studentsMapper", studentsMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "classManagementService", classManagementService);
    }

    @Test
    void managedClassMembersAreTaskRecipientsWithoutLegacyRosterRows() {
        when(studentsMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(classManagementService.audienceRows(eq(List.of("操作系统 1 班")), anyList()))
                .thenReturn(List.of(Map.of(
                        "className", "操作系统 1 班",
                        "userId", 9007199254740997L,
                        "userName", "学生甲",
                        "userRole", "user",
                        "isDelete", 0
                )));
        TaskAudienceRequest request = new TaskAudienceRequest();
        request.setClassNames(List.of("操作系统 1 班"));

        TaskAudienceService.Resolved result = service.resolve(request);

        assertEquals(1, result.getValidCount());
        assertEquals("9007199254740997", result.getStudents().get(0).get("userId"));
        assertEquals("学生甲", result.getStudents().get(0).get("name"));
        assertEquals("操作系统 1 班", result.getStudents().get(0).get("className"));
        verifyNoInteractions(userMapper);
    }
}
