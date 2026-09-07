package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.model.entity.QuestionSetMap;
import com.yupi.springbootinit.service.QuestionSetMapService;
import com.yupi.springbootinit.mapper.QuestionSetMapMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* @author 张娜
* @description 针对表【question_set_map(题目与题目集关联表)】的数据库操作Service实现
* @createDate 2025-04-04 18:05:16
*/
@Service
public class QuestionSetMapServiceImpl extends ServiceImpl<QuestionSetMapMapper, QuestionSetMap>
    implements QuestionSetMapService{

    @Override
    public List<Long> getQuestionIds(int setId) {
        LambdaQueryWrapper<QuestionSetMap> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(QuestionSetMap::getSetId, setId);
        List<QuestionSetMap> questionSetMaps = baseMapper.selectList(lambdaQueryWrapper);
        List<Long> questionIds = questionSetMaps.stream()
                .map(QuestionSetMap::getQuestionId)  // 假设 questionId 是 Long 类型
                .collect(Collectors.toList());
        return questionIds;
    }

    @Override
    public QuestionSetMap getByQuestionId(long questionId) {
        LambdaQueryWrapper<QuestionSetMap> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(QuestionSetMap::getQuestionId,questionId);
        QuestionSetMap questionSetMap = baseMapper.selectOne(lambdaQueryWrapper);
        return questionSetMap;
    }
}




