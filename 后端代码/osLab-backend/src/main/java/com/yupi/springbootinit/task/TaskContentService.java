package com.yupi.springbootinit.task;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.model.dto.task.TaskItemRequest;
import com.yupi.springbootinit.model.entity.LearningTask;
import com.yupi.springbootinit.model.entity.LearningTaskItem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务内容校验、来源解析、不可变执行副本与学生视角脱敏。
 * 任务副本只写 learning_question / question 的 TASK 作用域，绝不注册 course_resource。
 */
@Service
public class TaskContentService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> QUESTION_TYPES = new HashSet<>(Arrays.asList(
            "JUDGE", "SINGLE", "MULTIPLE", "FILL_BLANK"));

    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private NodeIdentityService identities;

    // ---------------- 草稿校验与规范化 ----------------

    public ObjectNode normalizeItem(TaskItemRequest request) {
        if (request == null) throw bad("内容块不能为空");
        String type = request.getItemType();
        if (!TaskConstants.ITEM_TYPES.contains(type)) throw bad("内容块类型无效");
        ObjectNode content = request.getContent() == null ? JSON.createObjectNode()
                : (ObjectNode) request.getContent().deepCopy();
        if (request.getMaxScore() != null && request.getMaxScore().compareTo(BigDecimal.ZERO) < 0) {
            throw bad("分值不能为负数");
        }
        switch (type) {
            case TaskConstants.ITEM_TEXT:
                validateText(content);
                break;
            case TaskConstants.ITEM_MATERIAL:
                validateMaterial(content);
                break;
            case TaskConstants.ITEM_QUESTION:
                content = normalizeQuestion(request, content);
                break;
            case TaskConstants.ITEM_EXPERIMENT:
                content = normalizeExperiment(request, content);
                break;
            default:
                throw bad("内容块类型无效");
        }
        return content;
    }

    private void validateText(ObjectNode content) {
        if (StringUtils.isBlank(text(content, "title")) && StringUtils.isBlank(text(content, "contentMd"))) {
            throw bad("文字任务需要标题或正文");
        }
        if (content.path("requireText").asBoolean(false) && StringUtils.isBlank(text(content, "textPrompt"))) {
            throw bad("要求文字答复时必须填写答复提示");
        }
        if (!content.path("requireText").isBoolean()) content.put("requireText", false);
        if (!content.path("requireFile").isBoolean()) content.put("requireFile", false);
        if (!content.path("requireConfirm").isBoolean()) content.put("requireConfirm", false);
    }

    private void validateMaterial(ObjectNode content) {
        JsonNode files = content.path("files");
        if (!files.isArray() || files.isEmpty()) {
            throw bad("资料内容块至少需要一个文件");
        }
        if (files.size() > 5) throw bad("每个内容块最多 5 个附件");
        for (JsonNode file : files) {
            if (StringUtils.isBlank(file.path("fileId").asText())
                    || StringUtils.isBlank(file.path("originalName").asText())) {
                throw bad("资料文件元数据不完整");
            }
            if (file.path("size").asLong(0) > 10L * 1024 * 1024) throw bad("附件不能超过 10MB");
        }
    }

    private ObjectNode normalizeQuestion(TaskItemRequest request, ObjectNode content) {
        String mode = StringUtils.defaultIfBlank(request.getSourceMode(), TaskConstants.MODE_AUTHORED).toUpperCase();
        if (!TaskConstants.MODE_REFERENCE.equals(mode) && !TaskConstants.MODE_AUTHORED.equals(mode)) {
            throw bad("题目来源模式无效");
        }
        content.put("sourceMode", mode);
        if (TaskConstants.MODE_REFERENCE.equals(mode)) {
            long sourceId = longValue(content.path("sourceQuestionId"), "来源题目 id");
            ObjectNode source = loadQuestionSource(sourceId);
            int requestedRevision = content.path("sourceRevision").asInt(0);
            if (requestedRevision > 0 && source.path("sourceRevision").asInt() != requestedRevision) {
                throw bad("来源题目已更新，请重新选择");
            }
            ObjectNode merged = source.deepCopy();
            mergeOverrides(merged, content);
            merged.put("sourceMode", mode);
            merged.put("sourceQuestionId", String.valueOf(sourceId));
            merged.put("sourceQuestionCode", source.path("sourceQuestionCode").asText());
            merged.put("sourceRevision", source.path("sourceRevision").asInt());
            content = merged;
        } else {
            content.remove("sourceQuestionId");
            content.remove("sourceQuestionCode");
            content.remove("sourceRevision");
        }
        validateQuestionContent(content);
        content.put("evaluationIdentity", EvaluationIdentity.forQuestion(content, text(content, "sourceQuestionCode")));
        content.set("nodeMappings", resolveNodeMappings(content));
        return content;
    }

    private void validateQuestionContent(ObjectNode content) {
        if (StringUtils.isAnyBlank(text(content, "title"), text(content, "content"))) {
            throw bad("题目标题和题干不能为空");
        }
        String type = text(content, "type").toUpperCase();
        if (!QUESTION_TYPES.contains(type)) throw bad("题目类型无效");
        content.put("type", type);
        if (content.path("difficulty").asInt(0) < 1 || content.path("difficulty").asInt(0) > 5) {
            content.put("difficulty", 2);
        }
        JsonNode options = content.path("options");
        JsonNode answer = content.path("answer");
        if ("JUDGE".equals(type)) {
            boolean valid = answer.isBoolean() || "true".equalsIgnoreCase(answer.asText())
                    || "false".equalsIgnoreCase(answer.asText());
            if (!valid) throw bad("判断题答案必须为 true/false");
            if (!content.path("options").isArray()) content.putArray("options");
        } else if ("SINGLE".equals(type) || "MULTIPLE".equals(type)) {
            if (!options.isArray() || options.size() < 2) throw bad("选择题至少两个选项");
            Set<String> keys = new HashSet<>();
            for (JsonNode option : options) {
                String key = option.path("key").asText();
                if (StringUtils.isBlank(key) || StringUtils.isBlank(option.path("value").asText())) {
                    throw bad("选项 key/value 不能为空");
                }
                if (!keys.add(key)) throw bad("选项 key 重复：" + key);
            }
            if ("SINGLE".equals(type)) {
                if (!answer.isTextual() || !keys.contains(answer.asText())) {
                    throw bad("单选题答案必须为选项之一");
                }
            } else {
                if (!answer.isArray() || answer.isEmpty()) throw bad("多选题答案不能为空");
                Set<String> selected = new HashSet<>();
                for (JsonNode item : answer) {
                    if (!keys.contains(item.asText()) || !selected.add(item.asText())) {
                        throw bad("多选答案重复或不在选项中");
                    }
                }
            }
        } else {
            if (answer.isNull() || (!answer.isTextual() && !answer.isArray())) {
                throw bad("填空题答案必须为字符串或字符串数组");
            }
            if (!content.path("options").isArray()) content.putArray("options");
        }
    }

    private ObjectNode normalizeExperiment(TaskItemRequest request, ObjectNode content) {
        String mode = StringUtils.defaultIfBlank(request.getSourceMode(), TaskConstants.MODE_AUTHORED).toUpperCase();
        if (!TaskConstants.MODE_REFERENCE.equals(mode) && !TaskConstants.MODE_AUTHORED.equals(mode)) {
            throw bad("实验来源模式无效");
        }
        content.put("sourceMode", mode);
        if (TaskConstants.MODE_REFERENCE.equals(mode)) {
            long sourceId = longValue(content.path("sourceQuestionId"), "来源实验 id");
            ObjectNode source = loadExperimentSource(sourceId);
            long requestedAt = content.path("sourceUpdatedAt").asLong(0);
            if (requestedAt > 0 && source.path("sourceUpdatedAt").asLong() != requestedAt) {
                throw bad("来源实验已更新，请重新选择");
            }
            ObjectNode merged = source.deepCopy();
            mergeOverrides(merged, content);
            merged.put("sourceMode", mode);
            merged.put("sourceQuestionId", String.valueOf(sourceId));
            merged.put("sourceUpdatedAt", source.path("sourceUpdatedAt").asLong());
            content = merged;
        } else {
            content.remove("sourceQuestionId");
            content.remove("sourceUpdatedAt");
        }
        validateExperimentContent(content);
        content.put("evaluationIdentity", EvaluationIdentity.forExperiment(content));
        content.set("nodeMappings", resolveNodeMappings(content));
        return content;
    }

    private void validateExperimentContent(ObjectNode content) {
        if (StringUtils.isAnyBlank(text(content, "title"), text(content, "content"))) {
            throw bad("实验标题和说明不能为空");
        }
        boolean manual = content.path("manualReview").asBoolean(false);
        content.put("manualReview", manual);
        if (!manual) {
            JsonNode cases = content.path("judgeCase");
            if (!cases.isArray() || cases.isEmpty()) throw bad("自动判题实验必须至少一个测试用例");
            for (JsonNode item : cases) {
                if (!item.path("input").isTextual() || !item.path("output").isTextual()) {
                    throw bad("测试用例需包含字符串 input/output");
                }
            }
            JsonNode config = content.path("judgeConfig");
            if (!config.isObject() || config.path("timeLimit").asLong(0) <= 0
                    || config.path("memoryLimit").asLong(0) <= 0) {
                throw bad("判题配置需正数 timeLimit/memoryLimit");
            }
            JsonNode languages = content.path("languages");
            if (!languages.isArray() || languages.isEmpty()) throw bad("至少选择一种支持语言");
            for (JsonNode language : languages) {
                if (!TaskConstants.LANGUAGES.contains(language.asText())) {
                    throw bad("不支持的语言：" + language.asText());
                }
            }
            content.put("requirePass", content.path("requirePass").asBoolean(true));
        } else {
            content.putArray("judgeCase");
            content.put("manualReview", true);
            content.put("requirePass", false);
            if (!content.path("requireText").isBoolean()) content.put("requireText", true);
            if (!content.path("requireFile").isBoolean()) content.put("requireFile", false);
            if (!content.path("languages").isArray()) content.putArray("languages");
        }
        if (!content.path("samples").isArray()) content.putArray("samples");
        if (content.path("samples").size() > 5) throw bad("样例最多 5 条");
        if (!content.path("tags").isArray()) content.putArray("tags");
    }

    private void mergeOverrides(ObjectNode target, JsonNode overrides) {
        Set<String> meta = new HashSet<>(Arrays.asList("sourceMode", "sourceQuestionId", "sourceQuestionCode",
                "sourceRevision", "sourceUpdatedAt", "evaluationIdentity", "nodeMappings"));
        overrides.fieldNames().forEachRemaining(field -> {
            if (meta.contains(field)) return;
            JsonNode value = overrides.get(field);
            if (value != null && !value.isNull()) target.set(field, value.deepCopy());
        });
    }

    private ArrayNode resolveNodeMappings(ObjectNode content) {
        Set<String> requested = new HashSet<>();
        JsonNode codes = content.path("nodeCodes");
        if (codes.isArray()) {
            for (JsonNode code : codes) {
                if (StringUtils.isNotBlank(code.asText())) requested.add(code.asText().trim());
            }
        }
        JsonNode existing = content.path("nodeMappings");
        if (existing.isArray()) {
            for (JsonNode mapping : existing) {
                if (StringUtils.isNotBlank(mapping.path("nodeCode").asText())) {
                    requested.add(mapping.path("nodeCode").asText().trim());
                }
            }
        }
        ArrayNode mappings = JSON.createArrayNode();
        ArrayNode codesOut = JSON.createArrayNode();
        for (String code : requested) {
            NodeIdentityService.Identity identity;
            try {
                identity = identities.resolve(code);
            } catch (RuntimeException e) {
                throw bad("知识节点无法唯一解析：" + code);
            }
            if (identity == null) throw bad("知识节点不存在：" + code);
            ObjectNode mapping = mappings.addObject();
            mapping.put("nodeCode", identity.nodeCode);
            mapping.put("nodeName", identity.storageKey);
            mapping.put("displayName", identity.name);
            mapping.put("role", "PRIMARY");
            mapping.put("weight", 1.0);
            codesOut.add(identity.nodeCode);
        }
        content.set("nodeCodes", codesOut);
        return mappings;
    }

    // ---------------- 发布执行副本 ----------------

    public ObjectNode validateDraftForPublish(LearningTaskItem item, ObjectNode draft) {
        String mode = draft.path("sourceMode").asText("");
        if (TaskConstants.MODE_REFERENCE.equals(mode)) {
            if (TaskConstants.ITEM_QUESTION.equals(item.getItemType())) {
                long sourceId = longValue(draft.path("sourceQuestionId"), "来源题目 id");
                int expected = draft.path("sourceRevision").asInt();
                Integer current = jdbc.queryForObject(
                        "SELECT revision FROM learning_question WHERE id=? AND isDelete=0 AND resourceScope='SHARED' AND reviewStatus='PUBLISHED'",
                        Integer.class, sourceId);
                if (current == null) throw bad("来源题目已不可用，请重新选择");
                if (current != expected) throw bad("来源题目已更新，请重新预览后发布");
                validateQuestionContent(draft);
            } else if (TaskConstants.ITEM_EXPERIMENT.equals(item.getItemType())) {
                long sourceId = longValue(draft.path("sourceQuestionId"), "来源实验 id");
                Long current = updatedAt(sourceId);
                if (current == null) throw bad("来源实验已不可用，请重新选择");
                if (current != draft.path("sourceUpdatedAt").asLong()) {
                    throw bad("来源实验已更新，请重新预览后发布");
                }
                validateExperimentContent(draft);
            }
        } else if (TaskConstants.ITEM_QUESTION.equals(item.getItemType())) {
            validateQuestionContent(draft);
        } else if (TaskConstants.ITEM_EXPERIMENT.equals(item.getItemType())) {
            validateExperimentContent(draft);
        }
        return draft;
    }

    public ObjectNode createExecutionCopy(LearningTask task, LearningTaskItem item, ObjectNode draft, long userId) {
        ObjectNode snapshot = draft.deepCopy();
        if (TaskConstants.ITEM_QUESTION.equals(item.getItemType())) {
            long id = IdWorker.getId();
            String code = taskItemCode(task.getTaskCode(), item.getItemCode());
            jdbc.update("INSERT INTO learning_question(id,questionCode,title,content,type,options,answer,analysis,"
                            + "misconception,chapter,sectionCode,difficulty,reviewStatus,userId,revision,resourceScope,"
                            + "ownerTaskCode,ownerTaskItemCode) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'PUBLISHED',?,1,'TASK',?,?)",
                    id, code, text(draft, "title"), text(draft, "content"), text(draft, "type"),
                    jsonText(draft.path("options"), "[]"), jsonText(draft.path("answer"), "null"),
                    text(draft, "analysis"), text(draft, "misconception"), text(draft, "chapter"),
                    nullIfBlank(text(draft, "sectionCode")), Math.max(1, Math.min(5, draft.path("difficulty").asInt(2))),
                    userId, task.getTaskCode(), item.getItemCode());
            insertQuestionNodes(id, draft.path("nodeMappings"));
            snapshot.put("runtimeResourceId", String.valueOf(id));
            snapshot.put("runtimeResourceType", "QUESTION");
            snapshot.put("evaluationIdentity", EvaluationIdentity.forQuestion(snapshot, text(snapshot, "sourceQuestionCode")));
        } else if (TaskConstants.ITEM_EXPERIMENT.equals(item.getItemType())) {
            long id = IdWorker.getId();
            jdbc.update("INSERT INTO question(id,title,content,tags,answer,submitNum,acceptedNum,judgeCase,judgeConfig,"
                            + "thumbNum,favourNum,userId,originalCode,resourceScope,ownerTaskCode,ownerTaskItemCode,publicationStatus) "
                            + "VALUES(?,?,?,?,?,0,0,?,?,0,0,?,?,'TASK',?,?,'PUBLISHED')",
                    id, text(draft, "title"), text(draft, "content"), jsonText(draft.path("tags"), "[]"),
                    text(draft, "answer"), jsonText(draft.path("judgeCase"), "[]"),
                    jsonText(draft.path("judgeConfig"), "{}"), userId, text(draft, "originalCode"),
                    task.getTaskCode(), item.getItemCode());
            insertExperimentNodes(id, draft.path("nodeMappings"));
            snapshot.put("runtimeResourceId", String.valueOf(id));
            snapshot.put("runtimeResourceType", "EXPERIMENT");
            snapshot.put("evaluationIdentity", EvaluationIdentity.forExperiment(snapshot));
        }
        snapshot.put("sourceQuestionId", draft.path("sourceQuestionId").isMissingNode()
                ? null : draft.path("sourceQuestionId").asText());
        return snapshot;
    }

    private String taskItemCode(String taskCode, String itemCode) {
        String value = ("task." + taskCode + "." + itemCode).replaceAll("[^A-Za-z0-9._-]", "_");
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private void insertQuestionNodes(long questionId, JsonNode mappings) {
        for (JsonNode mapping : mappings) {
            jdbc.update("INSERT INTO learning_question_node(id,questionId,nodeName,nodeCode,role,weight) VALUES(?,?,?,?,?,?)",
                    IdWorker.getId(), questionId, mapping.path("nodeName").asText(),
                    mapping.path("nodeCode").asText(), "PRIMARY", 1.0);
        }
    }

    private void insertExperimentNodes(long questionId, JsonNode mappings) {
        for (JsonNode mapping : mappings) {
            jdbc.update("INSERT INTO experiment_node(id,questionId,nodeCode,nodeName,role,weight,reason) VALUES(?,?,?,?,?,?,?)",
                    IdWorker.getId(), questionId, mapping.path("nodeCode").asText(),
                    mapping.path("nodeName").asText(), "PRIMARY", 1.0, "任务发布映射快照");
        }
    }

    // ---------------- 学生视角 ----------------

    /** 是否需要教师人工批阅。 */
    public static boolean manualRequired(String itemType, JsonNode content) {
        if (TaskConstants.ITEM_TEXT.equals(itemType)) {
            return content.path("requireText").asBoolean(false) || content.path("requireFile").asBoolean(false);
        }
        if (TaskConstants.ITEM_MATERIAL.equals(itemType)) {
            return content.path("requireFile").asBoolean(false);
        }
        if (TaskConstants.ITEM_EXPERIMENT.equals(itemType)) {
            return content.path("manualReview").asBoolean(false);
        }
        return false;
    }

    public static boolean requirePass(String itemType, JsonNode content) {
        return TaskConstants.ITEM_EXPERIMENT.equals(itemType) && content.path("requirePass").asBoolean(true);
    }

    public ObjectNode studentContent(LearningTaskItem item) {
        ObjectNode snapshot;
        try {
            snapshot = (ObjectNode) JSON.readTree(StringUtils.defaultIfBlank(item.getPublishedSnapshotJson(), "{}"));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "任务内容快照损坏");
        }
        ObjectNode visible = snapshot.deepCopy();
        visible.remove(Arrays.asList("sourceQuestionId", "sourceQuestionCode", "sourceRevision",
                "sourceUpdatedAt", "evaluationIdentity", "nodeMappings", "nodeCodes", "runtimeResourceId",
                "runtimeResourceType", "itemCode", "itemType", "sortOrder", "required", "manual", "maxScore"));
        if (TaskConstants.ITEM_QUESTION.equals(item.getItemType())) {
            visible.remove(Arrays.asList("answer", "analysis", "misconception"));
        } else if (TaskConstants.ITEM_EXPERIMENT.equals(item.getItemType())) {
            visible.remove(Arrays.asList("answer", "judgeCase"));
        }
        if (visible.path("files").isArray()) {
            for (JsonNode file : visible.path("files")) {
                if (file instanceof ObjectNode) {
                    ((ObjectNode) file).remove("storageKey");
                }
            }
        }
        return visible;
    }

    // ---------------- 资源选择器 ----------------

    public Map<String, Object> listQuestions(String keyword, long current, long pageSize) {
        long size = Math.min(Math.max(pageSize, 1), 20);
        long offset = (Math.max(current, 1) - 1) * size;
        String like = StringUtils.isBlank(keyword) ? null : "%" + keyword.trim() + "%";
        String where = " FROM learning_question WHERE isDelete=0 AND resourceScope='SHARED' AND reviewStatus='PUBLISHED'"
                + (like == null ? "" : " AND title LIKE ?");
        List<Object> args = new ArrayList<>();
        if (like != null) args.add(like);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(size);
        args.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,questionCode,title,type,difficulty,chapter,sectionCode,revision,updateTime"
                        + where + " ORDER BY updateTime DESC,id DESC LIMIT ? OFFSET ?", args.toArray());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(row.get("id")));
            item.put("questionCode", row.get("questionCode"));
            item.put("title", row.get("title"));
            item.put("type", row.get("type"));
            item.put("difficulty", row.get("difficulty"));
            item.put("chapter", row.get("chapter"));
            item.put("sectionCode", row.get("sectionCode"));
            item.put("revision", row.get("revision"));
            item.put("nodeCodes", questionNodeCodes(((Number) row.get("id")).longValue()));
            records.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total);
        out.put("current", current);
        out.put("pageSize", size);
        return out;
    }

    public ObjectNode getQuestionResource(long id) {
        return loadQuestionSource(id);
    }

    public Map<String, Object> listExperiments(String keyword, long current, long pageSize) {
        long size = Math.min(Math.max(pageSize, 1), 20);
        long offset = (Math.max(current, 1) - 1) * size;
        String like = StringUtils.isBlank(keyword) ? null : "%" + keyword.trim() + "%";
        String where = " FROM question q WHERE q.isDelete=0 AND q.resourceScope='SHARED' "
                + "AND (q.publicationStatus IS NULL OR q.publicationStatus='PUBLISHED')"
                + (like == null ? "" : " AND q.title LIKE ?");
        List<Object> args = new ArrayList<>();
        if (like != null) args.add(like);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(size);
        args.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT q.id,q.title,q.updateTime,r.resourceCode FROM question q "
                        + "LEFT JOIN course_resource r ON r.questionId=q.id AND r.resourceType='EXPERIMENT' "
                        + "AND r.isDelete=0 WHERE q.isDelete=0 AND q.resourceScope='SHARED' "
                        + "AND (q.publicationStatus IS NULL OR q.publicationStatus='PUBLISHED')"
                        + (like == null ? "" : " AND q.title LIKE ?")
                        + " ORDER BY q.updateTime DESC,q.id DESC LIMIT ? OFFSET ?", args.toArray());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(row.get("id")));
            item.put("title", row.get("title"));
            item.put("resourceCode", row.get("resourceCode"));
            item.put("updatedAt", row.get("updateTime"));
            item.put("nodeCodes", experimentNodeCodes(((Number) row.get("id")).longValue()));
            records.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total);
        out.put("current", current);
        out.put("pageSize", size);
        return out;
    }

    public ObjectNode getExperimentResource(long id) {
        return loadExperimentSource(id);
    }

    private ObjectNode loadQuestionSource(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,questionCode,revision,title,content,type,options,answer,analysis,misconception,"
                        + "difficulty,chapter,sectionCode FROM learning_question WHERE id=? AND isDelete=0 "
                        + "AND resourceScope='SHARED' AND reviewStatus='PUBLISHED'", id);
        if (rows.isEmpty()) throw bad("来源题目不存在或未发布");
        Map<String, Object> row = rows.get(0);
        ObjectNode out = JSON.createObjectNode();
        out.put("sourceQuestionId", String.valueOf(row.get("id")));
        out.put("sourceQuestionCode", str(row.get("questionCode")));
        out.put("sourceRevision", ((Number) row.get("revision")).intValue());
        out.put("title", str(row.get("title")));
        out.put("content", str(row.get("content")));
        out.put("type", str(row.get("type")));
        out.set("options", parse(row.get("options"), "[]"));
        out.set("answer", parse(row.get("answer"), "null"));
        out.put("analysis", str(row.get("analysis")));
        out.put("misconception", str(row.get("misconception")));
        out.put("difficulty", row.get("difficulty") == null ? 2 : ((Number) row.get("difficulty")).intValue());
        out.put("chapter", str(row.get("chapter")));
        out.put("sectionCode", str(row.get("sectionCode")));
        ArrayNode codes = out.putArray("nodeCodes");
        questionNodeCodes(id).forEach(codes::add);
        return out;
    }

    private ObjectNode loadExperimentSource(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,title,content,tags,answer,judgeCase,judgeConfig,originalCode,updateTime FROM question "
                        + "WHERE id=? AND isDelete=0 AND resourceScope='SHARED' "
                        + "AND (publicationStatus IS NULL OR publicationStatus='PUBLISHED')", id);
        if (rows.isEmpty()) throw bad("来源实验不存在或未发布");
        Map<String, Object> row = rows.get(0);
        ObjectNode out = JSON.createObjectNode();
        out.put("sourceQuestionId", String.valueOf(row.get("id")));
        long updatedAt = row.get("updateTime") instanceof Timestamp
                ? ((Timestamp) row.get("updateTime")).getTime() : 0L;
        out.put("sourceUpdatedAt", updatedAt);
        out.put("title", str(row.get("title")));
        out.put("content", str(row.get("content")));
        out.set("tags", parse(row.get("tags"), "[]"));
        out.put("answer", str(row.get("answer")));
        out.set("judgeCase", parse(row.get("judgeCase"), "[]"));
        out.set("judgeConfig", parse(row.get("judgeConfig"), "{}"));
        out.put("originalCode", str(row.get("originalCode")));
        out.put("manualReview", false);
        out.put("requirePass", true);
        ArrayNode languages = out.putArray("languages");
        languages.add("java");
        languages.add("cpp");
        languages.add("c");
        languages.add("go");
        out.putArray("samples");
        ArrayNode codes = out.putArray("nodeCodes");
        experimentNodeCodes(id).forEach(codes::add);
        return out;
    }

    private Long updatedAt(long questionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT updateTime FROM question WHERE id=? AND isDelete=0 AND resourceScope='SHARED' "
                        + "AND (publicationStatus IS NULL OR publicationStatus='PUBLISHED')", questionId);
        if (rows.isEmpty()) return null;
        Object value = rows.get(0).get("updateTime");
        return value instanceof Timestamp ? ((Timestamp) value).getTime() : 0L;
    }

    private List<String> questionNodeCodes(long questionId) {
        List<String> codes = jdbc.queryForList(
                "SELECT COALESCE(nodeCode,nodeName) FROM learning_question_node WHERE questionId=?", String.class, questionId);
        return new ArrayList<>(new java.util.LinkedHashSet<>(codes));
    }

    private List<String> experimentNodeCodes(long questionId) {
        return jdbc.queryForList("SELECT nodeCode FROM experiment_node WHERE questionId=?", String.class, questionId);
    }

    // ---------------- tools ----------------

    private static String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String nullIfBlank(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private static String jsonText(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        return node.toString();
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

    private static long longValue(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) throw bad("缺少" + field);
        try {
            return Long.parseLong(node.asText());
        } catch (Exception e) {
            throw bad(field + "无效");
        }
    }

    private static BusinessException bad(String message) {
        return new BusinessException(ErrorCode.PARAMS_ERROR, message);
    }
}
