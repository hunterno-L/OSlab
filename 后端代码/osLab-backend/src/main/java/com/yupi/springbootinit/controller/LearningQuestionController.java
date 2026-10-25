package com.yupi.springbootinit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.dto.learning.LearningCatalogRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionAddRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionImportRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionReviewRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionUpdateRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.ImportPreviewVO;
import com.yupi.springbootinit.model.vo.learning.ImportResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionManageVO;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 普通题接口（WP1）：/learning/questions/*
 */
@RestController
@RequestMapping("/learning/questions")
public class LearningQuestionController {

    @Resource
    private LearningQuestionService learningQuestionService;

    @Resource
    private UserService userService;

    /**
     * 创建普通题（教师/管理员，初始 DRAFT）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody LearningQuestionAddRequest request,
                                          HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.addQuestion(request, user));
    }

    /**
     * 更新普通题（作者或管理员；仅草稿/驳回/下线状态可改）
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateQuestion(@RequestBody LearningQuestionUpdateRequest request,
                                                HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.updateQuestion(request, user));
    }

    /**
     * 题目详情（学生永不含标准答案；解析仅本人作答后下发）
     */
    @GetMapping("/get")
    public BaseResponse<LearningQuestionVO> getQuestion(@RequestParam("questionId") long questionId,
                                                        HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.getQuestionVO(questionId, user));
    }

    /**
     * 分页查询：学生仅见 PUBLISHED；myOnly=看自己；reviewQueue=管理员审核队列
     */
    @PostMapping("/list/page")
    public BaseResponse<IPage<LearningQuestionVO>> pageQuestions(@RequestBody LearningQuestionQueryRequest request,
                                                                 HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.pageVO(request, user));
    }

    /**
     * 题目练习目录聚合（阶段 4）：章节 -> 知识点 -> 题目数/完成数/待复习数。
     * 学生与管理员同视图，均为本人数据口径；请求体可选 {"chapter":"..."}，
     * 缺省/空体返回全部章节（chapter 传"未归类"返回空白章节的题目）。
     * 未作答数不下发，前端用 questionCount - attempted 计算。
     */
    @PostMapping("/catalog")
    public BaseResponse<LearningCatalogVO> questionCatalog(
            @RequestBody(required = false) LearningCatalogRequest request,
            HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.questionCatalog(request, user));
    }

    /**
     * 管理员题面/编辑 VO（U11）：含 correctAnswer 与审核历史
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/manage/get")
    public BaseResponse<LearningQuestionManageVO> getManageQuestion(
            @RequestParam("questionId") long questionId, HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.getManageQuestionVO(questionId, user));
    }

    /**
     * 管理员分页（U11）：返回管理 VO（含答案），支持 myOnly/reviewQueue/章节/类型/节点筛选
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/manage/list/page")
    public BaseResponse<IPage<LearningQuestionManageVO>> pageManageQuestions(
            @RequestBody LearningQuestionQueryRequest request, HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.pageManageVO(request, user));
    }

    /**
     * 学生作答：服务端判分并返回解析、误区、掌握度变化（永不含标准答案）
     */
    @PostMapping("/answer")
    public BaseResponse<LearningAnswerResultVO> answerQuestion(@RequestBody LearningAnswerRequest request,
                                                               HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.answerQuestion(request, user));
    }

    /**
     * 审核流转：SUBMIT（作者）；APPROVE/REJECT/PUBLISH/OFFLINE（管理员）
     */
    @PostMapping("/review")
    public BaseResponse<Boolean> reviewQuestion(@RequestBody LearningQuestionReviewRequest request,
                                                HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.reviewQuestion(request, user));
    }

    /**
     * JSON 导入预览（Phase 8 §12.2）：逐项校验节点、题型、答案格式与解析，不落库
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/import/preview")
    public BaseResponse<ImportPreviewVO> importPreview(@RequestBody LearningQuestionImportRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(learningQuestionService.importPreview(request.getContent()));
    }

    /**
     * JSON 导入提交（Phase 8 §12.2）：仅导入校验通过项为 DRAFT，经真实审核后发布
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/import/commit")
    public BaseResponse<ImportResultVO> importCommit(@RequestBody LearningQuestionImportRequest request,
                                                     HttpServletRequest servletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningQuestionService.importCommit(request.getContent(), user));
    }
}
