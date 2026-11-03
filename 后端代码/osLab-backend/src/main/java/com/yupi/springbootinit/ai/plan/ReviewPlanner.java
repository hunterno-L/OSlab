package com.yupi.springbootinit.ai.plan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.mapper.LearningReviewItemMapper;
import com.yupi.springbootinit.model.entity.LearningReviewItem;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 复习阶梯（计划 §5.6）：可解释的 1 / 3 / 7 天安排。
 *
 * <p>与长期 BKT 状态相互独立：答对一道重复题不会把知识点刷成掌握，
 * 复习到期状态也不会反过来改写 learning_mastery。
 *
 * <p>幂等由 {@code (userId, reviewKey)} 唯一键保证：同一次失败观测
 * 重复提交、换会话或换模型都不会新增复习项，因此也无法刷出更多"到期任务"。
 *
 * <p>时间估算与阶段间隔是可配置启发规则，不包装成训练过的最优策略。
 */
@Slf4j
@Service
public class ReviewPlanner {

    private static final int MAX_STAGE = LearningReviewItem.LADDER_DAYS.length - 1;

    @Resource
    private LearningReviewItemMapper reviewMapper;

    @Data
    public static class ReviewTarget implements Serializable {
        private Long userId;
        private String courseCode;
        private String skillCode;
        private String nodeCode;
        private String activityKind;
        private String activityCode;
        private Long sourceEventId;
    }

    /**
     * 为一次真实失败观测安排第一阶梯（1 天后）。
     * 只有独立观测到的失败才安排复习：诊断事件与带提示作答不进复习队列。
     */
    public LearningReviewItem scheduleFirstStage(ReviewTarget target, Date now) {
        return upsert(target, 0, now);
    }

    /**
     * 完成一次复习：标记 DONE 并按阶梯安排下一阶段（1 → 3 → 7 天）。
     *
     * @param correct 本次复习是否答对（服务端判分结果）；答错则重排当前阶段，
     *                答对且已到最后一阶则不再新增
     */
    public LearningReviewItem complete(LearningReviewItem item, boolean correct, Date now) {
        if (item == null) {
            return null;
        }
        item.setStatus(LearningReviewItem.STATUS_DONE);
        item.setResultJson(AiJson.write(java.util.Collections.singletonMap("correct", correct)));
        reviewMapper.updateById(item);
        if (!correct) {
            // 答错：留在当前阶段重新到期，不因为"做过一次"就推进阶梯
            return upsert(targetOf(item), item.getStage() == null ? 0 : item.getStage(), now);
        }
        int stage = item.getStage() == null ? 0 : item.getStage();
        if (stage >= MAX_STAGE) {
            return null;
        }
        return upsert(targetOf(item), stage + 1, now);
    }

    /** 到期队列：PENDING 且 dueAt <= now，按到期时间升序。 */
    public List<LearningReviewItem> due(Long userId, String courseCode, Date now, int limit) {
        if (userId == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<LearningReviewItem> query = new LambdaQueryWrapper<LearningReviewItem>()
                .eq(LearningReviewItem::getUserId, userId)
                .eq(LearningReviewItem::getStatus, LearningReviewItem.STATUS_PENDING)
                .le(LearningReviewItem::getDueAt, now == null ? new Date() : now)
                .orderByAsc(LearningReviewItem::getDueAt)
                .orderByAsc(LearningReviewItem::getId);
        if (StringUtils.isNotBlank(courseCode)) {
            query.eq(LearningReviewItem::getCourseCode, courseCode);
        }
        query.last("limit " + Math.max(1, limit));
        try {
            return reviewMapper.selectList(query);
        } catch (RuntimeException e) {
            log.warn("[ai-review] due list unavailable user={}: {}", userId, e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    public int dueCount(Long userId, String courseCode, Date now) {
        if (userId == null) {
            return 0;
        }
        try {
            Long count = reviewMapper.selectCount(new LambdaQueryWrapper<LearningReviewItem>()
                    .eq(LearningReviewItem::getUserId, userId)
                    .eq(LearningReviewItem::getStatus, LearningReviewItem.STATUS_PENDING)
                    .eq(StringUtils.isNotBlank(courseCode), LearningReviewItem::getCourseCode, courseCode)
                    .le(LearningReviewItem::getDueAt, now == null ? new Date() : now));
            return count == null ? 0 : count.intValue();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public LearningReviewItem findById(Long userId, Long id) {
        if (userId == null || id == null) {
            return null;
        }
        LearningReviewItem item = reviewMapper.selectById(id);
        return item == null || !item.getUserId().equals(userId) ? null : item;
    }

    private LearningReviewItem upsert(ReviewTarget target, int stage, Date now) {
        if (target == null || target.getUserId() == null) {
            return null;
        }
        int safeStage = Math.max(0, Math.min(stage, MAX_STAGE));
        String reviewKey = reviewKey(target, safeStage);
        LearningReviewItem existing = findByKey(target.getUserId(), reviewKey);
        if (existing != null) {
            return existing;
        }
        LearningReviewItem item = new LearningReviewItem();
        item.setUserId(target.getUserId());
        item.setCourseCode(StringUtils.defaultString(target.getCourseCode()));
        item.setReviewKey(reviewKey);
        item.setSkillCode(target.getSkillCode());
        item.setNodeCode(target.getNodeCode());
        item.setActivityKind(StringUtils.defaultIfBlank(target.getActivityKind(), "QUESTION"));
        item.setActivityCode(StringUtils.defaultString(target.getActivityCode()));
        item.setSourceEventId(target.getSourceEventId());
        item.setStage(safeStage);
        item.setDueAt(plusDays(now == null ? new Date() : now, LearningReviewItem.ladderDays(safeStage)));
        item.setStatus(LearningReviewItem.STATUS_PENDING);
        item.setIsDelete(0);
        try {
            reviewMapper.insert(item);
            return item;
        } catch (DuplicateKeyException e) {
            // 并发安排：唯一键冲突即表示已存在，复用而不是新增
            return findByKey(target.getUserId(), reviewKey);
        }
    }

    private LearningReviewItem findByKey(Long userId, String reviewKey) {
        List<LearningReviewItem> found = reviewMapper.selectList(new LambdaQueryWrapper<LearningReviewItem>()
                .eq(LearningReviewItem::getUserId, userId)
                .eq(LearningReviewItem::getReviewKey, reviewKey)
                .last("limit 1"));
        return found.isEmpty() ? null : found.get(0);
    }

    private static ReviewTarget targetOf(LearningReviewItem item) {
        ReviewTarget target = new ReviewTarget();
        target.setUserId(item.getUserId());
        target.setCourseCode(item.getCourseCode());
        target.setSkillCode(item.getSkillCode());
        target.setNodeCode(item.getNodeCode());
        target.setActivityKind(item.getActivityKind());
        target.setActivityCode(item.getActivityCode());
        target.setSourceEventId(item.getSourceEventId());
        return target;
    }

    /** 幂等键：同一来源事件在同一阶段只有一条复习项。 */
    static String reviewKey(ReviewTarget target, int stage) {
        String subject = StringUtils.defaultIfBlank(target.getNodeCode(),
                StringUtils.defaultIfBlank(target.getSkillCode(), "unknown"));
        return subject + ":" + (target.getSourceEventId() == null ? "0" : target.getSourceEventId())
                + ":" + stage;
    }

    static Date plusDays(Date from, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(from);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    /** 阶梯说明，供轨迹与界面展示（可解释，不声称是最优策略）。 */
    public static String ladderLabel(int stage) {
        return LearningReviewItem.ladderDays(stage) + " 天后复习";
    }
}
