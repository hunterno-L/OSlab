package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.yupi.springbootinit.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.QuestionSubmitVO;

/**
* @author 张娜
* @description 针对表【question_submit(题目提交)】的数据库操作Service
* @createDate 2024-08-14 19:56:12
*/
public interface QuestionSubmitService extends IService<QuestionSubmit> {
    /**
     * 题目提交
     *
     * @param questionSubmitAddRequest 题目提交信息
     * @param loginUser
     * @return
     */
    long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser);

    /**
     * 获取查询条件
     *
     * @param questionSubmitQueryRequest
     * @return
     */
    QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest questionSubmitQueryRequest);

    /**
     * 获取题目封装
     *
     * @param questionSubmit
     * @param loginUser
     * @return
     */
    QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit, User loginUser);

    /**
     * 获取提交详情（本人约束：学生仅能查询自己的提交详情，管理员不受限）
     *
     * @param id        提交 id
     * @param loginUser 当前登录用户
     * @return 提交详情（含判题信息）
     */
    QuestionSubmitVO getQuestionSubmitDetail(long id, User loginUser);

    /**
     * 分页获取题目封装
     *
     * @param questionSubmitPage
     * @param loginUser
     * @return
     */
    Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser);
}
