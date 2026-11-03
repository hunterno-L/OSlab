package com.yupi.springbootinit.ai.plan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.mapper.LearningReviewItemMapper;
import com.yupi.springbootinit.model.entity.LearningReviewItem;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 到期复习阶梯的回归（计划 §5.6）。
 *
 * <p>要点：1/3/7 天阶梯、同一来源事件在同一阶段只有一条复习项（幂等）、
 * 答错重排当前阶段而不是推进阶梯、答对到最后一阶后不再新增。
 */
class ReviewPlannerTest {

    private final LearningReviewItemMapper reviewMapper = mock(LearningReviewItemMapper.class);
    private final ReviewPlanner planner = new ReviewPlanner();

    private static final Long USER = 7L;
    private static final String COURSE = "course.operating-systems";
    private Date now;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "review-planner-test");
        TableInfoHelper.initTableInfo(assistant, LearningReviewItem.class);
        ReflectionTestUtils.setField(planner, "reviewMapper", reviewMapper);
        when(reviewMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reviewMapper.insert(any())).thenAnswer(invocation -> {
            LearningReviewItem item = invocation.getArgument(0);
            item.setId(500L);
            return 1;
        });
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.NOVEMBER, 3, 9, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        now = calendar.getTime();
    }

    @Test
    void firstStageIsDueOneDayAfterTheFailure() {
        LearningReviewItem item = planner.scheduleFirstStage(target(9001L), now);

        assertNotNull(item);
        assertEquals(0, item.getStage());
        assertEquals(LearningReviewItem.STATUS_PENDING, item.getStatus());
        assertEquals(COURSE, item.getCourseCode());
        assertEquals(9001L, item.getSourceEventId());
        assertEquals(daysFromNow(1), stripTime(item.getDueAt()), "第一阶梯是 1 天后");
    }

    @Test
    void ladderFollowsOneThreeSevenDays() {
        assertArrayEquals(new int[]{1, 3, 7}, LearningReviewItem.LADDER_DAYS);
        assertEquals(1, LearningReviewItem.ladderDays(0));
        assertEquals(3, LearningReviewItem.ladderDays(1));
        assertEquals(7, LearningReviewItem.ladderDays(2));
        assertEquals("1 天后复习", ReviewPlanner.ladderLabel(0));
        assertEquals("3 天后复习", ReviewPlanner.ladderLabel(1));
        assertEquals("7 天后复习", ReviewPlanner.ladderLabel(2));
    }

    @Test
    void sameSourceEventAtSameStageIsIdempotent() {
        LearningReviewItem existing = item(0, 9001L);
        when(reviewMapper.selectList(any())).thenReturn(List.of(existing));

        LearningReviewItem result = planner.scheduleFirstStage(target(9001L), now);

        assertSame(existing, result, "重复安排必须复用同一条复习项");
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void concurrentInsertReusesTheExistingRow() {
        LearningReviewItem concurrent = item(0, 9001L);
        doThrow(new DuplicateKeyException("uk_review_item_key")).when(reviewMapper).insert(any());
        when(reviewMapper.selectList(any())).thenReturn(Collections.emptyList(), List.of(concurrent));

        assertSame(concurrent, planner.scheduleFirstStage(target(9001L), now));
    }

    @Test
    void differentSourceEventsGetTheirOwnReviewItems() {
        planner.scheduleFirstStage(target(9001L), now);
        planner.scheduleFirstStage(target(9002L), now);

        ArgumentCaptor<LearningReviewItem> captor = ArgumentCaptor.forClass(LearningReviewItem.class);
        verify(reviewMapper, times(2)).insert(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).getReviewKey(),
                captor.getAllValues().get(1).getReviewKey());
    }

    @Test
    void correctReviewAdvancesToTheNextStage() {
        LearningReviewItem stage0 = item(0, 9001L);
        when(reviewMapper.updateById(any())).thenReturn(1);

        LearningReviewItem next = planner.complete(stage0, true, now);

        assertEquals(LearningReviewItem.STATUS_DONE, stage0.getStatus());
        assertNotNull(next);
        assertEquals(1, next.getStage());
        assertEquals(daysFromNow(3), stripTime(next.getDueAt()), "第二阶梯是 3 天后");
    }

    @Test
    void wrongReviewStaysOnTheSameStageInsteadOfAdvancing() {
        LearningReviewItem stage1 = item(1, 9001L);
        when(reviewMapper.updateById(any())).thenReturn(1);

        LearningReviewItem next = planner.complete(stage1, false, now);

        assertEquals(LearningReviewItem.STATUS_DONE, stage1.getStatus());
        assertNotNull(next);
        assertEquals(1, next.getStage(), "答错不得推进阶梯");
        assertEquals(daysFromNow(3), stripTime(next.getDueAt()), "答错按当前阶段重新到期");
    }

    @Test
    void lastStageCompletionDoesNotCreateMoreReviews() {
        LearningReviewItem last = item(2, 9001L);
        when(reviewMapper.updateById(any())).thenReturn(1);

        assertNull(planner.complete(last, true, now), "1/3/7 天走完就不再排新复习");
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void completeWithoutItemIsANoOp() {
        assertNull(planner.complete(null, true, now));
        verifyNoInteractions(reviewMapper);
    }

    @Test
    void upsertWithoutUserIsRefused() {
        ReviewPlanner.ReviewTarget target = new ReviewPlanner.ReviewTarget();
        target.setNodeCode("os.memory.lru");
        assertNull(planner.scheduleFirstStage(target, now));
        assertNull(planner.scheduleFirstStage(null, now));
        verifyNoInteractions(reviewMapper);
    }

    @Test
    void reviewKeyIsScopedByNodeEventAndStage() {
        ReviewPlanner.ReviewTarget target = target(9001L);
        assertEquals("os.memory.lru:9001:0", ReviewPlanner.reviewKey(target, 0));
        assertEquals("os.memory.lru:9001:2", ReviewPlanner.reviewKey(target, 2));
        target.setNodeCode(null);
        target.setSkillCode("os.memory.lru");
        assertEquals("os.memory.lru:9001:0", ReviewPlanner.reviewKey(target, 0), "缺节点编码时退回技能编码");
    }

    @Test
    void stageIsClampedIntoTheLadder() {
        LearningReviewItem item = planner.scheduleFirstStage(target(9001L), now);
        assertNotNull(item);
        // upsert 内部会把越界阶段夹到 [0, MAX_STAGE]，这里验证键不会带上越界阶段
        assertFalse(item.getReviewKey().endsWith(":9"), "阶段必须被夹在阶梯范围内");
    }

    // region fixtures

    private ReviewPlanner.ReviewTarget target(Long sourceEventId) {
        ReviewPlanner.ReviewTarget target = new ReviewPlanner.ReviewTarget();
        target.setUserId(USER);
        target.setCourseCode(COURSE);
        target.setSkillCode("os.memory.lru");
        target.setNodeCode("os.memory.lru");
        target.setActivityKind("QUESTION");
        target.setActivityCode("101");
        target.setSourceEventId(sourceEventId);
        return target;
    }

    private LearningReviewItem item(int stage, Long sourceEventId) {
        LearningReviewItem item = new LearningReviewItem();
        item.setId(500L);
        item.setUserId(USER);
        item.setCourseCode(COURSE);
        item.setReviewKey("os.memory.lru:" + sourceEventId + ":" + stage);
        item.setSkillCode("os.memory.lru");
        item.setNodeCode("os.memory.lru");
        item.setActivityKind("QUESTION");
        item.setActivityCode("101");
        item.setSourceEventId(sourceEventId);
        item.setStage(stage);
        item.setStatus(LearningReviewItem.STATUS_PENDING);
        item.setDueAt(ReviewPlanner.plusDays(now, LearningReviewItem.ladderDays(stage)));
        item.setIsDelete(0);
        return item;
    }

    /** 只比较日期部分，避免测试在毫秒边界上抖动。 */
    private String stripTime(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR) + "-" + calendar.get(Calendar.MONTH) + "-"
                + calendar.get(Calendar.DAY_OF_MONTH);
    }

    private String daysFromNow(int days) {
        return stripTime(ReviewPlanner.plusDays(now, days));
    }

    // endregion
}
