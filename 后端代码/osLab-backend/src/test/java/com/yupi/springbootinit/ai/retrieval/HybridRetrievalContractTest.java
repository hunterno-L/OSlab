package com.yupi.springbootinit.ai.retrieval;

import com.yupi.springbootinit.ai.AiProperties;
import com.yupi.springbootinit.ai.EmbeddingClient;
import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.mapper.AiMaterialChunkMapper;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 联合检索的契约回归（计划 §5.2 / 任务书 §4）。
 *
 * <p>三条硬要求：
 * <ol>
 *   <li>课程与本人范围在召回阶段就限定死——缺少 courseCode/userId 直接拒绝，不返回任何来源；</li>
 *   <li>不同 embedding 模型的向量绝不混用：语义召回只查询与当前模型同名的块，
 *       维度不一致的向量余弦记为 0（等价于不命中），不会产生跨模型的假相似；</li>
 *   <li>语义不可用时如实标注，不把关键词检索包装成语义检索。</li>
 * </ol>
 */
class HybridRetrievalContractTest {

    private final AiMaterialChunkMapper chunkMapper = mock(AiMaterialChunkMapper.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final KnowledgeGraphService graphService = mock(KnowledgeGraphService.class);
    private final NodeIdentityService nodeIdentities = mock(NodeIdentityService.class);
    private final HybridRetrievalService retrieval = new HybridRetrievalService();

    private static final Long USER = 7L;
    private static final String COURSE = "course.operating-systems";
    private static final String MODEL = "doubao-embedding-text-240715";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(retrieval, "chunkMapper", chunkMapper);
        ReflectionTestUtils.setField(retrieval, "embeddingClient", embeddingClient);
        ReflectionTestUtils.setField(retrieval, "properties", new AiProperties());
        ReflectionTestUtils.setField(retrieval, "knowledgeGraphService", graphService);
        ReflectionTestUtils.setField(retrieval, "nodeIdentities", nodeIdentities);
        when(chunkMapper.searchByKeywords(anyString(), anyLong(), anyList(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(chunkMapper.selectVectorCandidates(anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(chunkMapper.selectScopedByBlockIds(anyString(), anyLong(), anyList()))
                .thenReturn(Collections.emptyList());
        when(graphService.getAllRelatedNames(anyString())).thenReturn(Collections.emptyList());
    }

    // region 作用域

    @Test
    void retrievalIsRefusedWithoutCourseScope() {
        HybridRetrievalService.Scope scope = new HybridRetrievalService.Scope();
        scope.setUserId(USER);

        HybridRetrievalService.Result result = retrieval.retrieve(scope, "LRU 换出哪一页");

        assertTrue(result.getSources().isEmpty());
        assertFalse(result.isSemanticUsed());
        assertFalse(result.isComplete());
        assertNotNull(result.getSemanticSkipReason());
        verifyNoInteractions(chunkMapper);
    }

    @Test
    void retrievalIsRefusedWithoutUserScope() {
        HybridRetrievalService.Scope scope = new HybridRetrievalService.Scope();
        scope.setCourseCode(COURSE);

        assertTrue(retrieval.retrieve(scope, "LRU 换出哪一页").getSources().isEmpty());
        verifyNoInteractions(chunkMapper);
    }

    @Test
    void everyRecallQueryCarriesCourseAndOwnerScope() {
        when(embeddingClient.available()).thenReturn(true);
        when(embeddingClient.model()).thenReturn(MODEL);
        when(embeddingClient.embed(anyList())).thenReturn(embedding(3, 1f, 0f, 0f));
        when(chunkMapper.searchByKeywords(anyString(), anyLong(), anyList(), anyInt()))
                .thenReturn(List.of(row("block.1", "LRU 置换", "LRU 命中后更新顺序", MODEL)));
        when(chunkMapper.selectVectorCandidates(anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(vectorRow("block.1", MODEL, 1f, 0f, 0f)));

        retrieval.retrieve(scope(COURSE, USER), "LRU 命中后为什么换出 2");

        verify(chunkMapper).searchByKeywords(eq(COURSE), eq(USER), anyList(), anyInt());
        verify(chunkMapper).selectVectorCandidates(eq(COURSE), eq(USER), eq(MODEL), anyInt());
    }

    @Test
    void anotherCourseNeverLeaksIntoResults() {
        when(embeddingClient.available()).thenReturn(true);
        when(embeddingClient.model()).thenReturn(MODEL);
        when(embeddingClient.embed(anyList())).thenReturn(embedding(3, 1f, 0f, 0f));
        when(chunkMapper.searchByKeywords(eq(COURSE), eq(USER), anyList(), anyInt()))
                .thenReturn(List.of(row("block.1", "LRU 置换", "LRU 命中后更新顺序", MODEL)));
        // 别的课程的块即使被误查出来也不会进入结果：SQL 已按课程过滤，这里再验一次映射
        when(chunkMapper.searchByKeywords(eq("course.other"), eq(USER), anyList(), anyInt()))
                .thenReturn(Collections.emptyList());

        HybridRetrievalService.Result result = retrieval.retrieve(scope(COURSE, USER), "LRU");

        assertEquals(1, result.getSources().size());
        assertEquals(COURSE, result.getSources().get(0).getCourseCode());
        verify(chunkMapper, never()).searchByKeywords(eq("course.other"), anyLong(), anyList(), anyInt());
    }

    // endregion

    // region 向量不混用

    @Test
    void semanticRecallOnlyQueriesChunksOfTheSameEmbeddingModel() {
        when(embeddingClient.available()).thenReturn(true);
        when(embeddingClient.model()).thenReturn(MODEL);
        when(embeddingClient.embed(anyList())).thenReturn(embedding(3, 1f, 0f, 0f));
        when(chunkMapper.selectVectorCandidates(anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(vectorRow("block.same", MODEL, 1f, 0f, 0f)));
        when(chunkMapper.searchByKeywords(anyString(), anyLong(), anyList(), anyInt()))
                .thenReturn(List.of(row("block.same", "LRU 置换", "LRU 命中后更新顺序", MODEL)));

        HybridRetrievalService.Result result = retrieval.retrieve(scope(COURSE, USER), "LRU");

        verify(chunkMapper).selectVectorCandidates(COURSE, USER, MODEL, 200);
        assertTrue(result.isSemanticUsed());
        assertTrue(result.getTrace().toString().contains(MODEL), "轨迹要写明用的是哪个 embedding 模型");
    }

    @Test
    void vectorsOfAnotherModelOrDimensionNeverProduceSimilarity() {
        float[] query = {1f, 0f, 0f};
        // 维度不同的向量：余弦记 0，等价于不命中，不会算出跨模型的假相似度
        assertEquals(0.0, VectorMath.cosine(query, new float[]{1f, 0f}), 1e-9);
        assertEquals(0.0, VectorMath.cosine(query, new float[]{1f, 0f, 0f, 0f}), 1e-9);
        assertEquals(0.0, VectorMath.cosine(query, null), 1e-9);
        assertEquals(0.0, VectorMath.cosine(query, new float[]{0f, 0f, 0f}), 1e-9);
        assertEquals(1.0, VectorMath.cosine(query, new float[]{2f, 0f, 0f}), 1e-9, "同向向量余弦为 1");
        assertEquals(0.0, VectorMath.cosine(query, new float[]{0f, 1f, 0f}), 1e-9, "正交向量余弦为 0");
    }

    @Test
    void vectorJsonRoundTripsAndGarbageIsTreatedAsNoVector() {
        float[] vector = {0.25f, -0.5f, 1f};
        assertArrayEquals(vector, VectorMath.parse(VectorMath.toJson(vector)), 1e-6f);
        assertNull(VectorMath.parse(null));
        assertNull(VectorMath.parse("not-json"));
        assertNull(VectorMath.parse("[]"));
        assertNull(VectorMath.toJson(null));
        // 解析失败等价于"没有向量"，余弦为 0，不会产生假命中
        assertEquals(0.0, VectorMath.cosine(vector, VectorMath.parse("not-json")), 1e-9);
    }

    @Test
    void embeddingFailureDegradesToKeywordAndGraphAndSaysSo() {
        when(embeddingClient.available()).thenReturn(true);
        when(embeddingClient.model()).thenReturn(MODEL);
        when(embeddingClient.embed(anyList())).thenThrow(
                new EmbeddingClient.EmbeddingUnavailableException("embedding 调用失败：鉴权失败"));
        when(chunkMapper.searchByKeywords(anyString(), anyLong(), anyList(), anyInt()))
                .thenReturn(List.of(row("block.1", "LRU 置换", "LRU 命中后更新顺序", MODEL)));

        HybridRetrievalService.Result result = retrieval.retrieve(scope(COURSE, USER), "LRU");

        assertFalse(result.isSemanticUsed());
        assertFalse(result.isComplete(), "语义不可用时检索不完整，编排器据此标 FALLBACK");
        assertTrue(result.getSemanticSkipReason().contains("embedding"));
        assertEquals(1, result.getSources().size(), "关键词召回仍然要给出真实来源");
        verify(chunkMapper, never()).selectVectorCandidates(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    void missingEmbeddingModelIsReportedWithTheExactEnvironmentVariable() {
        when(embeddingClient.available()).thenReturn(false);
        when(embeddingClient.model()).thenReturn(null);
        when(chunkMapper.searchByKeywords(anyString(), anyLong(), anyList(), anyInt()))
                .thenReturn(List.of(row("block.1", "LRU 置换", "LRU 命中后更新顺序", MODEL)));

        HybridRetrievalService.Result result = retrieval.retrieve(scope(COURSE, USER), "LRU");

        assertFalse(result.isSemanticUsed());
        assertTrue(result.getSemanticSkipReason().contains("AI_ARK_EMBEDDING_MODEL"),
                "降级原因要直接指出缺哪个环境变量，便于演示排障");
    }

    // endregion

    // region 来源定位

    @Test
    void sourceRefsKeepRevisionLocatorAndRealExcerpt() {
        when(chunkMapper.searchByKeywords(anyString(), anyLong(), anyList(), anyInt()))
                .thenReturn(List.of(row("block.1", "LRU 置换", "LRU 命中后必须把该页移到最近使用端。", MODEL)));

        HybridRetrievalService.Result result = retrieval.retrieve(scope(COURSE, USER), "LRU 命中");

        assertEquals(1, result.getSources().size());
        SourceRef ref = result.getSources().get(0);
        assertEquals("block.1", ref.getBlockId());
        assertEquals("chunk.lru", ref.getSourceId());
        assertEquals("COURSE", ref.getOwnerScope());
        assertEquals(COURSE, ref.getCourseCode());
        assertEquals(2, ref.getRevision());
        assertEquals("2.2.5", ref.getLocator());
        assertTrue(ref.getExcerpt().contains("LRU"), "摘录必须是原文片段，不能改写");
    }

    @Test
    void userSelectedSourcesAlwaysRankFirst() {
        when(chunkMapper.searchByKeywords(anyString(), anyLong(), anyList(), anyInt()))
                .thenReturn(Arrays.asList(
                        row("block.1", "LRU 置换", "LRU 命中后更新顺序", MODEL),
                        row("block.2", "FIFO 置换", "FIFO 按装入顺序换出", MODEL)));
        HybridRetrievalService.Scope scope = scope(COURSE, USER);
        scope.setSelectedSourceIds(List.of("block.2"));

        HybridRetrievalService.Result result = retrieval.retrieve(scope, "LRU 命中");

        assertEquals("block.2", result.getSources().get(0).getBlockId(),
                "用户选中的段落必须固定优先，不能被排序挤掉");
    }

    // endregion

    // region 切块与分词

    @Test
    void chunkerKeepsParagraphsTogetherAndSplitsOverlongOnes() {
        String markdown = "第一段讲 LRU。\n\n第二段讲 FIFO。\n\n"
                + "第三段很长。" + "补一句。".repeat(200);
        List<String> chunks = TextChunker.chunk(markdown, 200);

        assertFalse(chunks.isEmpty());
        for (String chunk : chunks) {
            assertFalse(chunk.isBlank());
            assertTrue(chunk.length() <= 900, "单块不应失控增长：" + chunk.length());
        }
        assertTrue(chunks.get(0).contains("LRU"));
        // 内容不丢：所有块拼回去应覆盖原文的全部非空白字符数
        int total = chunks.stream().mapToInt(String::length).sum();
        assertTrue(total >= markdown.replaceAll("\\s", "").length(),
                "切块不得丢正文");
    }

    @Test
    void chunkerHandlesBlankInput() {
        assertTrue(TextChunker.chunk(null, 700).isEmpty());
        assertTrue(TextChunker.chunk("   ", 700).isEmpty());
    }

    @Test
    void tokensCoverBothEnglishWordsAndChineseBigrams() {
        List<String> tokens = TextChunker.tokens("LRU 页面置换算法", 10);
        // 英文按词保留原样：MySQL 默认排序规则不区分大小写，LIKE 召回不受影响
        assertTrue(tokens.stream().anyMatch(token -> token.equalsIgnoreCase("lru")),
                "英文应按词切分，实际：" + tokens);
        assertTrue(tokens.stream().anyMatch(token -> token.contains("页面")),
                "中文按二字组切分，否则关键词召回命中率极低");
        assertTrue(tokens.size() <= 10);
        assertTrue(TextChunker.tokens(null, 10).isEmpty());
        assertTrue(TextChunker.tokens("   ", 10).isEmpty());
        assertEquals(1, TextChunker.tokens("LRU", 10).size(), "单字符以下不成词，避免噪音召回");
    }

    // endregion

    // region fixtures

    private HybridRetrievalService.Scope scope(String courseCode, Long userId) {
        HybridRetrievalService.Scope scope = new HybridRetrievalService.Scope();
        scope.setCourseCode(courseCode);
        scope.setUserId(userId);
        scope.setNodeCodes(List.of("os.memory.lru"));
        return scope;
    }

    private Map<String, Object> row(String blockId, String title, String content, String embeddingModel) {
        Map<String, Object> row = new HashMap<>();
        row.put("blockId", blockId);
        row.put("sourceId", "chunk.lru");
        row.put("ownerScope", "COURSE");
        row.put("courseCode", COURSE);
        row.put("revision", 2);
        row.put("locator", "2.2.5");
        row.put("title", title);
        row.put("contentMd", content);
        row.put("nodeCodesJson", "[\"os.memory.lru\"]");
        row.put("embeddingModel", embeddingModel);
        return row;
    }

    private Map<String, Object> vectorRow(String blockId, String embeddingModel, float... vector) {
        Map<String, Object> row = row(blockId, "LRU 置换", "LRU 命中后更新顺序", embeddingModel);
        row.put("vectorJson", VectorMath.toJson(vector));
        return row;
    }

    private EmbeddingClient.EmbeddingResult embedding(int dims, float... vector) {
        EmbeddingClient.EmbeddingResult result = new EmbeddingClient.EmbeddingResult();
        result.setModel(MODEL);
        result.setDims(dims);
        result.setVectors(List.of(vector));
        return result;
    }

    // endregion
}
