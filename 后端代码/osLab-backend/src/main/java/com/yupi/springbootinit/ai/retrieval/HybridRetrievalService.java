package com.yupi.springbootinit.ai.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.AiProperties;
import com.yupi.springbootinit.ai.EmbeddingClient;
import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.mapper.AiMaterialChunkMapper;
import com.yupi.springbootinit.model.entity.AiMaterial;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 语义 + 关键词 + 图谱联合检索（计划 §5.2）。
 *
 * <p>执行顺序是"先限制课程与权限，再召回和排序"：
 * 每条 SQL 都带 courseCode 与所有者条件，因此跨课程与跨用户的内容在召回阶段就不可能出现。
 *
 * <p>三路召回：
 * <ol>
 *   <li>语义——查询向量化后与同 embedding 模型的块算余弦，模型不一致的块不参与；</li>
 *   <li>关键词——SQL LIKE 预过滤 + 应用内 token 重叠度打分，中文额外切二字组；</li>
 *   <li>图谱——当前节点与其一跳前置/相关节点作为约束，命中的块加权，
 *       在给定节点约束下完全不沾边的块降权。</li>
 * </ol>
 * 合并去重后按综合分排序，用户选中的段落固定优先。
 *
 * <p>embedding 不可用时只走关键词+图谱，并把 {@code semanticUsed=false} 与原因写进轨迹，
 * 由编排器把整轮 mode 标成 FALLBACK——不把降级路径包装成语义检索。
 */
@Slf4j
@Service
public class HybridRetrievalService {

    /** 综合分权重：可配置启发规则，不宣称是训练出的最优排序。 */
    private static final double W_SEMANTIC = 0.60;
    private static final double W_KEYWORD = 0.40;
    private static final double W_GRAPH_HIT = 0.25;
    private static final double W_GRAPH_MISS = 0.20;
    private static final double W_SELECTED = 1.00;
    private static final double W_TITLE_HIT = 0.10;
    private static final int EXCERPT_CHARS = 500;
    private static final int MAX_QUERY_TOKENS = 10;

    @Resource
    private AiMaterialChunkMapper chunkMapper;

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private AiProperties properties;

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Resource
    private NodeIdentityService nodeIdentities;

    /** 检索范围：课程 + 本人 + 图谱节点约束。 */
    @Data
    public static class Scope {
        private Long userId;
        private String courseCode;
        private List<String> nodeCodes = new ArrayList<>();
        private List<String> selectedSourceIds = new ArrayList<>();
        /** false 时只用课程正文（例如教师视角或明确排除个人资料）。 */
        private boolean includePersonal = true;
    }

    /** 检索结果：来源 + 是否真的用上了语义 + 可检查的轨迹。 */
    @Data
    public static class Result {
        private List<SourceRef> sources = new ArrayList<>();
        private boolean semanticUsed;
        private String semanticSkipReason;
        private boolean graphUsed;
        private int keywordCandidates;
        private int semanticCandidates;
        private List<String> trace = new ArrayList<>();

        /** 检索自身是否完整（语义可用）。编排器据此决定整轮 mode。 */
        public boolean isComplete() {
            return semanticUsed;
        }
    }

    public Result retrieve(Scope scope, String query) {
        Result result = new Result();
        if (scope == null || StringUtils.isBlank(scope.getCourseCode()) || scope.getUserId() == null) {
            result.setSemanticSkipReason("缺少课程或用户范围");
            result.getTrace().add("检索被拒绝：必须先限定课程与本人范围");
            return result;
        }
        AiProperties.Retrieval config = properties.getRetrieval();
        String courseCode = scope.getCourseCode();

        Set<String> graphNodes = expandGraphNodes(scope.getNodeCodes(), result);
        boolean constrained = !scope.getNodeCodes().isEmpty();

        // 1. 关键词召回
        List<String> tokens = TextChunker.tokens(query, MAX_QUERY_TOKENS);
        List<Map<String, Object>> keywordRows = chunkMapper.searchByKeywords(courseCode, scope.getUserId(),
                tokens, config.getKeywordCandidates());
        result.setKeywordCandidates(keywordRows.size());
        result.getTrace().add("关键词召回 " + keywordRows.size() + " 个候选块（token "
                + tokens.size() + " 个，课程 " + courseCode + " 内）");

        Map<String, Map<String, Object>> rowsByBlockId = new LinkedHashMap<>();
        Map<String, Double> keywordScore = new HashMap<>();
        for (Map<String, Object> row : keywordRows) {
            String blockId = str(row.get("blockId"));
            rowsByBlockId.put(blockId, row);
            keywordScore.put(blockId, scoreTokens(tokens, str(row.get("title")), str(row.get("contentMd"))));
        }

        // 2. 语义召回（不可用时诚实降级）
        Map<String, Double> semanticScore = new HashMap<>();
        String embeddingModel = embeddingClient.available() ? embeddingClient.model() : null;
        if (embeddingModel == null) {
            result.setSemanticSkipReason("未配置 embedding 模型（AI_ARK_EMBEDDING_MODEL）");
            result.getTrace().add("语义检索未启用：" + result.getSemanticSkipReason()
                    + "，本轮仅用关键词+图谱（mode=FALLBACK）");
        } else if (StringUtils.isBlank(query)) {
            result.setSemanticSkipReason("查询为空");
        } else {
            try {
                EmbeddingClient.EmbeddingResult embedding =
                        embeddingClient.embed(java.util.Collections.singletonList(query));
                if (embedding.getVectors() == null || embedding.getVectors().isEmpty()) {
                    throw new EmbeddingClient.EmbeddingUnavailableException("查询向量为空");
                }
                float[] queryVector = embedding.getVectors().get(0);
                List<Map<String, Object>> candidates = chunkMapper.selectVectorCandidates(courseCode,
                        scope.getUserId(), embeddingModel, config.getSemanticCandidates());
                result.setSemanticCandidates(candidates.size());
                List<Map.Entry<String, Double>> ranked = new ArrayList<>();
                for (Map<String, Object> candidate : candidates) {
                    float[] vector = VectorMath.parse(str(candidate.get("vectorJson")));
                    double similarity = VectorMath.cosine(queryVector, vector);
                    if (similarity > 0) {
                        ranked.add(new java.util.AbstractMap.SimpleEntry<>(
                                str(candidate.get("blockId")), similarity));
                    }
                }
                ranked.sort(Map.Entry.<String, Double>comparingByValue().reversed());
                int keep = Math.max(config.getMaxSources() * 3, 12);
                for (int i = 0; i < Math.min(keep, ranked.size()); i++) {
                    semanticScore.put(ranked.get(i).getKey(), ranked.get(i).getValue());
                }
                result.setSemanticUsed(true);
                result.getTrace().add("语义召回 " + candidates.size() + " 个同模型向量块，取相似度前 "
                        + semanticScore.size() + " 个（模型 " + embeddingModel + "）");
            } catch (RuntimeException e) {
                result.setSemanticSkipReason("embedding 调用失败：" + e.getClass().getSimpleName());
                result.getTrace().add("语义检索失败（" + e.getClass().getSimpleName()
                        + "），降级为关键词+图谱（mode=FALLBACK）");
            }
        }

        // 3. 合并两路候选，补齐语义命中但未进入关键词集合的块
        Set<String> allBlockIds = new LinkedHashSet<>(rowsByBlockId.keySet());
        allBlockIds.addAll(semanticScore.keySet());
        List<String> missing = new ArrayList<>();
        for (String blockId : semanticScore.keySet()) {
            if (!rowsByBlockId.containsKey(blockId)) {
                missing.add(blockId);
            }
        }
        if (!missing.isEmpty()) {
            for (Map<String, Object> row : chunkMapper.selectScopedByBlockIds(courseCode, scope.getUserId(),
                    missing)) {
                rowsByBlockId.put(str(row.get("blockId")), row);
            }
        }

        // 4. 排序：用户选中的段落优先，其次综合分
        Set<String> selected = new HashSet<>(scope.getSelectedSourceIds());
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (String blockId : allBlockIds) {
            Map<String, Object> row = rowsByBlockId.get(blockId);
            if (row == null) {
                // 语义命中但正文取不到（可能刚被重建删除）：跳过，不伪造来源
                continue;
            }
            if (!scope.isIncludePersonal()
                    && AiMaterial.SCOPE_PERSONAL.equals(str(row.get("ownerScope")))) {
                continue;
            }
            double score = W_SEMANTIC * semanticScore.getOrDefault(blockId, 0.0)
                    + W_KEYWORD * keywordScore.getOrDefault(blockId, 0.0);
            Set<String> chunkNodes = nodeCodesOf(row);
            if (!graphNodes.isEmpty()) {
                boolean hit = !intersect(chunkNodes, graphNodes).isEmpty();
                score += hit ? W_GRAPH_HIT : (constrained ? -W_GRAPH_MISS : 0.0);
                if (hit) {
                    result.setGraphUsed(true);
                }
            }
            if (keywordScore.getOrDefault(blockId, 0.0) > 0
                    && titleHit(tokens, str(row.get("title")))) {
                score += W_TITLE_HIT;
            }
            if (selected.contains(blockId) || selected.contains(str(row.get("sourceId")))) {
                score += W_SELECTED;
            }
            Map<String, Object> scored = new HashMap<>(row);
            scored.put("__score", score);
            ranked.add(scored);
        }
        ranked.sort(Comparator.comparingDouble((Map<String, Object> row) ->
                (Double) row.get("__score")).reversed());

        int max = Math.max(1, config.getMaxSources());
        for (Map<String, Object> row : ranked) {
            if (result.getSources().size() >= max) {
                break;
            }
            result.getSources().add(toSourceRef(row));
        }
        result.getTrace().add("合并去重后返回 " + result.getSources().size() + " 条来源"
                + (selected.isEmpty() ? "" : "（含用户选中段落优先）"));
        if (result.getSources().isEmpty()) {
            result.getTrace().add("课程 " + courseCode + " 内没有可用索引块："
                    + "请先执行资料索引，或确认该课程已发布正文");
        }
        return result;
    }

    // region 内部

    /**
     * 图谱约束：当前节点 + 一跳相关/前置节点。
     * Neo4j 不可用时降级为只用给定节点，检索继续（不阻塞问答）。
     */
    private Set<String> expandGraphNodes(List<String> nodeCodes, Result result) {
        Set<String> nodes = new LinkedHashSet<>();
        if (nodeCodes == null || nodeCodes.isEmpty()) {
            return nodes;
        }
        for (String code : nodeCodes) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            nodes.add(normalize(code));
        }
        try {
            for (String code : new ArrayList<>(nodes)) {
                List<String> related = knowledgeGraphService.getAllRelatedNames(code);
                if (related == null) {
                    continue;
                }
                for (String name : related) {
                    if (StringUtils.isNotBlank(name)) {
                        nodes.add(normalize(name));
                    }
                }
            }
            result.setGraphUsed(true);
            result.getTrace().add("图谱约束扩展至 " + nodes.size() + " 个节点（含一跳前置/相关）");
        } catch (RuntimeException e) {
            result.getTrace().add("图谱扩展不可用（" + e.getClass().getSimpleName()
                    + "），仅按给定节点约束召回");
        }
        return nodes;
    }

    private String normalize(String codeOrName) {
        if (nodeIdentities == null) {
            return codeOrName;
        }
        NodeIdentityService.Identity identity = nodeIdentities.resolve(codeOrName);
        if (identity == null) {
            return codeOrName;
        }
        return identity.nodeCode;
    }

    private Set<String> nodeCodesOf(Map<String, Object> row) {
        Set<String> codes = new HashSet<>();
        JsonNode node = AiJson.mapper().valueToTree(row.get("nodeCodesJson"));
        if (node != null && node.isTextual()) {
            for (String code : AiJson.readList(node.asText(), String.class)) {
                codes.add(normalize(code));
            }
        } else if (node != null && node.isArray()) {
            for (JsonNode code : node) {
                codes.add(normalize(code.asText()));
            }
        }
        return codes;
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.retainAll(b);
        return out;
    }

    /** token 重叠度：命中数 / token 数，标题命中额外加成。 */
    private static double scoreTokens(List<String> tokens, String title, String content) {
        if (tokens.isEmpty()) {
            return 0.0;
        }
        String haystack = StringUtils.defaultString(title) + "\n" + StringUtils.defaultString(content);
        int hit = 0;
        for (String token : tokens) {
            if (haystack.contains(token)) {
                hit++;
            }
        }
        return (double) hit / tokens.size();
    }

    private static boolean titleHit(List<String> tokens, String title) {
        if (StringUtils.isBlank(title)) {
            return false;
        }
        for (String token : tokens) {
            if (title.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static SourceRef toSourceRef(Map<String, Object> row) {
        String content = str(row.get("contentMd"));
        return SourceRef.of(
                str(row.get("sourceId")),
                str(row.get("ownerScope")),
                str(row.get("courseCode")),
                row.get("revision") instanceof Number ? ((Number) row.get("revision")).intValue() : null,
                str(row.get("blockId")),
                str(row.get("locator")),
                StringUtils.abbreviate(content, EXCERPT_CHARS));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    // endregion
}
