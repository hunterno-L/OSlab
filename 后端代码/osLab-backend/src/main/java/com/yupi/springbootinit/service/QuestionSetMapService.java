package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.entity.QuestionSetMap;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 张娜
* @description 针对表【question_set_map(题目与题目集关联表)】的数据库操作Service
* @createDate 2025-04-04 18:05:16
*/
public interface QuestionSetMapService extends IService<QuestionSetMap> {

    List<Long> getQuestionIds(int setId);

    QuestionSetMap getByQuestionId(long questionId);
}
