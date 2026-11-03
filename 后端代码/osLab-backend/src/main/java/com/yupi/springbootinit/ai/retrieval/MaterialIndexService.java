package com.yupi.springbootinit.ai.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.AiProperties;
import com.yupi.springbootinit.ai.EmbeddingClient;
import com.yupi.springbootinit.learning.PublishedLearningContentService;
import com.yupi.springbootinit.mapper.AiMaterialChunkMapper;
import com.yupi.springbootinit.mapper.AiMaterialMapper;
import com.yupi.springbootinit.model.entity.AiMaterial;
import com.yupi.springbootinit.model.entity.AiMaterialChunk;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资料索引服务（计划 §5.2）。
 *
 * <p>语料来源两条：
 * <ol>
 *   <li>课程已发布正文——只经 {@link PublishedLearningContentService} 读取，
 *       该服务仍是唯一发布事实源，草稿与下线内容绝不进入索引；</li>
 *   <li>本人授权资料——粘贴文本与 UTF-8 Markdown，按课程隔离、按所有者隔离。</li>
 * </ol>
 *
 * <p>embedding 存 MySQL（模型名、维度、文本哈希、版本、向量），相似度在应用内计算，
 * 不引入 Elasticsearch 或向量数据库。增量重建：文本哈希未变且向量模型一致的块直接跳过。
 *
 * <p>embedding 不可用时索引仍可完成（KEYWORD_ONLY），检索诚实降级为关键词+图谱，
 * 不把降级包装成语义检索。
 */
@Slf4j
@Service
public class MaterialIndexService {

    @Resource
    private PublishedLearningContentService publishedContent;

    @Resource
    private AiMaterialChunkMapper chunkMapper;

    @Resource
    private AiMaterialMapper materialMapper;

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private AiProperties properties;

    /** 一个待索引的段落块（与存储解耦，便于单测）。 */
    @Data
    public static class Block {
        private String blockId;
        private String sourceId;
        private Integer revision;
        private String locator;
        private String title;
        private List<String> nodeCodes = new ArrayList<>();
        private String contentMd;
        private String textHash;
    }

    /** 索引结果：必须能说明"是否真的向量化了"，供界面与演示脚本核对。 */
    @Data
    public static class IndexReport {
        private String courseCode;
        private int blocks;
        private int written;
        private int skipped;
        private int removed;
        private int embedded;
        private String embeddingModel;
        /** true 表示未向量化，检索将走关键词+图谱降级路径。 */
        private boolean degraded;
        private String message;
    }

    // region 课程正文索引

    /**
     * 重建某课程的已发布正文索引（增量）。
     *
     * <p>课程正文块 ownerScope=COURSE、userId=0，对该课程所有学习者可见；
     * 个人资料块 ownerScope=PERSONAL 且严格绑定 userId。
     */
    public IndexReport indexCourse(String courseCode) {
        IndexReport report = new IndexReport();
        report.setCourseCode(courseCode);
        if (StringUtils.isBlank(courseCode)) {
            report.setMessage("courseCode 不能为空");
            report.setDegraded(true);
            return report;
        }
        List<Block> blocks = new ArrayList<>();
        for (Map<String, Object> published : publishedContent.publishedChunks(courseCode)) {
            JsonNode node = AiJson.mapper().valueToTree(published);
            String chunkCode = node.path("chunkCode").asText("");
            if (StringUtils.isBlank(chunkCode)) {
                continue;
            }
            String markdown = node.path("contentMd").asText("");
            String sectionCode = node.path("sourceRef").path("sectionCode")
                    .asText(node.path("sectionCode").asText(""));
            String title = node.path("title").asText("");
            int revision = node.path("revision").asInt(1);
            List<String> nodeCodes = new ArrayList<>();
            for (JsonNode code : node.path("nodeCodes")) {
                if (StringUtils.isNotBlank(code.asText())) {
                    nodeCodes.add(code.asText());
                }
            }
            List<String> paragraphs = TextChunker.chunk(markdown, properties.getRetrieval().getChunkTargetChars());
            for (int i = 0; i < paragraphs.size(); i++) {
                Block block = new Block();
                block.setBlockId(courseCode + "::" + chunkCode + "::p" + i);
                block.setSourceId(chunkCode);
                block.setRevision(revision);
                block.setLocator((StringUtils.isBlank(sectionCode) ? chunkCode : sectionCode)
                        + "#chunk-" + chunkCode + "-p" + i);
                block.setTitle(title);
                block.setNodeCodes(nodeCodes);
                block.setContentMd(paragraphs.get(i));
                block.setTextHash(sha256(paragraphs.get(i) + "|" + revision));
                blocks.add(block);
            }
        }
        List<Map<String, Object>> existing = chunkMapper.selectIndexState(courseCode, 0L, AiMaterial.SCOPE_COURSE);
        applyIndex(report, courseCode, AiMaterial.SCOPE_COURSE, 0L, null, blocks, existing);
        return report;
    }

    // endregion

    // region 个人资料索引

    /**
     * 索引一份本人资料。资料正文哈希未变时跳过重建，
     * 变化时先清掉该资料的旧块再写新块，保证不会残留过期段落。
     */
    public IndexReport indexPersonalMaterial(AiMaterial material) {
        IndexReport report = new IndexReport();
        report.setCourseCode(material.getCourseCode());
        List<String> nodeCodes = AiJson.readList(material.getNodeCodesJson(), String.class);
        List<String> paragraphs = TextChunker.chunk(material.getContentMd(),
                properties.getRetrieval().getChunkTargetChars());
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            Block block = new Block();
            block.setBlockId(material.getCourseCode() + "::material:" + material.getId() + "::p" + i);
            block.setSourceId("material:" + material.getId());
            block.setRevision(material.getRevision());
            block.setLocator("material:" + material.getId() + "#p" + i);
            block.setTitle(material.getTitle());
            block.setNodeCodes(nodeCodes);
            block.setContentMd(paragraphs.get(i));
            block.setTextHash(sha256(paragraphs.get(i) + "|" + material.getRevision()));
            blocks.add(block);
        }
        List<Map<String, Object>> existing =
                chunkMapper.selectMaterialIndexState(material.getId(), material.getUserId());
        applyIndex(report, material.getCourseCode(), AiMaterial.SCOPE_PERSONAL,
                material.getUserId(), material.getId(), blocks, existing);

        material.setChunkCount(blocks.size());
        material.setIndexStatus(report.isDegraded()
                ? AiMaterial.INDEX_KEYWORD_ONLY : AiMaterial.INDEX_EMBEDDED);
        materialMapper.updateById(material);
        return report;
    }

    // endregion

    // region 共用写入

    /**
     * 增量落库 + 向量化。
     *
     * @param materialId 非 null 时按资料维度清理旧块；null 表示课程维度
     */
    private void applyIndex(IndexReport report, String courseCode, String ownerScope, Long userId,
                            Long materialId, List<Block> blocks, List<Map<String, Object>> existing) {
        report.setBlocks(blocks.size());
        Map<String, Map<String, Object>> existingByBlockId = new LinkedHashMap<>();
        for (Map<String, Object> row : existing) {
            existingByBlockId.put(str(row.get("blockId")), row);
        }
        String embeddingModel = embeddingClient.available() ? embeddingClient.model() : null;
        report.setEmbeddingModel(embeddingModel);

        Set<String> keep = new HashSet<>();
        List<Block> needEmbedding = new ArrayList<>();
        List<AiMaterialChunk> needWrite = new ArrayList<>();
        Map<String, AiMaterialChunk> writtenByBlockId = new HashMap<>();

        for (Block block : blocks) {
            keep.add(block.getBlockId());
            Map<String, Object> row = existingByBlockId.get(block.getBlockId());
            boolean sameText = row != null && block.getTextHash().equals(str(row.get("textHash")));
            boolean sameVectorModel = row != null && StringUtils.equals(embeddingModel, str(row.get("embeddingModel")));
            if (sameText && (sameVectorModel || embeddingModel == null)) {
                report.setSkipped(report.getSkipped() + 1);
                if (embeddingModel != null && !sameVectorModel) {
                    // 平台换了 embedding 模型：旧向量必须作废重建，绝不混用
                    needEmbedding.add(block);
                }
                continue;
            }
            AiMaterialChunk chunk = toChunk(block, courseCode, ownerScope, userId, materialId);
            if (row != null) {
                Object id = row.get("id");
                if (id instanceof Number) {
                    chunk.setId(((Number) id).longValue());
                }
            }
            needWrite.add(chunk);
            writtenByBlockId.put(block.getBlockId(), chunk);
            if (embeddingModel != null) {
                needEmbedding.add(block);
            }
        }

        // 清理已下线正文或被编辑掉的段落，避免检索命中过期内容
        int removed = 0;
        List<Long> staleIds = new ArrayList<>();
        for (Map<String, Object> row : existing) {
            if (!keep.contains(str(row.get("blockId"))) && row.get("id") instanceof Number) {
                staleIds.add(((Number) row.get("id")).longValue());
            }
        }
        for (int from = 0; from < staleIds.size(); from += 200) {
            List<Long> batch = staleIds.subList(from, Math.min(from + 200, staleIds.size()));
            removed += chunkMapper.deletePhysicallyByIds(new ArrayList<>(batch));
        }
        report.setRemoved(removed);

        for (AiMaterialChunk chunk : needWrite) {
            if (chunk.getId() == null) {
                insertReplacingStaleRow(chunk);
            } else {
                chunkMapper.updateById(chunk);
            }
            report.setWritten(report.getWritten() + 1);
        }

        if (embeddingModel == null) {
            report.setDegraded(true);
            report.setMessage("未配置 embedding 模型（AI_ARK_EMBEDDING_MODEL），"
                    + "已建关键词与图谱索引，检索将标注 FALLBACK");
            return;
        }
        if (needEmbedding.isEmpty()) {
            report.setMessage("索引已是最新");
            return;
        }
        try {
            List<String> texts = new ArrayList<>(needEmbedding.size());
            for (Block block : needEmbedding) {
                texts.add(block.getTitle() == null ? block.getContentMd()
                        : block.getTitle() + "\n" + block.getContentMd());
            }
            EmbeddingClient.EmbeddingResult result = embeddingClient.embed(texts);
            if (result.getVectors() == null || result.getVectors().size() != needEmbedding.size()) {
                throw new EmbeddingClient.EmbeddingUnavailableException("embedding 结果条数不匹配");
            }
            for (int i = 0; i < needEmbedding.size(); i++) {
                Block block = needEmbedding.get(i);
                float[] vector = result.getVectors().get(i);
                AiMaterialChunk chunk = writtenByBlockId.get(block.getBlockId());
                if (chunk == null) {
                    // 内容未变但需要换向量模型：按 blockId 重新取行再更新
                    chunk = chunkMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query
                            .LambdaQueryWrapper<AiMaterialChunk>()
                            .eq(AiMaterialChunk::getBlockId, block.getBlockId())
                            .last("limit 1"));
                }
                if (chunk == null) {
                    continue;
                }
                chunk.setVectorJson(VectorMath.toJson(vector));
                chunk.setEmbeddingModel(result.getModel());
                chunk.setEmbeddingDims(vector.length);
                chunk.setEmbeddingVersion(chunk.getEmbeddingVersion() == null ? 1 : chunk.getEmbeddingVersion() + 1);
                chunk.setIndexedAt(new Date());
                chunkMapper.updateById(chunk);
                report.setEmbedded(report.getEmbedded() + 1);
            }
            report.setMessage("已向量化 " + report.getEmbedded() + " 个段落块");
        } catch (RuntimeException e) {
            // 索引失败不能连带丢掉关键词/图谱索引：诚实标注降级原因
            log.warn("[ai-index] embedding failed for course={}: {}", courseCode, e.getClass().getSimpleName());
            report.setDegraded(true);
            report.setMessage("embedding 调用失败（" + e.getClass().getSimpleName()
                    + "），已保留关键词与图谱索引，检索将标注 FALLBACK");
        }
    }

    /**
     * 插入新块；若 blockId 唯一索引上残留了历史行（例如早先逻辑删除的同一块），
     * 先物理删除再插入，保证"下线后重新发布"能重建索引而不是直接失败。
     */
    private void insertReplacingStaleRow(AiMaterialChunk chunk) {
        try {
            chunkMapper.insert(chunk);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            chunkMapper.deletePhysicallyByBlockId(chunk.getBlockId());
            chunk.setId(null);
            chunkMapper.insert(chunk);
        }
    }

    private AiMaterialChunk toChunk(Block block, String courseCode, String ownerScope,
                                    Long userId, Long materialId) {
        AiMaterialChunk chunk = new AiMaterialChunk();
        chunk.setBlockId(block.getBlockId());
        chunk.setSourceId(block.getSourceId());
        chunk.setMaterialId(materialId == null ? 0L : materialId);
        chunk.setOwnerScope(ownerScope);
        chunk.setUserId(userId == null ? 0L : userId);
        chunk.setCourseCode(courseCode);
        chunk.setRevision(block.getRevision() == null ? 1 : block.getRevision());
        chunk.setLocator(block.getLocator());
        chunk.setTitle(block.getTitle());
        chunk.setNodeCodesJson(AiJson.write(block.getNodeCodes()));
        chunk.setContentMd(block.getContentMd());
        chunk.setTextHash(block.getTextHash());
        chunk.setEmbeddingVersion(1);
        chunk.setIsDelete(0);
        return chunk;
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("哈希计算失败", e);
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    // endregion
}
