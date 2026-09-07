package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.entity.QuestionSets;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 张娜
* @description 针对表【question_sets(题目集表)】的数据库操作Service
* @createDate 2025-04-04 18:05:47
*/
public interface QuestionSetsService extends IService<QuestionSets> {
    List<QuestionSets> allQuestionSets();
}
