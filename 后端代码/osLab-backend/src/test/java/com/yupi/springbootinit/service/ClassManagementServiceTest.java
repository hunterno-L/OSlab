package com.yupi.springbootinit.service;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassManagementServiceTest {
    private final RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
    private final ClassManagementService service = new ClassManagementService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
    }

    @Test
    void studentJoinsWithInvitationCodeAndDuplicateMembershipIsIdempotent() {
        User student = user(21L, "user");
        jdbc.classId = 41L;
        jdbc.joinedClass = Map.of(
                "classId", 41L,
                "className", "操作系统 1 班",
                "classCode", "OS-AB123456",
                "teacherName", "教师"
        );

        Map<String, Object> result = service.join(student, Map.of("classCode", " OS-AB123456 "));

        assertEquals(41L, result.get("classId"));
        assertEquals("INSERT IGNORE INTO teaching_class_member(classId,userId) VALUES(?,?)", jdbc.updateSql);
        assertEquals(List.of(41L, 21L), List.of(jdbc.updateArgs));
    }

    @Test
    void nonStudentCannotJoinAndDoesNotQueryTheDatabase() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.join(user(7L, "admin"), Map.of("classCode", "OS-AB123456"))
        );

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), error.getCode());
        assertEquals(0, jdbc.queryCount);
    }

    private User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        private int queryCount;
        private long classId;
        private String updateSql;
        private Object[] updateArgs = new Object[0];
        private Map<String, Object> joinedClass;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCount++;
            if (sql.contains("SELECT id FROM teaching_class WHERE classCode=?")) {
                return List.of(Collections.singletonMap("id", classId));
            }
            return new ArrayList<>();
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            queryCount++;
            return joinedClass;
        }

        @Override
        public int update(String sql, Object... args) {
            updateSql = sql;
            updateArgs = args;
            return 1;
        }
    }
}
