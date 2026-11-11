package com.yupi.springbootinit.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves course nodes from published unit and content snapshots in one query. */
@Service
public class PublishedCourseScopeService {
    @Resource private JdbcTemplate jdbc;

    public void requireUnit(String courseCode, String unitCode) {
        if (unitCode == null || unitCode.trim().isEmpty()) return;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM learning_unit WHERE courseCode=? AND unitCode=? AND isDelete=0 AND publishedJson IS NOT NULL", Integer.class, courseCode, unitCode.trim());
        if (count == null || count == 0) {
            throw new com.yupi.springbootinit.exception.BusinessException(com.yupi.springbootinit.common.ErrorCode.PARAMS_ERROR, "学习单元不属于当前已发布课程");
        }
    }

    public Map<String, NodeIdentityService.Identity> nodes(String courseCode) {
        Map<String, NodeIdentityService.Identity> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT DISTINCT reg.nodeCode, reg.name, legacy.legacyNodeName FROM ("
                + "SELECT j.nodeCode COLLATE utf8mb4_unicode_ci AS nodeCode FROM learning_unit u JOIN JSON_TABLE(u.publishedJson, '$.nodeMappings[*]' COLUMNS(nodeCode VARCHAR(128) PATH '$.nodeCode')) j "
                + "WHERE u.courseCode=? AND u.isDelete=0 AND u.publishedJson IS NOT NULL "
                + "UNION ALL SELECT j.nodeCode COLLATE utf8mb4_unicode_ci FROM learning_unit u JOIN JSON_TABLE(u.publishedJson, '$.activities[*]' COLUMNS(kind VARCHAR(24) PATH '$.reference.kind', code VARCHAR(128) PATH '$.reference.code')) a "
                + "JOIN learning_content_chunk c ON a.kind='CHUNK' AND c.chunkCode=a.code COLLATE utf8mb4_unicode_ci AND c.isDelete=0 AND c.publishedJson IS NOT NULL "
                + "JOIN JSON_TABLE(c.publishedJson, '$.nodeCodes[*]' COLUMNS(nodeCode VARCHAR(128) PATH '$')) j "
                + "WHERE u.courseCode=? AND u.isDelete=0 AND u.publishedJson IS NOT NULL "
                + "UNION ALL SELECT n.nodeCode COLLATE utf8mb4_unicode_ci FROM learning_unit u JOIN JSON_TABLE(u.publishedJson, '$.activities[*]' COLUMNS(kind VARCHAR(24) PATH '$.reference.kind', code VARCHAR(128) PATH '$.reference.code')) a "
                + "JOIN learning_question q ON a.kind='QUESTION' AND q.questionCode=a.code COLLATE utf8mb4_unicode_ci AND q.isDelete=0 AND q.reviewStatus='PUBLISHED' "
                + "JOIN learning_question_node n ON n.questionId=q.id WHERE u.courseCode=? AND u.isDelete=0 AND u.publishedJson IS NOT NULL "
                + "UNION ALL SELECT n.nodeCode COLLATE utf8mb4_unicode_ci FROM learning_unit u JOIN JSON_TABLE(u.publishedJson, '$.activities[*]' COLUMNS(type VARCHAR(24) PATH '$.activityType', kind VARCHAR(24) PATH '$.reference.kind', code VARCHAR(128) PATH '$.reference.code')) a "
                + "JOIN course_resource r ON a.type='EXPERIMENT' AND a.kind='RESOURCE' AND r.resourceCode=a.code COLLATE utf8mb4_unicode_ci AND r.resourceType='EXPERIMENT' AND r.status='PUBLISHED' AND r.isDelete=0 "
                + "JOIN experiment_node n ON n.questionId=r.questionId WHERE u.courseCode=? AND u.isDelete=0 AND u.publishedJson IS NOT NULL"
                + ") codes JOIN knowledge_node_registry reg ON reg.nodeCode=codes.nodeCode COLLATE utf8mb4_unicode_ci AND reg.isDelete=0 LEFT JOIN knowledge_node_legacy_identity legacy ON legacy.nodeCode=reg.nodeCode "
                + "WHERE codes.nodeCode IS NOT NULL AND codes.nodeCode<>'' ORDER BY reg.nodeCode",
                courseCode, courseCode, courseCode, courseCode)) {
            String code = row.get("nodeCode").toString();
            String name = row.get("name").toString();
            Object legacy = row.get("legacyNodeName");
            result.put(code, new NodeIdentityService.Identity(code, name, legacy == null ? code : legacy.toString()));
        }
        return result;
    }
    public com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO filterGraph(
            com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO graph, String courseCode) {
        return filterGraph(graph, nodes(courseCode));
    }

    public com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO filterGraph(
            com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO graph,
            Map<String, NodeIdentityService.Identity> scope) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (NodeIdentityService.Identity identity : scope.values()) keys.add(identity.storageKey);
        java.util.List<com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO> selected = new java.util.ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        Map<String, Integer> chapters = new LinkedHashMap<>();
        if (graph.getNodes() != null) for (com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO node : graph.getNodes()) {
            if (scope.containsKey(node.getNodeCode()) || (node.getNodeCode() == null && keys.contains(node.getName()))) {
                selected.add(node);
                ids.add(node.getId());
                chapters.merge(node.getChapter() == null ? "未分类" : node.getChapter(), 1, Integer::sum);
            }
        }
        java.util.List<com.yupi.springbootinit.model.dto.knowledgegraph.LinkDTO> links = new java.util.ArrayList<>();
        if (graph.getLinks() != null) for (com.yupi.springbootinit.model.dto.knowledgegraph.LinkDTO link : graph.getLinks()) {
            if (ids.contains(link.getSource()) && ids.contains(link.getTarget())) links.add(link);
        }
        com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO result = new com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO();
        result.setNodes(selected); result.setLinks(links); result.setCategories(graph.getCategories());
        result.setTotalNodes(selected.size()); result.setTotalLinks(links.size()); result.setChapterSummary(chapters);
        return result;
    }

}
