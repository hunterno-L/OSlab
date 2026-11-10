package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.model.dto.knowledgegraph.*;
import com.yupi.springbootinit.model.entity.EntityNode;
import com.yupi.springbootinit.model.entity.KnowledgeGraphNode;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/knowledge_graph")
public class KnowledgeGraphController {
    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    @GetMapping("/get")
    public BaseResponse<GraphDataDTO> getKnowledgeGraphData(String name) {
        // 1. 从数据库或其他数据源获取原始数据
        List<String> allNames = knowledgeGraphService.getAllRelatedNames(name);
        List<KnowledgeGraphNode> rawData = knowledgeGraphService.getKnowledgeGraph(allNames);
        // 2. 转换为ECharts格式
        GraphDataDTO graphData = knowledgeGraphService.convertToEChartsFormat(rawData);

        return ResultUtils.success(graphData);
    }
    @PostMapping("/getMultiple")
    public BaseResponse<GraphDataDTO> getKnowledgeGraphDataByList(@RequestBody List<String> names) {
        GraphDataDTO graphData = null;
        try {
            List<String> allNames = knowledgeGraphService.getAllRelatedNames(names);
            List<KnowledgeGraphNode> rawData = knowledgeGraphService.getKnowledgeGraph(allNames);
            graphData = knowledgeGraphService.convertToEChartsFormat(rawData);
        } catch (Exception ignored) {
            graphData = null;
        }
        if (graphData != null && graphData.getNodes() != null && !graphData.getNodes().isEmpty()) {
            return ResultUtils.success(graphData);
        }
        // 标签不是图谱节点名时，按章节名回退成该章节的子图
        GraphDataDTO snapshot = knowledgeGraphService.getGraphSnapshot();
        java.util.Set<String> keys = new java.util.HashSet<>(names == null ? java.util.Collections.emptyList() : names);
        java.util.List<com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO> nodes = new java.util.ArrayList<>();
        java.util.Set<String> keep = new java.util.HashSet<>();
        if (snapshot.getNodes() != null) {
            for (com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO node : snapshot.getNodes()) {
                boolean match = keys.contains(node.getName())
                        || keys.contains(node.getDisplayName())
                        || (node.getChapter() != null && keys.contains(node.getChapter()));
                if (match) {
                    nodes.add(node);
                    keep.add(node.getId());
                }
            }
        }
        java.util.List<com.yupi.springbootinit.model.dto.knowledgegraph.LinkDTO> links = new java.util.ArrayList<>();
        if (snapshot.getLinks() != null) {
            for (com.yupi.springbootinit.model.dto.knowledgegraph.LinkDTO link : snapshot.getLinks()) {
                if (keep.contains(link.getSource()) && keep.contains(link.getTarget())) {
                    links.add(link);
                }
            }
        }
        GraphDataDTO fallback = new GraphDataDTO();
        fallback.setNodes(nodes);
        fallback.setLinks(links);
        fallback.setTotalNodes(nodes.size());
        fallback.setTotalLinks(links.size());
        fallback.setCategories(snapshot.getCategories());
        fallback.setChapterSummary(snapshot.getChapterSummary());
        return ResultUtils.success(fallback);
    }
    @GetMapping("/getAll")
    public BaseResponse<GraphDataDTO> getAllKnowledgeGraphData()
    {
        // Phase 1：全量接口改用一次性快照（保留孤立节点、边去重、含 chapter/统计）
        GraphDataDTO graphDataDTO = knowledgeGraphService.getGraphSnapshot();
        return ResultUtils.success(graphDataDTO);
    }
    @GetMapping("/getNames")
    public BaseResponse<List<String>> getAllKnowledgeGraphName()
    {
        List<EntityNode> allEntityNode = knowledgeGraphService.getAllEntityNode();
        List<String> allTags=allEntityNode.stream().map(EntityNode::getName).collect(Collectors.toList());
        return ResultUtils.success(allTags);
    }
    @PostMapping("/execute")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> execute(@RequestBody CypherRequest cypherRequest) {
        knowledgeGraphService.executeCypher(cypherRequest);
        return ResultUtils.success("执行成功");
    }
    @PostMapping("/addNode")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<EntityNode> addKnowledgeGraphNode(@RequestBody NodeDTO nodeDTO)
    {
        EntityNode entityNode = knowledgeGraphService.saveKnowledgeGraphNode(nodeDTO);
        return ResultUtils.success(entityNode);
    }
    @PostMapping("/updateChapter")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> updateNodeChapter(@RequestBody UpdateNodeChapterRequest request) {
        knowledgeGraphService.updateNodeChapter(request);
        return ResultUtils.success("章节已更新");
    }
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> deleteByName(@RequestBody DeleteNodeRequest deleteNodeRequest)
    {
        knowledgeGraphService.deleteNode(deleteNodeRequest);
        return ResultUtils.success("删除成功");
    }
    @PostMapping("/addRelation")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> addKnowledgeGraphRelation(@RequestBody AddRelationRequest addRelationRequest)
    {
        knowledgeGraphService.addKnowledgeGraphRelation(addRelationRequest);
        return ResultUtils.success("添加成功");
    }
}
