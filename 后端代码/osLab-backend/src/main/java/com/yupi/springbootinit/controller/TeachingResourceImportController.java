package com.yupi.springbootinit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.*;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.learning.*;
import com.yupi.springbootinit.service.UserService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/teaching")
public class TeachingResourceImportController {
    @Resource private TeachingAssessmentService assessments;
    @Resource private ExperimentMaintenanceService experiments;
    @Resource private NodeIdentityService identities;
    @Resource private UserService users;
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/assessments/import/preview") public BaseResponse<Map<String,Object>> preview(@RequestBody JsonNode body){return ResultUtils.success(assessments.preview(body));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/assessments/import/commit") public BaseResponse<Map<String,Object>> commit(@RequestBody JsonNode body,HttpServletRequest request){return ResultUtils.success(assessments.commit(body,users.getLoginUser(request).getId()));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/assessments/{code}") public BaseResponse<Map<String,Object>> manage(@PathVariable String code){return ResultUtils.success(assessments.manage(code));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/assessments/{code}/review") public BaseResponse<Boolean> review(@PathVariable String code,@RequestBody JsonNode body,HttpServletRequest request){long id=Long.parseLong(assessments.manage(code).get("questionId").toString());return ResultUtils.success(assessments.review(id,body.path("action").asText(),body.path("reason").asText(),users.getLoginUser(request).getId()));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/experiments/import/preview") public BaseResponse<Map<String,Object>> experimentPreview(@RequestBody JsonNode body){return ResultUtils.success(experiments.preview(body));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/experiments/import/commit") public BaseResponse<Map<String,Object>> experimentCommit(@RequestBody JsonNode body,HttpServletRequest request){return ResultUtils.success(experiments.commit(body,users.getLoginUser(request).getId()));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/experiments/{questionId}/mappings") public BaseResponse<Map<String,Object>> mappings(@PathVariable String questionId){return ResultUtils.success(experiments.mappings(questionId));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PutMapping("/experiments/{questionId}/mappings") public BaseResponse<Map<String,Object>> mappings(@PathVariable String questionId,@RequestBody JsonNode body,HttpServletRequest request){return ResultUtils.success(experiments.saveMappings(questionId,body,users.getLoginUser(request).getId()));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/experiments/{questionId}/review") public BaseResponse<Map<String,Object>> experimentReview(@PathVariable String questionId,@RequestBody JsonNode body,HttpServletRequest request){return ResultUtils.success(experiments.review(questionId,body,users.getLoginUser(request).getId()));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PutMapping("/nodes/{code}/legacy-identity") public BaseResponse<NodeIdentityService.Identity> bind(@PathVariable String code,@RequestBody JsonNode body){return ResultUtils.success(identities.bindLegacy(code,body.path("legacyNodeName").asText()));}
}
