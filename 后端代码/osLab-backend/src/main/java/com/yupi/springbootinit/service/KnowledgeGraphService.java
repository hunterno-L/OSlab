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
    public EntityNode saveKnowledgeGraphNode(EntityNode node) {
        return knowledgeGraphRepository.save(node);
    }
    public EntityNode saveKnowledgeGraphNode(NodeDTO node) {
        return knowledgeGraphRepository.save(convertToEntity(node));
    }
    public EntityNode findById(Long id) {
        return knowledgeGraphRepository.findById(id).orElse(null);
    }
    public List<EntityNode> findByName(String name) {
        return knowledgeGraphRepository.findByName(name);
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
        node.setCategory(entity.getLabel());
        node.setDescription(entity.getDescription());
        return node;
    }
    public EntityNode convertToEntity(NodeDTO nodeDTO)
    {
        EntityNode entityNode =new EntityNode();
        entityNode.setName(nodeDTO.getName());
        entityNode.setLabel(nodeDTO.getCategory());
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
