package com.yupi.springbootinit.course;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.config.LearningTransactionConfig;
import com.yupi.springbootinit.learning.AssessmentAssetService;
import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.learning.TeachingAssessmentService;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeliveredChaptersImport {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String COURSE = "course.operating-systems";
    private static final List<String> CHAPTERS = Arrays.asList("process", "memory", "files", "io");

    @EnableTransactionManagement
    @Import({LearningTransactionConfig.class, CourseRepository.class, CourseContentService.class,
            CourseGraphSyncService.class, NodeIdentityService.class, TeachingAssessmentService.class,
            AssessmentAssetService.class})
    static class Config {
        @Bean DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl("jdbc:mysql://127.0.0.1:3306/oslab_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
            dataSource.setUsername(requiredEnv("OSLAB_IMPORT_DB_USER"));
            dataSource.setPassword(requiredEnv("OSLAB_IMPORT_DB_PASSWORD"));
            return dataSource;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean(destroyMethod="close") Driver driver() {
            return GraphDatabase.driver("bolt://127.0.0.1:7687",
                    AuthTokens.basic(requiredEnv("OSLAB_IMPORT_NEO4J_USER"), requiredEnv("OSLAB_IMPORT_NEO4J_PASSWORD")));
        }
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null) throw new IllegalArgumentException("需要环境变量 " + key);
        return value;
    }
    private static JsonNode read(Path path) throws Exception { return JSON.readTree(Files.readAllBytes(path)); }
    private static void save(Path path, Object data) throws Exception { JSON.writeValue(path.toFile(), data); }
    private static ObjectNode request(Path packageFile, Path assetsRoot) throws Exception {
        ObjectNode request = JSON.createObjectNode();
        request.set("package", read(packageFile));
        ArrayNode assets = request.putArray("assets");
        if (assetsRoot != null && Files.isDirectory(assetsRoot.resolve("assets"))) {
            try (Stream<Path> files = Files.walk(assetsRoot.resolve("assets"))) {
                for (Path file : files.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                    ObjectNode asset = assets.addObject();
                    asset.put("path", assetsRoot.relativize(file).toString().replace('\\', '/'));
                    asset.put("contentBase64", Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
                    String contentType = Files.probeContentType(file);
                    asset.put("contentType", contentType == null ? "image/png" : contentType);
                }
            }
        }
        return request;
    }
    private static void requireValid(String phase, Map<String,Object> result) {
        System.out.println(phase + " valid=" + result.get("valid") + " created=" + result.get("created")
                + " updated=" + result.get("updated") + " errors=" + result.get("errors"));
        if (!Boolean.TRUE.equals(result.get("valid"))) throw new IllegalStateException(phase + " 预览失败");
    }
    private static void prepareCatalog(JdbcTemplate jdbc, Path root) throws Exception {
        for (JsonNode source : read(root.resolve("sources.json"))) {
            jdbc.update("INSERT IGNORE INTO course_source(id,sourceCode,metadataJson) VALUES(?,?,?)",
                    IdWorker.getId(), source.path("sourceCode").asText(), source.toString());
        }
        JsonNode sections = read(root.resolve("sections.json"));
        Set<String> existing = new HashSet<>(jdbc.queryForList(
                "SELECT sectionCode FROM course_section WHERE courseCode=? AND isDelete=0", String.class, COURSE));
        Set<String> combined = new HashSet<>(existing);
        for (JsonNode section : sections) combined.add(section.path("sectionCode").asText());
        for (JsonNode section : sections) {
            String code = section.path("sectionCode").asText();
            String parent = section.path("parentSectionCode").asText();
            if (!parent.isEmpty() && !combined.contains(parent)) throw new IllegalStateException("未知父目录 " + parent);
            if (existing.contains(code)) continue;
            jdbc.update("INSERT INTO course_section(id,sectionCode,courseCode,parentSectionCode,title,textbookNumber,sectionType,sortOrder,sourceCode,pageStart,metadataJson) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    IdWorker.getId(), code, COURSE, parent.isEmpty() ? null : parent, section.path("title").asText(),
                    section.path("textbookNumber").asText(), section.path("sectionType").asText(), section.path("sortOrder").asInt(),
                    "book.wangdao.os.2025", section.hasNonNull("pageStart") ? section.path("pageStart").asInt() : null, section.toString());
            existing.add(code);
        }
        System.out.println("目录、来源已登记");
    }
    private static void importKnowledge(CourseContentService content, Path root, String chapter, long operator) throws Exception {
        Path file = chapter == null ? root.resolve("reused-nodes.json") : root.resolve(chapter).resolve("knowledge.json");
        Path assets = chapter == null ? null : root.resolve("original").resolve("J-" + chapter);
        String phase = chapter == null ? "reused-nodes" : chapter + "-knowledge";
        if (Files.exists(root.resolve(phase + "-commit.json"))) {
            System.out.println(phase + " 已导入，跳过");
            return;
        }
        ObjectNode request = request(file, assets);
        Map<String,Object> preview = content.preview(request);
        save(root.resolve(phase + "-preview.json"), preview);
        requireValid(phase, preview);
        save(root.resolve(phase + "-commit.json"), content.commit(request, operator));
    }
    private static void importQuestions(TeachingAssessmentService assessments, Path root, String chapter,
                                        String name, long operator) throws Exception {
        if (Files.exists(root.resolve(chapter + "-" + name + "-commit.json"))) {
            System.out.println(chapter + " " + name + " 已导入，跳过");
            return;
        }
        ObjectNode request = request(root.resolve(chapter).resolve(name + ".json"), root.resolve("original").resolve("A-" + chapter));
        Map<String,Object> preview = assessments.preview(request);
        save(root.resolve(chapter + "-" + name + "-preview.json"), preview);
        requireValid(chapter + "-" + name, preview);
        save(root.resolve(chapter + "-" + name + "-commit.json"), assessments.commit(request, operator));
    }
    private static void publishQuestions(TeachingAssessmentService assessments, Path root, String chapter,
                                         String name, long operator, Set<String> unapprovedSources) throws Exception {
        int published = 0, withheld = 0;
        for (JsonNode question : read(root.resolve(chapter).resolve(name + ".json")).path("questions")) {
            if (unapprovedSources.contains(question.path("sourceRef").path("sourceCode").asText())) { withheld++; continue; }
            Map<String,Object> managed = assessments.manage(question.path("questionCode").asText());
            long id = Long.parseLong(managed.get("questionId").toString());
            String status = managed.get("status").toString();
            if ("DRAFT".equals(status) || "REJECTED".equals(status)) assessments.review(id, "SUBMIT", null, operator);
            if ("DRAFT".equals(status) || "REJECTED".equals(status) || "SUBMITTED".equals(status)) assessments.review(id, "APPROVE", null, operator);
            if (!"PUBLISHED".equals(status)) assessments.review(id, "PUBLISH", null, operator);
            published++;
        }
        System.out.println(chapter + " " + name + " 已发布=" + published + " 保留草稿=" + withheld);
    }
    private static void publishContent(CourseContentService content, Path root, String chapter,
                                       String kind, long operator) throws Exception {
        JsonNode pack = read(root.resolve(chapter).resolve("chunks".equals(kind) ? "knowledge.json" : "units.json"));
        String array = "chunks".equals(kind) ? "chunks" : "units";
        String codeKey = "chunks".equals(kind) ? "chunkCode" : "unitCode";
        for (JsonNode item : pack.path(array)) {
            String code = item.path(codeKey).asText();
            String status = content.manage(kind, code).get("status").toString();
            if ("DRAFT".equals(status) || "REJECTED".equals(status)) content.review(kind, code, JSON.createObjectNode().put("action", "SUBMIT"), operator);
            if ("DRAFT".equals(status) || "REJECTED".equals(status) || "SUBMITTED".equals(status)) content.review(kind, code, JSON.createObjectNode().put("action", "APPROVE"), operator);
            if (!"PUBLISHED".equals(status)) content.review(kind, code, JSON.createObjectNode().put("action", "PUBLISH"), operator);
        }
        System.out.println(chapter + " " + kind + " 已发布=" + pack.path(array).size());
    }
    private static Map<String,Object> counts(JdbcTemplate jdbc) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String table : Arrays.asList("course_section", "knowledge_node_registry", "knowledge_node_relation", "learning_content_chunk",
                "learning_question", "learning_unit", "learning_attempt", "learning_event", "learning_mastery", "question", "question_submit")) {
            result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("需要导入目录和现有管理员 ID");
        ((LoggerContext)LoggerFactory.getILoggerFactory()).getLogger("ROOT").setLevel(Level.WARN);
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        long operator = Long.parseLong(args[1]);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND userRole='admin' AND isDelete=0", Long.class, operator) != 1L)
                throw new IllegalArgumentException("现有管理员不存在");
            if (!Files.exists(root.resolve("database-before.json"))) save(root.resolve("database-before.json"), counts(jdbc));
            CourseContentService content = context.getBean(CourseContentService.class);
            CourseGraphSyncService graph = context.getBean(CourseGraphSyncService.class);
            TeachingAssessmentService assessments = context.getBean(TeachingAssessmentService.class);
            Set<String> unapprovedSources = new HashSet<>();
            for (JsonNode source : read(root.resolve("sources.json"))) if (!source.path("authorized").asBoolean()) unapprovedSources.add(source.path("sourceCode").asText());
            prepareCatalog(jdbc, root);
            importKnowledge(content, root, null, operator);
            for (String chapter : CHAPTERS) importKnowledge(content, root, chapter, operator);
            Map<String,Object> graphStatus = graph.syncPending();
            save(root.resolve("graph-sync.json"), graphStatus);
            if (!"SYNCED".equals(graphStatus.get("status"))) throw new IllegalStateException("图谱同步失败: " + graphStatus);
            for (String chapter : CHAPTERS) {
                importQuestions(assessments, root, chapter, "questions", operator);
                importQuestions(assessments, root, chapter, "application-questions", operator);
                publishQuestions(assessments, root, chapter, "questions", operator, unapprovedSources);
                publishQuestions(assessments, root, chapter, "application-questions", operator, unapprovedSources);
                publishContent(content, root, chapter, "chunks", operator);
                if (!Files.exists(root.resolve(chapter + "-units-commit.json"))) {
                    ObjectNode unitRequest = request(root.resolve(chapter).resolve("units.json"), null);
                    Map<String,Object> unitPreview = content.preview(unitRequest);
                    save(root.resolve(chapter + "-units-preview.json"), unitPreview);
                    requireValid(chapter + "-units", unitPreview);
                    save(root.resolve(chapter + "-units-commit.json"), content.commit(unitRequest, operator));
                }
                publishContent(content, root, chapter, "units", operator);
            }
            save(root.resolve("database-after.json"), counts(jdbc));
            System.out.println("四章导入发布完成");
        }
    }
}
