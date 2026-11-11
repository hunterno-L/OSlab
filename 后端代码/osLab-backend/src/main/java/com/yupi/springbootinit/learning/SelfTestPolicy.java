package com.yupi.springbootinit.learning;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 自测锁定判定：只依赖测评会话表，供答题服务与导师守卫共用，避免循环依赖。
 *
 * <p>服务端以“本人 ACTIVE + SELF_TEST + 队列包含该题”为唯一事实源；
 * 不依赖前端是否还带着 runId，删掉参数也不能绕过。
 */
@Service
public class SelfTestPolicy {

    private static final int ACTIVE_RUN_SCAN_LIMIT = 20;

    @Resource
    private LearningAssessmentRunMapper assessmentRunMapper;

    /** 该题当前是否属于本人未交卷的整组自测。 */
    public LearningAssessmentRun activeSelfTestContaining(Long userId, Long questionId) {
        if (userId == null || questionId == null) {
            return null;
        }
        for (LearningAssessmentRun run : activeSelfTests(userId)) {
            if (queueIds(run).contains(questionId)) {
                return run;
            }
        }
        return null;
    }

    public boolean isLocked(Long userId, Long questionId) {
        return activeSelfTestContaining(userId, questionId) != null;
    }

    /** 本人所有 ACTIVE 的 SELF_TEST（最近 20 个，足够覆盖真实使用）。 */
    public List<LearningAssessmentRun> activeSelfTests(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return assessmentRunMapper.selectList(new LambdaQueryWrapper<LearningAssessmentRun>()
                .eq(LearningAssessmentRun::getUserId, userId)
                .eq(LearningAssessmentRun::getStatus, "ACTIVE")
                .eq(LearningAssessmentRun::getMode, "SELF_TEST")
                .orderByDesc(LearningAssessmentRun::getId)
                .last("limit " + ACTIVE_RUN_SCAN_LIMIT));
    }

    /** 队列 id 解析；损坏数据一律视为空队列，不抛给调用方。 */
    public List<Long> queueIds(LearningAssessmentRun run) {
        if (run == null || StringUtils.isBlank(run.getQuestionIdsJson())) {
            return new ArrayList<>();
        }
        try {
            List<Long> ids = new ArrayList<>(new LinkedHashSet<>(
                    JSONUtil.toList(run.getQuestionIdsJson(), Long.class)));
            ids.removeIf(id -> id == null);
            return ids;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    public static boolean isSelfTest(LearningAssessmentRun run) {
        return run != null && "SELF_TEST".equals(run.getMode());
    }
}
