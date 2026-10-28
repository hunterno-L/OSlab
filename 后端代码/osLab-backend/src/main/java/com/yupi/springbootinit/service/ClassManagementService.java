package com.yupi.springbootinit.service;

import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.entity.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 班级与成员管理。新班级表与旧 students 名册并行存在，便于平滑迁移。 */
@Service
public class ClassManagementService {
    @Resource
    private JdbcTemplate jdbc;

    public List<Map<String, Object>> listTeacherClasses(User teacher) {
        return jdbc.queryForList("SELECT c.id, c.className, c.classCode, c.description, c.creatorId, "
                + "c.createdAt, c.updatedAt, COUNT(m.id) AS studentCount FROM teaching_class c "
                + "LEFT JOIN teaching_class_member m ON m.classId = c.id "
                + "WHERE c.creatorId = ? AND c.isDelete = 0 "
                + "GROUP BY c.id, c.className, c.classCode, c.description, c.creatorId, c.createdAt, c.updatedAt "
                + "ORDER BY c.updatedAt DESC, c.id DESC", teacher.getId());
    }

    public List<Map<String, Object>> listAllActiveClasses() {
        return jdbc.queryForList("SELECT c.id, c.className, c.classCode, c.description, c.creatorId, "
                + "c.createdAt, c.updatedAt, COUNT(m.id) AS studentCount FROM teaching_class c "
                + "LEFT JOIN teaching_class_member m ON m.classId = c.id "
                + "WHERE c.isDelete = 0 GROUP BY c.id, c.className, c.classCode, c.description, c.creatorId, c.createdAt, c.updatedAt "
                + "ORDER BY c.className ASC");
    }

    public List<Map<String, Object>> memberIdsOfClass(String className) {
        return jdbc.queryForList("SELECT m.userId FROM teaching_class c JOIN teaching_class_member m ON m.classId=c.id "
                + "JOIN `user` u ON u.id=m.userId WHERE c.className=? AND c.isDelete=0 AND u.isDelete=0", className);
    }

    public Map<String, Object> create(User teacher, Map<String, Object> body) {
        String name = text(body, "className");
        if (!StringUtils.hasText(name)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "班级名称不能为空");
        validateLengths(name, text(body, "description"));
        String description = text(body, "description");
        String code = newCode();
        try {
            jdbc.update("INSERT INTO teaching_class(className,classCode,description,creatorId) VALUES(?,?,?,?)",
                    name.trim(), code, description, teacher.getId());
        } catch (DuplicateKeyException error) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "班级名称已存在");
        }
        return findOwned(teacher, idByCode(code));
    }

    public Map<String, Object> update(User teacher, long classId, Map<String, Object> body) {
        requireOwned(teacher, classId);
        String name = text(body, "className");
        String description = text(body, "description");
        validateLengths(name, description);
        if (StringUtils.hasText(name)) {
            try {
                jdbc.update("UPDATE teaching_class SET className=?, description=? WHERE id=? AND creatorId=? AND isDelete=0",
                        name.trim(), description, classId, teacher.getId());
            } catch (DuplicateKeyException error) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "班级名称已存在");
            }
        } else {
            jdbc.update("UPDATE teaching_class SET description=? WHERE id=? AND creatorId=? AND isDelete=0",
                    description, classId, teacher.getId());
        }
        return findOwned(teacher, classId);
    }

    public Map<String, Object> rotateCode(User teacher, long classId) {
        requireOwned(teacher, classId);
        jdbc.update("UPDATE teaching_class SET classCode=? WHERE id=? AND creatorId=? AND isDelete=0",
                newCode(), classId, teacher.getId());
        return findOwned(teacher, classId);
    }

    public void delete(User teacher, long classId) {
        requireOwned(teacher, classId);
        jdbc.update("UPDATE teaching_class SET isDelete=1 WHERE id=? AND creatorId=?", classId, teacher.getId());
    }

    public List<Map<String, Object>> members(User teacher, long classId) {
        requireOwned(teacher, classId);
        return jdbc.queryForList("SELECT CAST(m.userId AS CHAR) AS userId, u.userName, u.userAccount, u.userAvatar, m.joinedAt "
                + "FROM teaching_class_member m JOIN `user` u ON u.id=m.userId "
                + "WHERE m.classId=? AND u.isDelete=0 ORDER BY m.joinedAt ASC", classId);
    }

    public void removeMember(User teacher, long classId, long userId) {
        requireOwned(teacher, classId);
        jdbc.update("DELETE FROM teaching_class_member WHERE classId=? AND userId=?", classId, userId);
    }

    public List<Map<String, Object>> listMyClasses(User student) {
        requireStudent(student);
        return jdbc.queryForList("SELECT c.id AS classId, c.className, c.classCode, c.description, "
                + "c.creatorId, u.userName AS teacherName, m.joinedAt "
                + "FROM teaching_class_member m JOIN teaching_class c ON c.id=m.classId AND c.isDelete=0 "
                + "LEFT JOIN `user` u ON u.id=c.creatorId WHERE m.userId=? ORDER BY m.joinedAt DESC", student.getId());
    }

    public Map<String, Object> join(User student, Map<String, Object> body) {
        requireStudent(student);
        String code = text(body, "classCode");
        if (!StringUtils.hasText(code)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入班级邀请码");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM teaching_class WHERE classCode=? AND isDelete=0", code.trim());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.PARAMS_ERROR, "班级邀请码无效或班级已关闭");
        long classId = ((Number) rows.get(0).get("id")).longValue();
        jdbc.update("INSERT IGNORE INTO teaching_class_member(classId,userId) VALUES(?,?)", classId, student.getId());
        return jdbc.queryForMap("SELECT c.id AS classId, c.className, c.classCode, c.description, "
                + "c.creatorId, u.userName AS teacherName, m.joinedAt FROM teaching_class_member m "
                + "JOIN teaching_class c ON c.id=m.classId LEFT JOIN `user` u ON u.id=c.creatorId "
                + "WHERE m.classId=? AND m.userId=?", classId, student.getId());
    }

    public void leave(User student, long classId) {
        requireStudent(student);
        jdbc.update("DELETE FROM teaching_class_member WHERE classId=? AND userId=?", classId, student.getId());
    }

    /** 任务发布按班级名解析成员；表尚未升级时返回空，兼容旧库启动。 */
    public List<Map<String, Object>> audienceRows(List<String> classNames, List<Long> userIds) {
        if ((classNames == null || classNames.isEmpty()) && (userIds == null || userIds.isEmpty())) return new ArrayList<>();
        List<String> names = classNames == null ? new ArrayList<>() : classNames;
        List<Long> ids = userIds == null ? new ArrayList<>() : userIds;
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (!names.isEmpty()) {
            clauses.add("c.className IN (" + String.join(",", Collections.nCopies(names.size(), "?")) + ")");
            args.addAll(names);
        }
        if (!ids.isEmpty()) {
            clauses.add("m.userId IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")");
            args.addAll(ids);
        }
        String sql = "SELECT c.className, m.userId, u.userName, u.userRole, u.isDelete "
                + "FROM teaching_class c JOIN teaching_class_member m ON m.classId=c.id "
                + "JOIN `user` u ON u.id=m.userId WHERE c.isDelete=0 AND u.isDelete=0 AND u.userRole <> 'ban' AND ("
                + String.join(" OR ", clauses) + ") ORDER BY c.className, m.userId";
        try {
            return jdbc.queryForList(sql, args.toArray());
        } catch (DataAccessException ignored) {
            return new ArrayList<>();
        }
    }

    private long idByCode(String code) {
        Number id = jdbc.queryForObject("SELECT id FROM teaching_class WHERE classCode=?", Number.class, code);
        return id == null ? 0 : id.longValue();
    }

    private Map<String, Object> findOwned(User teacher, long classId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.id, c.className, c.classCode, c.description, c.creatorId, c.createdAt, c.updatedAt, "
                + "(SELECT COUNT(*) FROM teaching_class_member m WHERE m.classId=c.id) AS studentCount "
                + "FROM teaching_class c WHERE c.id=? AND c.creatorId=? AND c.isDelete=0", classId, teacher.getId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "班级不存在");
        return rows.get(0);
    }

    private void requireOwned(User teacher, long classId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM teaching_class WHERE id=? AND creatorId=? AND isDelete=0",
                Integer.class, classId, teacher.getId()) == 0) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "班级不存在或无权操作");
    }

    private String newCode() {
        return "OS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private void requireStudent(User user) {
        if (user == null || !UserConstant.DEFAULT_ROLE.equals(user.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅学生账号可以加入或退出班级");
        }
    }

    private void validateLengths(String name, String description) {
        if (StringUtils.hasText(name) && name.length() > 128) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "班级名称不能超过 128 个字符");
        }
        if (description.length() > 512) throw new BusinessException(ErrorCode.PARAMS_ERROR, "班级说明不能超过 512 个字符");
    }

    private String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
