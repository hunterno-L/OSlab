package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.vo.learning.TutorSourceVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PublishedLearningContentServiceTest {
    static class PublishedDb extends JdbcTemplate {
        String query;
        @Override public List<Map<String,Object>> queryForList(String sql) {
            query=sql;
            return List.of(Map.of("publishedJson","{\"chunkCode\":\"chunk.real\",\"title\":\"真实正文\",\"revision\":3,\"nodeCodes\":[\"os.cpu.fcfs\"],\"contentMd\":\"定义段。\\n\\n调度规则：先来先服务。![图](assets/a.png)\",\"sourceRef\":{\"sourceCode\":\"book\",\"origin\":\"TEXTBOOK\",\"sectionCode\":\"s.2\",\"pageStart\":42,\"locator\":\"2.2.5\"},\"assetBatchCode\":\"batch\",\"assets\":[{\"path\":\"assets/a.png\"}]}"),
                    Map.of("publishedJson","{\"chunkCode\":\"chunk.unrelated\",\"nodeCodes\":[\"os.disk.fcfs\"],\"contentMd\":\"不同领域\"}"));
        }
        @Override public <T> List<T> queryForList(String sql,Class<T> type,Object...args) {
            return Collections.singletonList(type.cast(sql.contains("course_asset")?"9007199254741009":"进程与线程"));
        }
    }
    @Test void readsPublishedSnapshotBindingsAndPreservesCitableSourceAndAssets() {
        PublishedDb db=new PublishedDb();PublishedLearningContentService service=new PublishedLearningContentService();
        ReflectionTestUtils.setField(service,"jdbc",db);
        List<Map<String,Object>> chunks=service.chunksForNodes(List.of("os.cpu.fcfs"));
        assertEquals(1,chunks.size());assertEquals("chunk.real",chunks.get(0).get("chunkCode"));
        assertTrue(db.query.contains("publishedJson IS NOT NULL"));assertFalse(db.query.contains("draftJson"));
        assertTrue(chunks.get(0).get("contentMd").toString().contains("/api/courses/assets/9007199254741009"));
        assertFalse(chunks.get(0).containsKey("assetBatchCode"));
        List<TutorSourceVO> sources=service.sources(List.of("os.cpu.fcfs"),"调度规则",2);
        assertEquals(1,sources.size());TutorSourceVO source=sources.get(0);
        assertEquals("chunk.real",source.getParentChunkCode());assertEquals(3,source.getRevision());
        assertEquals(42,source.getSourceRef().get("pageStart"));assertEquals("2.2.5",source.getSourceRef().get("locator"));
        assertEquals("进程与线程",source.getSourceSectionTitle());assertTrue(source.getSnippet().startsWith("调度规则"));
    }
}
