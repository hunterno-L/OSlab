package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.model.vo.learning.TutorSourceVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;

/** Shared read boundary for textbook evidence: never reads mutable draft mappings or generates prose. */
@Service
public class PublishedLearningContentService {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Resource private JdbcTemplate jdbc;

    public List<Map<String,Object>> chunksForNodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        Set<String> scope = new HashSet<>(codes);
        List<Map<String,Object>> out = new ArrayList<>();
        for (Map<String,Object> row : jdbc.queryForList("SELECT publishedJson FROM learning_content_chunk WHERE publishedJson IS NOT NULL AND isDelete=0 ORDER BY chunkCode")) {
            ObjectNode published = parse(row.get("publishedJson").toString());
            boolean matches = false;
            for (JsonNode code : published.path("nodeCodes")) if (scope.contains(code.asText())) matches = true;
            if (!matches) continue;
            out.add(enrich(published));
        }
        return out;
    }

    /**
     * 某课程全部已发布正文块（AI 联合检索的建索引入口）。
     *
     * <p>发布判定与 {@link #chunksForNodes} 完全一致——只认 publishedJson 非空且未逻辑删除，
     * 因此本服务仍是唯一发布事实源；草稿(draftJson)与下线内容绝不进入检索语料。
     * 课程隔离由 SQL 的 courseCode 条件保证，不依赖调用方自觉。
     */
    public List<Map<String,Object>> publishedChunks(String courseCode) {
        if (courseCode == null || courseCode.trim().isEmpty()) return Collections.emptyList();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Map<String,Object> row : jdbc.queryForList("SELECT publishedJson FROM learning_content_chunk WHERE publishedJson IS NOT NULL AND isDelete=0 AND courseCode=? ORDER BY chunkCode", courseCode)) {
            Object value = row.get("publishedJson");
            if (value == null) continue;
            out.add(enrich(parse(value.toString())));
        }
        return out;
    }

    /** 资产链接改写与小节标题补齐：两个读取入口共用，保证引用出处一致。 */
    private Map<String,Object> enrich(ObjectNode published) {
        String markdown = published.path("contentMd").asText();
        for (JsonNode asset : published.path("assets")) {
            String path = asset.path("path").asText();
            List<String> ids = jdbc.queryForList("SELECT CAST(id AS CHAR) FROM course_asset WHERE assetPath=?", String.class,
                    published.path("assetBatchCode").asText()+"/"+path);
            if (!ids.isEmpty()) {
                String url = "/api/courses/assets/"+ids.get(0);
                ((ObjectNode)asset).put("url",url);
                markdown = markdown.replace("("+path+")", "("+url+")");
            }
        }
        published.put("contentMd",markdown);
        published.remove("assetBatchCode");
        JsonNode source = published.path("sourceRef");
        String section = source.path("sectionCode").asText(published.path("sectionCode").asText());
        List<String> titles = jdbc.queryForList("SELECT title FROM course_section WHERE sectionCode=? AND isDelete=0",String.class,section);
        if (!titles.isEmpty()) published.put("sourceSectionTitle",titles.get(0));
        return JSON.convertValue(published,Map.class);
    }

    /** Retrieval fragments retain their parent block and exact publication/source metadata. */
    public List<TutorSourceVO> sources(Collection<String> codes, String question, int quota) {
        if(quota<=0)return Collections.emptyList();
        List<TutorSourceVO> out = new ArrayList<>();
        for (Map<String,Object> chunk : chunksForNodes(codes)) {
            ObjectNode c = JSON.valueToTree(chunk);
            String markdown = c.path("contentMd").asText();
            String[] paragraphs = markdown.split("\\n\\s*\\n");
            String selected = paragraphs.length == 0 ? markdown : paragraphs[0];
            int best = -1;
            for (String paragraph : paragraphs) {
                int score = 0;
                for (String token : (question==null?"":question).split("[\\s，。？、:：]+")) if (token.length()>1 && paragraph.contains(token)) score++;
                if (score > best) {best=score;selected=paragraph;}
            }
            TutorSourceVO vo = new TutorSourceVO();
            vo.setSourceType("TEXTBOOK");vo.setActionType("RESOURCE");
            vo.setSourceId(c.path("chunkCode").asText());vo.setParentChunkCode(c.path("chunkCode").asText());
            vo.setTitle(c.path("title").asText());vo.setRevision(c.path("revision").asInt());
            vo.setSnippet(selected.length()>900?selected.substring(0,900):selected);
            vo.setSourceRef(JSON.convertValue(c.path("sourceRef"),Map.class));
            vo.setSourceSectionTitle(c.path("sourceSectionTitle").asText(null));
            vo.setTargetId(vo.getParentChunkCode());
            String firstCode = c.path("nodeCodes").path(0).asText();
            vo.setTargetUrl("/knowledge/concept/"+firstCode+"#chunk-"+vo.getParentChunkCode());
            out.add(vo);if(out.size()>=quota)break;
        }
        return out;
    }
    private ObjectNode parse(String value) {
        try {return (ObjectNode)JSON.readTree(value);} catch(Exception e) {throw new IllegalStateException("发布内容快照无效",e);}
    }
}
