package com.yupi.springbootinit.task;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.model.dto.task.ExperimentDraftRequest;
import com.yupi.springbootinit.model.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 共享实验的表单维护链路：新建草稿、改稿（发布后进入待应用修订）、应用、发布/下线。
 * 始终保留原 questionId、提交历史与实验映射。
 */
@Service
public class ExperimentDraftService {
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private NodeIdentityService identities;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(ExperimentDraftRequest request, User user) {
        validate(request);
        long id = IdWorker.getId();
        jdbc.update("INSERT INTO question(id,title,content,tags,answer,submitNum,acceptedNum,judgeCase,judgeConfig,"
                        + "thumbNum,favourNum,userId,originalCode,resourceScope,publicationStatus) "
                        + "VALUES(?,?,?,?,?,0,0,?,?,0,0,?,?,'SHARED','DRAFT')",
                id, request.getTitle().trim(), request.getContent(), json(request.getTags(), "[]"),
                request.getAnswer(), json(request.getJudgeCase(), "[]"), json(request.getJudgeConfig(), "{}"),
                user.getId(), request.getOriginalCode());
        replaceNodes(id, request.getNodeCodes());
        jdbc.update("INSERT INTO course_resource(id,resourceCode,resourceType,title,questionId,status,metadataJson) "
                        + "VALUES(?,?,'EXPERIMENT',?,?,'DRAFT','{}') ON DUPLICATE KEY UPDATE title=VALUES(title),questionId=VALUES(questionId)",
                IdWorker.getId(), "exp." + id, request.getTitle().trim(), id);
        return get(id);
    }

    public Map<String, Object> get(long id) {
        Map<String, Object> row = question(id, false);
        if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "实验不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("title", row.get("title"));
        out.put("content", row.get("content"));
        out.put("originalCode", row.get("originalCode"));
        out.put("answer", row.get("answer"));
        out.put("tags", parse(row.get("tags"), "[]"));
        out.put("judgeCase", parse(row.get("judgeCase"), "[]"));
        out.put("judgeConfig", parse(row.get("judgeConfig"), "{}"));
        out.put("publicationStatus", row.get("publicationStatus"));
        out.put("nodeCodes", nodeCodes(id));
        out.put("resourceCode", resourceCode(id));
        out.put("updatedAt", epoch(row.get("updateTime")));
        Map<String, Object> revision = latestRevision(id);
        if (revision != null && !"APPLIED".equals(revision.get("reviewStatus"))) {
            out.put("pendingRevision", revision.get("revision"));
            out.put("pendingStatus", revision.get("reviewStatus"));
            out.put("pending", parse(revision.get("requestJson"), "{}"));
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(long id, ExperimentDraftRequest request, User user) {
        validate(request);
        Map<String, Object> row = question(id, false);
        ThrowUtils.throwIf(row == null, ErrorCode.NOT_FOUND_ERROR, "实验不存在");
        if ("DRAFT".equals(row.get("publicationStatus"))) {
            updateQuestion(id, request);
            replaceNodes(id, request.getNodeCodes());
            touchResource(id, request.getTitle(), "DRAFT");
            return get(id);
        }
        boolean apply = Boolean.TRUE.equals(request.getApply());
        if (apply) {
            updateQuestion(id, request);
            replaceNodes(id, request.getNodeCodes());
            jdbc.update("UPDATE question SET publicationStatus='PUBLISHED' WHERE id=?", id);
            touchResource(id, request.getTitle(), "PUBLISHED");
            return get(id);
        }
        Map<String, Object> current = latestRevision(id);
        int revision = current == null ? 1 : ((Number) current.get("revision")).intValue() + 1;
        ObjectNode requestJson = JSON.valueToTree(request);
        ObjectNode after = JSON.createObjectNode();
        after.put("title", request.getTitle());
        after.put("content", request.getContent());
        after.put("originalCode", request.getOriginalCode());
        after.put("answer", request.getAnswer());
        after.set("tags", parse(request.getTags(), "[]"));
        after.set("judgeCase", parse(request.getJudgeCase(), "[]"));
        after.set("judgeConfig", parse(request.getJudgeConfig(), "{}"));
        jdbc.update("INSERT INTO experiment_revision(id,questionId,resourceCode,revision,reviewStatus,requestJson,"
                        + "beforeJson,afterJson,userId,contributor,batchCode) VALUES(?,?,?,?,'DRAFT',?,?,?,?,?,?)",
                IdWorker.getId(), id, resourceCode(id), revision, requestJson.toString(),
                JSON.valueToTree(row).toString(), after.toString(), user.getId(), "L",
                "batch.form." + IdWorker.getId());
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(long id, User user) {
        Map<String, Object> revision = latestRevision(id);
        ThrowUtils.throwIf(revision == null || "APPLIED".equals(revision.get("reviewStatus")),
                ErrorCode.OPERATION_ERROR, "没有待应用的实验修订");
        JsonNode after = parse(revision.get("afterJson"), "{}");
        JsonNode requestJson = parse(revision.get("requestJson"), "{}");
        ExperimentDraftRequest request = new ExperimentDraftRequest();
        request.setTitle(after.path("title").asText());
        request.setContent(after.path("content").asText());
        request.setOriginalCode(after.path("originalCode").asText());
        request.setAnswer(after.path("answer").asText());
        request.setTags(after.path("tags"));
        request.setJudgeCase(after.path("judgeCase"));
        request.setJudgeConfig(after.path("judgeConfig"));
        request.setNodeCodes(new ArrayList<>());
        for (JsonNode code : requestJson.path("nodeCodes")) request.getNodeCodes().add(code.asText());
        validate(request);
        updateQuestion(id, request);
        replaceNodes(id, request.getNodeCodes());
        jdbc.update("UPDATE question SET publicationStatus='PUBLISHED' WHERE id=?", id);
        jdbc.update("UPDATE experiment_revision SET reviewStatus='APPLIED',reviewerId=?,appliedTime=CURRENT_TIMESTAMP WHERE id=?",
                user.getId(), revision.get("id"));
        touchResource(id, request.getTitle(), "PUBLISHED");
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publish(long id, User user) {
        Map<String, Object> row = question(id, false);
        ThrowUtils.throwIf(row == null, ErrorCode.NOT_FOUND_ERROR, "实验不存在");
        JsonNode judgeCase = parse(row.get("judgeCase"), "[]");
        ThrowUtils.throwIf(!judgeCase.isArray() || judgeCase.isEmpty(),
                ErrorCode.OPERATION_ERROR, "实验至少需要一个测试用例才能发布");
        jdbc.update("UPDATE question SET publicationStatus='PUBLISHED' WHERE id=?", id);
        touchResource(id, String.valueOf(row.get("title")), "PUBLISHED");
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> offline(long id) {
        jdbc.update("UPDATE question SET publicationStatus='OFFLINE' WHERE id=?", id);
        String code = resourceCode(id);
        if (code != null) jdbc.update("UPDATE course_resource SET status='OFFLINE' WHERE resourceCode=?", code);
        return get(id);
    }

    private void updateQuestion(long id, ExperimentDraftRequest request) {
        jdbc.update("UPDATE question SET title=?,content=?,tags=?,answer=?,judgeCase=?,judgeConfig=?,originalCode=? WHERE id=?",
                request.getTitle().trim(), request.getContent(), json(request.getTags(), "[]"),
                request.getAnswer(), json(request.getJudgeCase(), "[]"),
                json(request.getJudgeConfig(), "{}"), request.getOriginalCode(), id);
    }

    private void replaceNodes(long id, List<String> nodeCodes) {
        jdbc.update("DELETE FROM experiment_node WHERE questionId=?", id);
        Set<String> unique = new LinkedHashSet<>(nodeCodes == null ? new ArrayList<>() : nodeCodes);
        for (String code : unique) {
            if (StringUtils.isBlank(code)) continue;
            NodeIdentityService.Identity identity = identities.resolve(code.trim());
            if (identity == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识节点不存在：" + code);
            jdbc.update("INSERT INTO experiment_node(id,questionId,nodeCode,nodeName,role,weight,reason) VALUES(?,?,?,?,?,?,?)",
                    IdWorker.getId(), id, identity.nodeCode, identity.storageKey, "PRIMARY", 1.0, "共享实验表单映射");
        }
    }

    private void touchResource(long id, String title, String status) {
        String code = resourceCode(id);
        if (code == null) {
            jdbc.update("INSERT INTO course_resource(id,resourceCode,resourceType,title,questionId,status,metadataJson) "
                            + "VALUES(?,?,'EXPERIMENT',?,?,?,'{}')",
                    IdWorker.getId(), "exp." + id, title, id, status);
        } else {
            jdbc.update("UPDATE course_resource SET title=?,status=? WHERE resourceCode=?", title, status, code);
        }
    }

    private void validate(ExperimentDraftRequest request) {
        ThrowUtils.throwIf(request == null || StringUtils.isAnyBlank(request.getTitle(), request.getContent()),
                ErrorCode.PARAMS_ERROR, "实验标题和说明不能为空");
        JsonNode cases = request.getJudgeCase();
        if (cases != null) {
            ThrowUtils.throwIf(!cases.isArray(), ErrorCode.PARAMS_ERROR, "测试用例必须为数组");
            for (JsonNode item : cases) {
                ThrowUtils.throwIf(!item.path("input").isTextual() || !item.path("output").isTextual(),
                        ErrorCode.PARAMS_ERROR, "测试用例需包含字符串 input/output");
            }
        }
        JsonNode config = request.getJudgeConfig();
        if (config != null && !config.isNull()) {
            ThrowUtils.throwIf(!config.isObject() || config.path("timeLimit").asLong(0) <= 0
                            || config.path("memoryLimit").asLong(0) <= 0,
                    ErrorCode.PARAMS_ERROR, "判题配置需正数 timeLimit/memoryLimit");
        }
        JsonNode tags = request.getTags();
        if (tags != null && !tags.isNull()) {
            ThrowUtils.throwIf(!tags.isArray(), ErrorCode.PARAMS_ERROR, "tags 必须为数组");
        }
    }

    private Map<String, Object> question(long id, boolean forUpdate) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,title,content,tags,answer,judgeCase,judgeConfig,originalCode,publicationStatus,updateTime "
                        + "FROM question WHERE id=? AND isDelete=0 AND resourceScope='SHARED'"
                        + (forUpdate ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> latestRevision(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,revision,reviewStatus,requestJson,afterJson FROM experiment_revision "
                        + "WHERE questionId=? ORDER BY revision DESC LIMIT 1", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String resourceCode(long id) {
        List<String> codes = jdbc.queryForList(
                "SELECT resourceCode FROM course_resource WHERE questionId=? AND resourceType='EXPERIMENT' AND isDelete=0 LIMIT 1",
                String.class, id);
        return codes.isEmpty() ? null : codes.get(0);
    }

    private List<String> nodeCodes(long id) {
        List<String> codes = jdbc.queryForList(
                "SELECT nodeCode FROM experiment_node WHERE questionId=? ORDER BY nodeCode", String.class, id);
        return new ArrayList<>(new LinkedHashSet<>(codes));
    }

    private static String json(JsonNode node, String fallback) {
        return node == null || node.isNull() ? fallback : node.toString();
    }

    private static long epoch(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).getTime();
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof java.util.Date) return ((java.util.Date) value).getTime();
        return 0L;
    }

    private static JsonNode parse(Object value, String fallback) {
        try {
            return JSON.readTree(value == null ? fallback : value.toString());
        } catch (Exception e) {
            try {
                return JSON.readTree(fallback);
            } catch (Exception ignored) {
                return JSON.nullNode();
            }
        }
    }
}
