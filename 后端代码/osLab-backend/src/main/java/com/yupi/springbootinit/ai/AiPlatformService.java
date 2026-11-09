package com.yupi.springbootinit.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 平台骨架：统一会话、检索、工具轨迹、个人模型配置、下一步计划与诊断。
 * 没有配置模型 key 时走确定性降级，不伪造语义检索。
 */
@Service
public class AiPlatformService {

    @Resource
    private JdbcTemplate jdbc;

    @Resource
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${DEEPSEEK_API_KEY:}")
    private String envDeepseekKey;

    @org.springframework.beans.factory.annotation.Value("${AI_ARK_API_KEY:}")
    private String envArkKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicLong idGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    private long nextId() {
        return System.currentTimeMillis() * 1000L + (idGen.incrementAndGet() % 1000);
    }

    // ---------- 会话 ----------

    public Map<String, Object> startSession(long userId, Map<String, Object> body) {
        String sessionId = UUID.randomUUID().toString();
        String courseCode = str(body.get("courseCode"));
        String unitCode = str(body.get("unitCode"));
        String nodeCodes = body.get("nodeCodes") == null ? null : objectMapper.valueToTree(body.get("nodeCodes")).toString();
        String goal = StringUtils.hasText(str(body.get("goal"))) ? str(body.get("goal")) : "LEARN";
        Integer minutes = body.get("timeBudgetMinutes") == null ? null : Integer.valueOf(String.valueOf(body.get("timeBudgetMinutes")));
        jdbc.update("INSERT INTO ai_session (id,sessionId,userId,courseCode,unitCode,nodeCodes,goal,timeBudgetMinutes) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                nextId(), sessionId, userId, courseCode, unitCode, nodeCodes, goal, minutes);
        return getSession(userId, sessionId);
    }

    public Map<String, Object> getSession(long userId, String sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ai_session WHERE sessionId=? AND userId=? AND isDelete=0", sessionId, userId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        if (row.get("nodeCodes") != null) {
            try {
                row.put("nodeCodes", objectMapper.readValue(String.valueOf(row.get("nodeCodes")), Object.class));
            } catch (Exception ignored) {
                // 保留原始字符串
            }
        }
        return row;
    }

    // ---------- 模型配置 ----------

    public Map<String, Object> getConfig(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ai_user_config WHERE userId=?", userId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            result.put("configured", false);
            result.put("baseUrl", "https://ark.cn-beijing.volces.com/api/v3");
            result.put("model", "deepseek-v3-250324");
            result.put("apiKeyMasked", "");
            return result;
        }
        Map<String, Object> row = rows.get(0);
        String key = str(row.get("apiKey"));
        result.put("configured", StringUtils.hasText(key));
        result.put("baseUrl", row.get("baseUrl"));
        result.put("model", row.get("model"));
        result.put("embeddingModel", row.get("embeddingModel"));
        result.put("apiKeyMasked", StringUtils.hasText(key)
                ? key.substring(0, Math.min(4, key.length())) + "****" + key.substring(Math.max(0, key.length() - 2))
                : "");
        return result;
    }

    public Map<String, Object> saveConfig(long userId, Map<String, Object> body) {
        String baseUrl = StringUtils.hasText(str(body.get("baseUrl")))
                ? str(body.get("baseUrl")) : "https://ark.cn-beijing.volces.com/api/v3";
        String model = StringUtils.hasText(str(body.get("model"))) ? str(body.get("model")) : "deepseek-v3-250324";
        String key = str(body.get("apiKey"));
        String embedding = str(body.get("embeddingModel"));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_user_config WHERE userId=?", Integer.class, userId);
        if (count != null && count > 0) {
            if (StringUtils.hasText(key)) {
                jdbc.update("UPDATE ai_user_config SET baseUrl=?,apiKey=?,model=?,embeddingModel=? WHERE userId=?",
                        baseUrl, key, model, embedding, userId);
            } else {
                jdbc.update("UPDATE ai_user_config SET baseUrl=?,model=?,embeddingModel=? WHERE userId=?",
                        baseUrl, model, embedding, userId);
            }
        } else {
            jdbc.update("INSERT INTO ai_user_config (id,userId,baseUrl,apiKey,model,embeddingModel) VALUES (?,?,?,?,?,?)",
                    nextId(), userId, baseUrl, key, model, embedding);
        }
        return getConfig(userId);
    }

    public Map<String, Object> testConfig(long userId) {
        Map<String, Object> cfg = rawConfig(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (cfg == null || !StringUtils.hasText(str(cfg.get("apiKey")))) {
            result.put("ok", false);
            result.put("reason", "未配置 API Key（当前为降级模式）");
            return result;
        }
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", cfg.get("model"));
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(msg("user", "只回复 ok"));
            payload.put("messages", messages);
            payload.put("max_tokens", 8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(str(cfg.get("apiKey")));
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    trimRight(str(cfg.get("baseUrl"))) + "/chat/completions",
                    new HttpEntity<>(objectMapper.writeValueAsString(payload), headers), String.class);
            result.put("ok", resp.getStatusCode().is2xxSuccessful());
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("status", resp.getStatusCodeValue());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("reason", e.getMessage() == null ? "调用失败" : e.getMessage());
        }
        return result;
    }

    private Map<String, Object> rawConfig(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ai_user_config WHERE userId=?", userId);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        // 未保存个人配置时回落到进程环境（填充式：有 key 自动启用）
        Map<String, Object> env = new LinkedHashMap<>();
        String deepseek = envDeepseekKey;
        String ark = envArkKey;
        if (StringUtils.hasText(deepseek)) {
            env.put("baseUrl", "https://api.deepseek.com/v1");
            env.put("apiKey", deepseek);
            env.put("model", "deepseek-chat");
        } else if (StringUtils.hasText(ark)) {
            env.put("baseUrl", "https://ark.cn-beijing.volces.com/api/v3");
            env.put("apiKey", ark);
            env.put("model", "deepseek-v3-250324");
        } else {
            return null;
        }
        env.put("source", "ENV");
        return env;
    }

    // ---------- 问答 ----------

    public Map<String, Object> ask(long userId, Map<String, Object> body) {
        String sessionId = str(body.get("sessionId"));
        String question = str(body.get("question"));
        Map<String, Object> session = StringUtils.hasText(sessionId) ? getSession(userId, sessionId) : null;
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("问题不能为空");
        }
        String courseCode = session == null ? str(body.get("courseCode")) : str(session.get("courseCode"));
        String nodeCode = session == null ? null : firstNode(session.get("nodeCodes"));

        List<Map<String, Object>> tools = new ArrayList<>();
        List<Map<String, Object>> sources = retrieve(courseCode, question, nodeCode, 5);
        tools.add(tool("search_material", Map.of("query", question, "courseCode", courseCode == null ? "" : courseCode),
                sources.size()));
        Map<String, Object> evidence = evidence(userId, courseCode, nodeCode);
        tools.add(tool("read_evidence", Map.of("nodeCode", nodeCode == null ? "" : nodeCode),
                ((Number) evidence.getOrDefault("totalAttempts", 0)).intValue()));

        Map<String, Object> verify = verifyPageReplacement(question);
        if (verify != null) {
            tools.add(tool("verify_page_replacement", verify, 1));
        }

        Map<String, Object> cfg = rawConfig(userId);
        String answer;
        String mode;
        if (cfg != null && StringUtils.hasText(str(cfg.get("apiKey")))) {
            try {
                answer = modelAnswer(cfg, question, sources, evidence, verify);
                mode = "MODEL";
            } catch (Exception e) {
                answer = degradedAnswer(question, sources, evidence, verify, "模型调用失败：" + e.getMessage());
                mode = "DEGRADED";
            }
        } else {
            answer = degradedAnswer(question, sources, evidence, verify, null);
            mode = "DEGRADED";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("mode", mode);
        result.put("answer", answer);
        result.put("sources", sources);
        result.put("tools", tools);
        result.put("evidence", evidence);
        return result;
    }

    private List<Map<String, Object>> retrieve(String courseCode, String question, String nodeCode, int limit) {
        List<String> keywords = keywords(question);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (courseCode != null && !keywords.isEmpty()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT chunkCode,courseCode,sectionCode,title,contentMd FROM learning_content_chunk "
                            + "WHERE reviewStatus='PUBLISHED' AND isDelete=0 AND courseCode=? AND (1=0");
            List<Object> args = new ArrayList<>();
            args.add(courseCode);
            for (int i = 0; i < keywords.size(); i++) {
                sql.append(" OR title LIKE ? OR contentMd LIKE ?");
                String like = "%" + keywords.get(i) + "%";
                args.add(like);
                args.add(like);
            }
            sql.append(") LIMIT ?");
            args.add(limit);
            rows = jdbc.queryForList(sql.toString(), args.toArray());
        }
        if (rows.isEmpty() && courseCode != null) {
            rows = jdbc.queryForList("SELECT chunkCode,courseCode,sectionCode,title,contentMd FROM learning_content_chunk "
                    + "WHERE reviewStatus='PUBLISHED' AND isDelete=0 AND courseCode=? LIMIT ?", courseCode, limit);
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> src = new LinkedHashMap<>();
            src.put("sourceId", row.get("chunkCode"));
            src.put("title", row.get("title"));
            src.put("sectionCode", row.get("sectionCode"));
            src.put("snippet", snippet(str(row.get("contentMd")), 160));
            sources.add(src);
        }
        return sources;
    }

    /** 中文按 2-3 字切片、英文按词，最多 6 个关键词。 */
    private List<String> keywords(String question) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(question)) {
            return result;
        }
        String text = question.replaceAll("[\\p{Punct}\\s，。？！、；：“”‘’（）《》【】…]+", " ").trim();
        for (String token : text.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.chars().allMatch(c -> c < 128)) {
                if (token.length() >= 2 && !result.contains(token)) {
                    result.add(token);
                }
                continue;
            }
            for (int size = 3; size >= 2; size--) {
                for (int i = 0; i + size <= token.length(); i++) {
                    String piece = token.substring(i, i + size);
                    if (!result.contains(piece)) {
                        result.add(piece);
                    }
                    if (result.size() >= 6) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Object> evidence(long userId, String courseCode, String nodeCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        String base = "SELECT COUNT(*) c, SUM(CASE WHEN a.isCorrect=1 THEN 1 ELSE 0 END) ok FROM learning_attempt a "
                + "JOIN learning_question q ON q.id=a.questionId WHERE a.userId=?";
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String sql = base;
        if (nodeCode != null) {
            sql += " AND EXISTS (SELECT 1 FROM learning_question_node n WHERE n.questionId=a.questionId AND n.nodeCode=?)";
            args.add(nodeCode);
        }
        Map<String, Object> row = jdbc.queryForMap(sql, args.toArray());
        int total = ((Number) row.getOrDefault("c", 0)).intValue();
        int ok = row.get("ok") == null ? 0 : ((Number) row.get("ok")).intValue();
        result.put("totalAttempts", total);
        result.put("correct", ok);
        result.put("needsReview", Math.max(0, total - ok));
        return result;
    }

    private String degradedAnswer(String question, List<Map<String, Object>> sources,
                                  Map<String, Object> evidence, Map<String, Object> verify, String note) {
        StringBuilder sb = new StringBuilder();
        if (note != null) {
            sb.append("（").append(note).append("）\n");
        }
        sb.append("当前为降级模式：基于课程已发布资料与你的作答记录回答，未调用大模型。\n\n");
        if (!sources.isEmpty()) {
            sb.append("相关教材段落：\n");
            for (int i = 0; i < Math.min(3, sources.size()); i++) {
                sb.append(i + 1).append(". ").append(sources.get(i).get("title")).append("：")
                        .append(sources.get(i).get("snippet")).append("\n");
            }
        } else {
            sb.append("没有检索到直接相关的教材段落，建议换一个更具体的问法或先看当前节点知识内容。\n");
        }
        sb.append("\n你的记录：共作答 ").append(evidence.get("totalAttempts"))
                .append(" 次，答对 ").append(evidence.get("correct"))
                .append(" 次，待复习 ").append(evidence.get("needsReview")).append(" 题。\n");
        if (verify != null) {
            sb.append("\n确定性核验：").append(verify.get("summary")).append("\n");
        }
        sb.append("\n配置个人模型 Key 后可获得完整解释（设置 → AI 模型）。");
        return sb.toString();
    }

    private String modelAnswer(Map<String, Object> cfg, String question, List<Map<String, Object>> sources,
                               Map<String, Object> evidence, Map<String, Object> verify) throws Exception {
        StringBuilder context = new StringBuilder();
        for (Map<String, Object> src : sources) {
            context.append("[").append(src.get("sourceId")).append("] ")
                    .append(src.get("title")).append("：").append(src.get("snippet")).append("\n");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", cfg.get("model"));
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(msg("system", "你是操作系统课程导师。只依据给定资料与学习记录回答；"
                + "引用资料时标注 sourceId；证据不足要说明。资料：\n" + context
                + "\n学习记录：" + evidence));
        messages.add(msg("user", question));
        payload.put("messages", messages);
        payload.put("temperature", 0.3);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(str(cfg.get("apiKey")));
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                trimRight(str(cfg.get("baseUrl"))) + "/chat/completions",
                new HttpEntity<>(objectMapper.writeValueAsString(payload), headers), Map.class);
        Map body = resp.getBody();
        if (body == null) {
            throw new IllegalStateException("空响应");
        }
        List choices = (List) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("无 choices");
        }
        Map first = (Map) choices.get(0);
        Map message = (Map) first.get("message");
        return String.valueOf(message.get("content"));
    }

    // ---------- 下一步计划 ----------

    public Map<String, Object> plan(long userId, Map<String, Object> body) {
        String sessionId = str(body.get("sessionId"));
        Map<String, Object> session = StringUtils.hasText(sessionId) ? getSession(userId, sessionId) : null;
        String courseCode = session == null ? str(body.get("courseCode")) : str(session.get("courseCode"));
        String unitCode = session == null ? null : str(session.get("unitCode"));

        int wrong = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_attempt a WHERE a.userId=? AND a.isCorrect=0",
                Integer.class, userId);
        int due = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_attempt a WHERE a.userId=? AND a.isCorrect=0 "
                        + "AND a.createTime > DATE_SUB(NOW(), INTERVAL 7 DAY)",
                Integer.class, userId);

        List<Map<String, Object>> tasks = new ArrayList<>();
        if (wrong > 0) {
            tasks.add(task("复习最近错题", "你近 7 天有 " + due + " 次错误作答，共 " + wrong + " 条待复习记录",
                    "REVIEW", "/assessments?courseCode=" + nvl(courseCode) + "&studyState=NEEDS_REVIEW", wrong));
        }
        if (unitCode != null) {
            tasks.add(task("继续当前单元", "会话定位在 " + unitCode + "，先完成本单元剩余活动",
                    "LEARN", "/learning/units/" + unitCode, 0));
        } else {
            List<Map<String, Object>> units = jdbc.queryForList(
                    "SELECT unitCode,title FROM learning_unit WHERE courseCode=? AND reviewStatus='PUBLISHED' "
                            + "AND isDelete=0 ORDER BY sortOrder LIMIT 1", courseCode);
            if (!units.isEmpty()) {
                tasks.add(task("开始第一个单元", "还没有定位单元，从「" + units.get(0).get("title") + "」开始",
                        "LEARN", "/learning/units/" + units.get(0).get("unitCode"), 0));
            }
        }
        tasks.add(task("做一组 5 题测评", "用一次短测评确认当前掌握度，结果会进入学习记录",
                "ASSESS", "/assessments?courseCode=" + nvl(courseCode) + "&count=5", 0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseCode", courseCode);
        result.put("goal", session == null ? "LEARN" : session.get("goal"));
        result.put("tasks", tasks);
        return result;
    }

    // ---------- 薄弱点诊断 ----------

    public Map<String, Object> diagnose(long userId, String nodeCode, String courseCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT q.misconception, COUNT(*) c FROM learning_attempt a "
                        + "JOIN learning_question q ON q.id=a.questionId "
                        + "JOIN learning_question_node n ON n.questionId=q.id "
                        + "WHERE a.userId=? AND a.isCorrect=0 AND n.nodeCode=? "
                        + "AND q.misconception IS NOT NULL AND q.misconception<>'' "
                        + "GROUP BY q.misconception ORDER BY c DESC LIMIT 3", userId, nodeCode);
        List<Map<String, Object>> causes = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cause", row.get("misconception"));
            item.put("count", row.get("c"));
            item.put("confidence", "INFERRED");
            causes.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeCode", nodeCode);
        result.put("causes", causes);
        result.put("note", causes.isEmpty()
                ? "没有足够错误证据，先完成 1–3 道短题再判断薄弱点。"
                : "以上原因由历史错题误区推断，建议用一道新题验证。");
        return result;
    }

    public void recordHelp(long userId, Map<String, Object> body) {
        String sessionId = str(body.get("sessionId"));
        String type = str(body.get("eventType"));
        Long questionId = body.get("questionId") == null ? null : Long.valueOf(String.valueOf(body.get("questionId")));
        String payload = body.get("payload") == null ? null : objectMapper.valueToTree(body.get("payload")).toString();
        jdbc.update("INSERT INTO ai_help_event (id,sessionId,userId,eventType,questionId,payloadJson) VALUES (?,?,?,?,?,?)",
                nextId(), sessionId, userId, type, questionId, payload);
    }

    // ---------- 工具：页面置换核验 ----------

    private Map<String, Object> verifyPageReplacement(String question) {
        if (question == null || !(question.contains("缺页") || question.contains("置换") || question.contains("页面"))) {
            return null;
        }
        Matcher frameMatcher = Pattern.compile("(\\d+)\\s*个?页框").matcher(question);
        if (!frameMatcher.find()) {
            return null;
        }
        int frames = Integer.parseInt(frameMatcher.group(1));
        if (frames < 1 || frames > 8) {
            return null;
        }
        Matcher seq = Pattern.compile("((?:\\d+[\\s,，、]+){2,}\\d+)").matcher(question);
        if (!seq.find()) {
            return null;
        }
        List<Integer> pages = new ArrayList<>();
        for (String token : seq.group(1).split("[\\s,，、]+")) {
            if (!token.isEmpty()) {
                pages.add(Integer.parseInt(token));
            }
        }
        if (pages.size() < 3) {
            return null;
        }
        int faults = fifoFaults(frames, pages);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frames", frames);
        result.put("pages", pages);
        result.put("fifoFaults", faults);
        result.put("summary", "按 FIFO、" + frames + " 个页框计算，" + pages.size() + " 次访问缺页 " + faults + " 次");
        return result;
    }

    private int fifoFaults(int frames, List<Integer> pages) {
        List<Integer> slots = new ArrayList<>();
        int faults = 0;
        for (Integer page : pages) {
            if (slots.contains(page)) {
                continue;
            }
            faults++;
            if (slots.size() < frames) {
                slots.add(page);
            } else {
                slots.remove(0);
                slots.add(page);
            }
        }
        return faults;
    }

    // ---------- 小工具 ----------

    private Map<String, Object> tool(String name, Map<String, Object> args, int resultCount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tool", name);
        item.put("args", args);
        item.put("resultCount", resultCount);
        return item;
    }

    private Map<String, Object> task(String title, String reason, String type, String link, int evidenceCount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("reason", reason);
        item.put("type", type);
        item.put("link", link);
        item.put("evidenceCount", evidenceCount);
        return item;
    }

    private Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private String firstNode(Object nodeCodes) {
        if (nodeCodes instanceof List && !((List<?>) nodeCodes).isEmpty()) {
            return String.valueOf(((List<?>) nodeCodes).get(0));
        }
        if (nodeCodes instanceof String && StringUtils.hasText((String) nodeCodes)) {
            return (String) nodeCodes;
        }
        return null;
    }

    private String snippet(String md, int size) {
        if (md == null) {
            return "";
        }
        String text = md.replaceAll("[#>*_`\\-]+", " ").replaceAll("\\s+", " ").trim();
        return text.length() <= size ? text : text.substring(0, size) + "…";
    }

    private String trimRight(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
