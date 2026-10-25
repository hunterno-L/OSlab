package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.dto.learning.LearningCatalogRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionAddRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionReviewRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionUpdateRequest;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.ImportPreviewVO;
import com.yupi.springbootinit.model.vo.learning.ImportResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionManageVO;

import java.util.List;

/**
 * 普通题服务（WP1）：CRUD + 四题型服务端判分 + 审核状态机
 */
public interface LearningQuestionService extends IService<LearningQuestion> {

    /**
     * 创建普通题（作者=当前教师/管理员），初始状态 DRAFT
     */
    Long addQuestion(LearningQuestionAddRequest request, User author);

    /**
     * 更新普通题（仅作者或管理员；仅 DRAFT/REJECTED/OFFLINE 可改，改后回 DRAFT）
     */
    boolean updateQuestion(LearningQuestionUpdateRequest request, User user);

    /**
     * 获取题目 VO（学生永不含标准答案；解析仅在本人已作答后下发）
     */
    LearningQuestionVO getQuestionVO(long questionId, User user);

    /**
     * 批量组装学生题目视图。工作台使用该入口，映射与本人作答状态均按批次读取。
     */
    List<LearningQuestionVO> getQuestionVOs(List<Long> questionIds, User user);

    /**
     * 分页查询：学生仅见 PUBLISHED；myOnly 看自己的；reviewQueue 管理员审核队列
     */
    IPage<LearningQuestionVO> pageVO(LearningQuestionQueryRequest request, User user);

    /**
     * 测评候选完整 ID 集（阶段 5 §5.2）：学生口径（仅 PUBLISHED 且未删除）+
     * 章节（与目录/分页共享同一谓词）/节点/题型/难度过滤 + 本人学习状态过滤，
     * 返回全部符合条件题目 id（id 升序，无分页上限，供测评 start 按数量稳定抽样）。
     */
    List<Long> listEligibleQuestionIds(LearningQuestionQueryRequest request, User user);

    /**
     * 管理员题面/编辑 VO（U11）：含 correctAnswer 与审核历史。仅 ADMIN 端点调用。
     */
    LearningQuestionManageVO getManageQuestionVO(long questionId, User user);

    /**
     * 管理员分页（U11）：支持 myOnly/reviewQueue/chapter/type/nodeName/标题/难度，返回管理 VO。
     */
    IPage<LearningQuestionManageVO> pageManageVO(LearningQuestionQueryRequest request, User user);

    /**
     * 学生作答：服务端判分 -> 记录 attempt -> 写学习事件并更新掌握状态 -> 返回解析（永不含标准答案）
     */
    LearningAnswerResultVO answerQuestion(LearningAnswerRequest request, User user);

    /**
     * 审核流转：SUBMIT（作者）、APPROVE/REJECT/PUBLISH/OFFLINE（管理员）
     */
    boolean reviewQuestion(LearningQuestionReviewRequest request, User operator);

    /**
     * JSON 导入预览（Phase 8 §12.2）：逐项校验节点、题型、答案格式与解析，不落库
     */
    ImportPreviewVO importPreview(String content);

    /**
     * JSON 导入提交（Phase 8 §12.2）：仅导入校验通过项为 DRAFT，返回新建 id
     */
    ImportResultVO importCommit(String content, User author);

    /**
     * 题目练习目录聚合（阶段 4）：章节 -> 知识点 -> 题目数/完成数/待复习数。
     * 仅统计 PUBLISHED 且未逻辑删除的题目；attempted/needsReview/correct 与 studyState
     * 过滤同口径（本人最新有效作答）。chapter 可选：空白返回全部；"未归类"命中空白章节；
     * 其余按章节原值精确匹配。学生与管理员同视图，均为本人数据口径。
     */
    LearningCatalogVO questionCatalog(String chapter, User user);

    /**
     * 教材目录口径的题目练习目录聚合：稳定 course/section/unit 范围 + 学习状态过滤，
     * 返回小节树、已发布单元题量、范围内节点及本人状态统计。
     */
    LearningCatalogVO questionCatalog(LearningCatalogRequest request, User user);
}
