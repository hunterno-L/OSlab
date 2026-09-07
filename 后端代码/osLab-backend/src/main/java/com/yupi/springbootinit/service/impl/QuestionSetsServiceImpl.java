package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.model.entity.QuestionSets;
import com.yupi.springbootinit.service.QuestionSetsService;
import com.yupi.springbootinit.mapper.QuestionSetsMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author 张娜
* @description 针对表【question_sets(题目集表)】的数据库操作Service实现
* @createDate 2025-04-04 18:05:47
*/
@Service
public class QuestionSetsServiceImpl extends ServiceImpl<QuestionSetsMapper, QuestionSets>
    implements QuestionSetsService{

    @Override
    public List<QuestionSets> allQuestionSets() {
        return baseMapper.selectList(null);
    }
}




