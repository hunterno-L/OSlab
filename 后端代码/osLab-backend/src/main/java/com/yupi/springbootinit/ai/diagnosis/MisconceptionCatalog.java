package com.yupi.springbootinit.ai.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.ai.AiJson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 版本化误区映射（计划 §5.4 / §6.3）。
 *
 * <p>诊断的候选原因<b>只能</b>来自这份人工核验的映射、题目自带误区标注与学生选项，
 * 模型可以在其中排序或补充说明，但不能凭空生成新的"原因代码"——
 * 否则诊断结论无法复核，也无法在答辩时说明依据。
 *
 * <p>课程包提供知识点与区分任务，引擎提供候选原因、证据合并与下一步；
 * 因此新增课程只需追加 JSON 条目，不改引擎。文件缺失或损坏时目录为空，
 * 诊断会诚实返回 INSUFFICIENT，而不是编造原因。
 */
@Slf4j
@Component
public class MisconceptionCatalog {

    private static final String RESOURCE = "ai/misconception-catalog-v1.json";

    @Data
    public static class Misconception {
        private String code;
        private String label;
        private List<String> optionHints = new ArrayList<>();
        private List<String> textHints = new ArrayList<>();
    }

    @Data
    public static class Verification {
        private List<String> discriminates = new ArrayList<>();
        private String predictCase;
        private String reason;
        private int estimatedMinutes = 3;
    }

    @Data
    public static class Skill {
        private String skillCode;
        private String label;
        private List<String> nodeCodes = new ArrayList<>();
        private List<Misconception> misconceptions = new ArrayList<>();
        private List<Verification> verification = new ArrayList<>();
    }

    private String catalogVersion = "unavailable";
    private List<CourseEntry> courses = new ArrayList<>();

    @Data
    private static class CourseEntry {
        private String courseCode;
        private List<Skill> skills = new ArrayList<>();
    }

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            byte[] bytes = readAll(in);
            JsonNode root = AiJson.mapper().readTree(new String(bytes, StandardCharsets.UTF_8));
            catalogVersion = root.path("catalogVersion").asText("unknown");
            List<CourseEntry> parsed = new ArrayList<>();
            for (JsonNode courseNode : root.path("courses")) {
                CourseEntry entry = new CourseEntry();
                entry.setCourseCode(courseNode.path("courseCode").asText(""));
                for (JsonNode skillNode : courseNode.path("skills")) {
                    entry.getSkills().add(AiJson.mapper().treeToValue(skillNode, Skill.class));
                }
                parsed.add(entry);
            }
            this.courses = parsed;
            log.info("[ai-diagnosis] misconception catalog loaded version={} courses={}",
                    catalogVersion, parsed.size());
        } catch (Exception e) {
            this.courses = new ArrayList<>();
            this.catalogVersion = "unavailable";
            log.warn("[ai-diagnosis] misconception catalog unavailable: {}", e.getClass().getSimpleName());
        }
    }

    /** 测试注入 seam。 */
    void loadFrom(String json) {
        try {
            JsonNode root = AiJson.mapper().readTree(json);
            catalogVersion = root.path("catalogVersion").asText("unknown");
            List<CourseEntry> parsed = new ArrayList<>();
            for (JsonNode courseNode : root.path("courses")) {
                CourseEntry entry = new CourseEntry();
                entry.setCourseCode(courseNode.path("courseCode").asText(""));
                for (JsonNode skillNode : courseNode.path("skills")) {
                    entry.getSkills().add(AiJson.mapper().treeToValue(skillNode, Skill.class));
                }
                parsed.add(entry);
            }
            this.courses = parsed;
        } catch (Exception e) {
            this.courses = new ArrayList<>();
        }
    }

    public String version() {
        return catalogVersion;
    }

    public boolean isEmpty() {
        return courses.isEmpty();
    }

    /**
     * 按课程 + 知识点/技能编码匹配技能条目。
     *
     * @param keys 知识点编码、历史节点名或技能编码，任一命中即算匹配
     */
    public List<Skill> match(String courseCode, java.util.Collection<String> keys) {
        if (StringUtils.isBlank(courseCode)) {
            return Collections.emptyList();
        }
        Set<String> wanted = new LinkedHashSet<>();
        if (keys != null) {
            for (String key : keys) {
                if (StringUtils.isNotBlank(key)) {
                    wanted.add(key.trim());
                }
            }
        }
        List<Skill> out = new ArrayList<>();
        for (CourseEntry course : courses) {
            if (!courseCode.equals(course.getCourseCode())) {
                continue;
            }
            for (Skill skill : course.getSkills()) {
                if (wanted.isEmpty() || wanted.contains(skill.getSkillCode())
                        || intersects(skill.getNodeCodes(), wanted)) {
                    out.add(skill);
                }
            }
        }
        return out;
    }

    /** 该课程全部技能（无节点约束时用，例如复习目标的通用诊断）。 */
    public List<Skill> allSkills(String courseCode) {
        return match(courseCode, Collections.emptyList());
    }

    public Misconception find(String courseCode, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        for (Skill skill : allSkills(courseCode)) {
            for (Misconception misconception : skill.getMisconceptions()) {
                if (code.equals(misconception.getCode())) {
                    return misconception;
                }
            }
        }
        return null;
    }

    private static boolean intersects(List<String> a, Set<String> b) {
        if (a == null) {
            return false;
        }
        for (String value : a) {
            if (b.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
