package com.yupi.springbootinit.service;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import com.yupi.springbootinit.constant.RelationType;
import com.yupi.springbootinit.model.dto.knowledgegraph.*;
import com.yupi.springbootinit.model.entity.EntityNode;
import com.yupi.springbootinit.model.entity.KnowledgeGraphNode;
import com.yupi.springbootinit.repository.KnowledgeGraphRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphService {
    @Autowired
    private KnowledgeGraphRepository knowledgeGraphRepository;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.neo4j.driver.Driver driver;
    @Autowired
    private com.yupi.springbootinit.learning.RegistryGraphService registryGraph;
    @Autowired
    private com.yupi.springbootinit.learning.NodeIdentityService nodeIdentities;
    public EntityNode saveKnowledgeGraphNode(EntityNode node) {
        return knowledgeGraphRepository.save(node);
    }
    public EntityNode saveKnowledgeGraphNode(NodeDTO node) {
        return knowledgeGraphRepository.save(convertToEntity(node));
    }
    /** 按真实名称定位节点，仅更新 chapter，保留名称、描述、类型和全部关系。 */
    public void updateNodeChapter(UpdateNodeChapterRequest request) {
        if (request == null || StringUtils.isBlank(request.getNodeName())
                || StringUtils.isBlank(request.getChapter())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "节点名称和所属章节不能为空");
        }
        String nodeName = request.getNodeName().trim();
        String chapter = request.getChapter().trim();
        if (nodeName.length() > 200 || chapter.length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "节点名称或章节过长");
        }
        try (org.neo4j.driver.Session session = driver.session()) {
            session.writeTransaction(transaction -> {
                org.neo4j.driver.Result result = transaction.run(
                        "MATCH (n:Entity {name: $nodeName}) WITH collect(n) AS nodes "
                                + "FOREACH (n IN CASE WHEN size(nodes) = 1 THEN nodes ELSE [] END "
                                + "| SET n.chapter = $chapter) RETURN size(nodes) AS matched",
                        Map.of("nodeName", nodeName, "chapter", chapter));
                long matched = result.single().get("matched").asLong();
                if (matched == 0) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识节点不存在：" + nodeName);
                }
                if (matched > 1) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "节点名称重复，无法确定所属节点");
                }
                return null;
            });
        }
    }
    public EntityNode findById(Long id) {
        return knowledgeGraphRepository.findById(id).orElse(null);
    }
    public List<EntityNode> findByName(String name) {
        com.yupi.springbootinit.learning.NodeIdentityService.Identity identity = nodeIdentities == null ? null : nodeIdentities.resolve(name);
        if (identity != null) {
            GraphDataDTO graph = getGraphSnapshot();
            NodeDTO node = resolveNode(graph, identity.nodeCode, identity.storageKey);
            if (node == null) return Collections.emptyList();
            EntityNode entity = convertToEntity(node);
            if (node.getId() != null && node.getId().matches("\\d+")) entity.setId(Long.valueOf(node.getId()));
            return Collections.singletonList(entity);
        }
        List<EntityNode> found = knowledgeGraphRepository.findByName(name);
        if (found.size()>1) throw new BusinessException(ErrorCode.PARAMS_ERROR,"节点名称不唯一，请使用 nodeCode："+name);
        return found;
    }
    /** Code wins over names; duplicate legacy names must never select an arbitrary graph node. */
    public static NodeDTO resolveNode(GraphDataDTO graph, String code, String storageKey) {
        List<NodeDTO> matches = new ArrayList<>();
        for (NodeDTO node : graph.getNodes()==null?Collections.<NodeDTO>emptyList():graph.getNodes())
            if (code!=null&&code.equals(node.getNodeCode())) matches.add(node);
        if (matches.isEmpty()) for (NodeDTO node : graph.getNodes()==null?Collections.<NodeDTO>emptyList():graph.getNodes())
            if (storageKey!=null&&storageKey.equals(node.getName())) matches.add(node);
        if (matches.size()>1) throw new BusinessException(ErrorCode.PARAMS_ERROR,"节点名称不唯一，请使用 nodeCode："+storageKey);
        return matches.isEmpty()?null:matches.get(0);
    }
    public static boolean isPrerequisiteRelation(String type) {
        return RelationType.DEPENDENT.equals(type)||"REQUIRES".equals(type);
    }
    public List<KnowledgeGraphNode> getKnowledgeGraph(String name) {
        List<KnowledgeGraphNode> result=new ArrayList<>();
        List<String> relationTypes = Arrays.asList(RelationType.RELATED, RelationType.DEPENDENT, RelationType.CONTAIN, RelationType.IMPLEMENTS);

        for (String relationType : relationTypes) {
            Optional<KnowledgeGraphNode> nodeOptional = knowledgeGraphRepository.findByNameWithRelationship(name, relationType);
            if (nodeOptional.isPresent()) {
                KnowledgeGraphNode node = nodeOptional.get();
                node.setRelationType(relationType); // 设置关系类型
                result.add(node);
            } else {
                System.out.println("No Data for relation type: " + relationType);
            }
        }

        return result;
    }
    public KnowledgeGraphNode findByNameWithRelationship(String name,String relationshipType)
    {
        Optional<KnowledgeGraphNode> result = knowledgeGraphRepository.findByNameWithRelationship(name, relationshipType);
        if (result.isPresent()) {
            KnowledgeGraphNode node = result.get();
            node.setRelationType(relationshipType);
            // 可以在这里做进一步的处理
            return node;
        } else {
            System.out.println("No Data");
            return null;
        }
    }
    /**
     * 一次性图谱快照（Phase 1 契约 §5）：单条 Cypher 返回全部节点与关系，
     * 保留孤立节点、边按 (起点,类型,终点) 去重、方向按存储方向，节点 chapter 缺省"未分类"。
     * 替代旧 getAllKnowledgeGraph 的"节点数 × 关系类型数"N+1 查询。
     */
    public GraphDataDTO getGraphSnapshot() {
        GraphDataDTO graphData = new GraphDataDTO();
        Map<String, NodeDTO> nodeMap = new LinkedHashMap<>();
        List<LinkDTO> links = new ArrayList<>();
        Set<String> linkKeys = new HashSet<>();
        try (org.neo4j.driver.Session session = driver.session()) {
            org.neo4j.driver.Result result = session.run(
                    "MATCH (n:Entity) OPTIONAL MATCH (n)-[r]->(m:Entity) "
                            + "RETURN id(n) AS nid, n AS n, id(m) AS mid, r AS r");
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                String id = String.valueOf(record.get("nid").asLong());
                org.neo4j.driver.types.Node n = record.get("n").asNode();
                NodeDTO node = nodeMap.computeIfAbsent(id, k -> toNodeDTO(id, n));
                if (node.getChapter() == null || node.getChapter().isBlank()) {
                    node.setChapter("未分类");
                }
                if (!record.get("r").isNull()) {
                    org.neo4j.driver.types.Relationship r = record.get("r").asRelationship();
                    String mid = String.valueOf(record.get("mid").asLong());
                    String key = id + "|" + r.type() + "|" + mid;
                    if (linkKeys.add(key)) {
                        LinkDTO link = new LinkDTO();
                        link.setSource(id);
                        link.setTarget(mid);
                        link.setRelationType(r.type());
                        link.setValue(r.type());
                        links.add(link);
                    }
                }
            }
        } catch (org.neo4j.driver.exceptions.Neo4jException unavailable) {
            if (registryGraph == null) throw unavailable;
            // Registered course identities remain usable while the optional legacy graph is unavailable.
        }
        Map<String, Integer> chapterSummary = new LinkedHashMap<>();
        for (NodeDTO node : nodeMap.values()) {
            chapterSummary.merge(node.getChapter(), 1, Integer::sum);
        }
        List<CategoryDTO> categories = nodeMap.values().stream()
                .map(NodeDTO::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .map(CategoryDTO::new)
                .collect(Collectors.toList());
        graphData.setNodes(new ArrayList<>(nodeMap.values()));
        graphData.setLinks(links);
        graphData.setCategories(categories);
        graphData.setTotalNodes(nodeMap.size());
        graphData.setTotalLinks(links.size());
        graphData.setChapterSummary(chapterSummary);
        if (registryGraph != null) registryGraph.overlay(graphData);
        return graphData;
    }

    /** 快照节点映射：id 与旧 createNodeDTO 一致（内部 id 字符串），章节缺省"未分类"。 */
    private NodeDTO toNodeDTO(String id, org.neo4j.driver.types.Node n) {
        NodeDTO node = new NodeDTO();
        node.setId(id);
        node.setName(n.get("name").asString(null));
        node.setDisplayName(n.get("displayName").asString(node.getName()));
        node.setNodeType(n.get("nodeType").asString(null));
        node.setLayer(n.get("layer").asString(null));
        node.setHomeSectionCode(n.get("homeSectionCode").asString(null));
        node.setNodeCode(n.get("nodeCode").asString(null));
        node.setCategory(n.get("label").asString(null));
        node.setDescription(n.get("description").asString(null));
        String chapter = n.get("chapter").asString(null);
        node.setChapter(chapter == null || chapter.isBlank() ? "未分类" : chapter);
        return node;
    }

    /** 测试与诊断辅助：执行只读计数查询。 */
    public long countByQuery(String cypher) {
        try (org.neo4j.driver.Session session = driver.session()) {
            org.neo4j.driver.Result result = session.run(cypher);
            return result.single().get(0).asLong();
        }
    }

    public GraphDataDTO convertToEChartsFormat(List<KnowledgeGraphNode> knowledgeGraphNodes) {
        GraphDataDTO graphData = new GraphDataDTO();
        List<LinkDTO> links = new ArrayList<>();
        // 用于去重和快速查找的映射
        Map<String, NodeDTO> nodeMap = new HashMap<>();
        Set<String> categorySet = new HashSet<>();
        for (KnowledgeGraphNode kgNode : knowledgeGraphNodes) {
            // 处理主实体节点
            EntityNode entity = kgNode.getEntity();
            NodeDTO mainNode = createNodeDTO(entity);
            nodeMap.putIfAbsent(mainNode.getId(), mainNode);
            categorySet.add(entity.getLabel());
            // 处理相关实体节点
            for (EntityNode relatedEntity : kgNode.getRelatedEntities()) {
                NodeDTO relatedNode = createNodeDTO(relatedEntity);
                nodeMap.putIfAbsent(relatedNode.getId(), relatedNode);
                categorySet.add(relatedEntity.getLabel());
                // 创建关系链接
                LinkDTO link = new LinkDTO();
                link.setSource(mainNode.getId());
                link.setTarget(relatedNode.getId());
                link.setValue(kgNode.getRelationType());
                link.setRelationType(kgNode.getRelationType());
                links.add(link);
            }
        }
        List<NodeDTO> nodes = new ArrayList<>(nodeMap.values());
        graphData.setNodes(nodes);
        graphData.setLinks(links);
        // 使用 Stream API 转换 Set 到 List<CategoryDTO>
        List<CategoryDTO> categories = categorySet.stream()
                .map(category -> {
                    CategoryDTO cat = new CategoryDTO(category);
                    return cat;
                })
                .collect(Collectors.toList());
        graphData.setCategories(categories); // 设置分类

        return graphData;
    }
    public GraphDataDTO convertToEChartsFormat(KnowledgeGraphNode knowledgeGraphNode) {
        GraphDataDTO graphData = new GraphDataDTO();
        List<LinkDTO> links = new ArrayList<>();
        // 用于去重和快速查找的映射
        Map<String, NodeDTO> nodeMap = new HashMap<>();

        // 处理主实体节点
        EntityNode entity = knowledgeGraphNode.getEntity();
        NodeDTO mainNode = createNodeDTO(entity);
        nodeMap.putIfAbsent(mainNode.getId(), mainNode);

        // 处理相关实体节点
        for (EntityNode relatedEntity : knowledgeGraphNode.getRelatedEntities()) {
            NodeDTO relatedNode = createNodeDTO(relatedEntity);
            nodeMap.putIfAbsent(relatedNode.getId(), relatedNode);

            // 创建关系链接
            LinkDTO link = new LinkDTO();
            link.setSource(mainNode.getId());
            link.setTarget(relatedNode.getId());
            link.setValue(knowledgeGraphNode.getRelationType());
            link.setRelationType(knowledgeGraphNode.getRelationType());
            links.add(link);
        }
        List<NodeDTO> nodes = new ArrayList<>(nodeMap.values());
        graphData.setNodes(nodes);
        graphData.setLinks(links);
        graphData.setCategories(new ArrayList<>(List.of(new CategoryDTO(knowledgeGraphNode.getRelationType())))); // 设置分类

        return graphData;
    }
    private NodeDTO createNodeDTO(EntityNode entity) {
        NodeDTO node = new NodeDTO();
        node.setId(entity.getId().toString());
        node.setName(entity.getName());
        node.setNodeCode(entity.getNodeCode());
        node.setCategory(entity.getLabel());
        node.setDescription(entity.getDescription());
        String chapter = entity.getChapter();
        node.setChapter(chapter == null || chapter.isBlank() ? "未分类" : chapter);
        return node;
    }
    public EntityNode convertToEntity(NodeDTO nodeDTO)
    {
        EntityNode entityNode =new EntityNode();
        entityNode.setName(nodeDTO.getName());
        entityNode.setNodeCode(nodeDTO.getNodeCode());
        entityNode.setLabel(nodeDTO.getCategory());
        entityNode.setChapter(StringUtils.isBlank(nodeDTO.getChapter()) ? "未分类" : nodeDTO.getChapter().trim());
        entityNode.setDescription(nodeDTO.getDescription());
        return entityNode;
    }

    // 将 label 转换为分类索引
    private Integer getCategoryIndex(String label) {
        return switch(label) {
            case "基础" -> 0;
            case "进阶" -> 1;
            case "应用" -> 2;
            default -> throw new IllegalArgumentException("未知的标签类型: " + label);
        };
    }


    public List<KnowledgeGraphNode> getKnowledgeGraph(List<String> names) {
        List<KnowledgeGraphNode> result=new ArrayList<>();
        List<String> relationTypes = Arrays.asList(RelationType.RELATED, RelationType.DEPENDENT, RelationType.CONTAIN, RelationType.IMPLEMENTS);
        for(String name:names)
        {
            for (String relationType : relationTypes) {
                Optional<KnowledgeGraphNode> nodeOptional = knowledgeGraphRepository.findByNameWithRelationshipDirected(name, relationType);
                if (nodeOptional.isPresent()) {
                    KnowledgeGraphNode node = nodeOptional.get();
                    node.setRelationType(relationType); // 设置关系类型
                    result.add(node);
                } else {
                    System.out.println("No Data for relation type: " + relationType);
                }
            }
        }
        return result;
    }
    public List<String> getAllRelatedNames(List<String> names)
    {
        Set<String> allNames = new HashSet<>(names);
        List<String> relationTypes = Arrays.asList(RelationType.RELATED, RelationType.DEPENDENT, RelationType.CONTAIN, RelationType.IMPLEMENTS);
        for(String name:names)
        {
            for (String relationType : relationTypes) {
                Optional<KnowledgeGraphNode> nodeOptional = knowledgeGraphRepository.findByNameWithRelationship(name, relationType);
                if (nodeOptional.isPresent()) {
                    KnowledgeGraphNode node = nodeOptional.get();
                    for(EntityNode n:node.getRelatedEntities())
                    {
                        allNames.add(n.getName());
                    }
                } else {
                    System.out.println("No Data for relation type: " + relationType);
                }
            }
        }
        return new ArrayList<>(allNames);

    }
    public List<String> getAllRelatedNames(String name)
    {
        if (nodeIdentities != null) {
            com.yupi.springbootinit.learning.NodeIdentityService.Identity identity = nodeIdentities.resolve(name);
            GraphDataDTO graph = getGraphSnapshot();
            NodeDTO seed = resolveNode(graph, identity==null?null:identity.nodeCode, identity==null?name:identity.storageKey);
            if (seed==null) return Collections.emptyList();
            Set<String> ids = new LinkedHashSet<>();ids.add(seed.getId());
            for (LinkDTO link : graph.getLinks()==null?Collections.<LinkDTO>emptyList():graph.getLinks()) {
                if (seed.getId().equals(link.getSource())) ids.add(link.getTarget());
                if (seed.getId().equals(link.getTarget())) ids.add(link.getSource());
            }
            List<String> names=new ArrayList<>();
            for(NodeDTO node:graph.getNodes()) if(ids.contains(node.getId()))names.add(node.getName());
            return names;
        }
        Set<String> allNames = new HashSet<>();
        allNames.add(name);
        List<String> relationTypes = Arrays.asList(RelationType.RELATED, RelationType.DEPENDENT, RelationType.CONTAIN, RelationType.IMPLEMENTS);

        for (String relationType : relationTypes) {
            Optional<KnowledgeGraphNode> nodeOptional = knowledgeGraphRepository.findByNameWithRelationship(name, relationType);
            if (nodeOptional.isPresent()) {
                KnowledgeGraphNode node = nodeOptional.get();
                for(EntityNode n:node.getRelatedEntities())
                {
                    allNames.add(n.getName());
                }
            } else {
                System.out.println("No Data for relation type: " + relationType);
            }
        }

        return new ArrayList<>(allNames);

    }
    public List<KnowledgeGraphNode> getAllKnowledgeGraph()
    {
        List<EntityNode> allEntityNodes = knowledgeGraphRepository.findAll();
        List<KnowledgeGraphNode> result=new ArrayList<>();
        List<String> allNames = allEntityNodes.stream()
                .map(EntityNode::getName)
                .collect(Collectors.toList());
        return getKnowledgeGraph(allNames);

    }
    public List<EntityNode> getAllEntityNode()
    {
        return knowledgeGraphRepository.findAll();
    }
    /**
     * 只读 Cypher 前缀白名单（忽略大小写与行首空白）。
     */
    private static final Pattern READ_ONLY_CYPHER_PREFIX = Pattern.compile(
            "^\\\\s*(?i)(MATCH|OPTIONAL\\\\s+MATCH|RETURN|WITH|UNWIND)\\\\b");

    /**
     * 写操作关键字黑名单：命中即拒绝。
     */
    private static final Pattern WRITE_CYPHER_KEYWORDS = Pattern.compile(
            "(?i)\\\\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP|LOAD|CALL|FOREACH)\\\\b");

    /**
     * 执行用户的 Cypher 语句（不返回结果，只执行）。
     * <p>安全限制：仅允许只读查询，禁止任何写操作（CREATE/MERGE/DELETE 等），
     * 防止管理员误操作或账号被盗时整图被删。
     */
    @Transactional
    public void executeCypher(CypherRequest cypherRequest) {
        String query = cypherRequest.getQuery();
        if (StringUtils.isBlank(query) || !READ_ONLY_CYPHER_PREFIX.matcher(query).find()
                || WRITE_CYPHER_KEYWORDS.matcher(query).find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅允许执行只读查询（如 MATCH / RETURN），禁止删除或修改操作");
        }
        neo4jClient.query(query).run();
    }

    public void deleteNode(DeleteNodeRequest deleteNodeRequest) {
        knowledgeGraphRepository.deleteByName(deleteNodeRequest.getName());
    }

    public void addKnowledgeGraphRelation(AddRelationRequest addRelationRequest) {
        if(addRelationRequest.getRelationType().equals("相关"))
        {
            knowledgeGraphRepository.createRelatedRelationByName(addRelationRequest.getFromName() ,addRelationRequest.getToName());
        }
        else if(addRelationRequest.getRelationType().equals("包含"))
        {
            knowledgeGraphRepository.createContainRelationByName(addRelationRequest.getFromName() ,addRelationRequest.getToName());
        } else if (addRelationRequest.getRelationType().equals("依赖")) {
            knowledgeGraphRepository.createDependentRelationByName(addRelationRequest.getFromName() ,addRelationRequest.getToName());
        } else if (addRelationRequest.getRelationType().equals("实现")) {
            knowledgeGraphRepository.createImplementsRelationByName(addRelationRequest.getFromName() ,addRelationRequest.getToName());
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的关系类型：" + addRelationRequest.getRelationType());
        }
    }
}
