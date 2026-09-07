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
        // 1. 从数据库或其他数据源获取原始数据
        List<String> allNames = knowledgeGraphService.getAllRelatedNames(names);
        List<KnowledgeGraphNode> rawData = knowledgeGraphService.getKnowledgeGraph(allNames);
        // 2. 转换为ECharts格式
        GraphDataDTO graphData = knowledgeGraphService.convertToEChartsFormat(rawData);

        return ResultUtils.success(graphData);
    }
    @GetMapping("/getAll")
    public BaseResponse<GraphDataDTO> getAllKnowledgeGraphData()
    {
        List<KnowledgeGraphNode> allKnowledgeGraph = knowledgeGraphService.getAllKnowledgeGraph();
        GraphDataDTO graphDataDTO=knowledgeGraphService.convertToEChartsFormat(allKnowledgeGraph);
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
