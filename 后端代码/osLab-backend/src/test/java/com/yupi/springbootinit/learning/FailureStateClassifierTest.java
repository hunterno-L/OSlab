package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.enums.learning.LearningEventTypeEnum;
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Phase 7 §11.3 失败归类契约测试：
 * 当前失误 / 历史失误 / 已纠正 / 无失败；基线与系统错误不算学习活动。
 */
class FailureStateClassifierTest {

    private LearningEvent event(long id, String eventType, String resourceType, long resourceId, String result) {
        LearningEvent e = new LearningEvent();
        e.setId(id);
        e.setEventType(eventType);
        e.setResourceType(resourceType);
        e.setResourceId(resourceId);
        e.setResult(result);
        e.setIsFirst(1);
        return e;
    }

    @Test
    void emptyOrNullIsNone() {
        Assertions.assertEquals(FailureStateClassifier.NONE,
                FailureStateClassifier.classify(null).state);
        Assertions.assertEquals(FailureStateClassifier.NONE,
                FailureStateClassifier.classify(Collections.emptyList()).state);
    }

    @Test
    void noFailIsNone() {
        List<LearningEvent> events = Arrays.asList(
                event(1, "ATTEMPT", "LEARNING_QUESTION", 100, "PASS"),
                event(2, "SIMULATION", "SIMULATION", 0, "PASS"));
        Assertions.assertEquals(FailureStateClassifier.NONE,
                FailureStateClassifier.classify(events).state);
    }

    @Test
    void failWithoutLaterActivityIsCurrent() {
        List<LearningEvent> events = Arrays.asList(
                event(1, "ATTEMPT", "LEARNING_QUESTION", 100, "PASS"),
                event(2, "ATTEMPT", "LEARNING_QUESTION", 200, "FAIL"));
        FailureStateClassifier.Result r = FailureStateClassifier.classify(events);
        Assertions.assertEquals(FailureStateClassifier.CURRENT, r.state);
        Assertions.assertEquals(Long.valueOf(2), r.lastFail.getId());
        Assertions.assertNull(r.correctedBy);
    }

    @Test
    void sameResourceLaterPassIsCorrected() {
        List<LearningEvent> events = Arrays.asList(
                event(1, "ATTEMPT", "LEARNING_QUESTION", 200, "FAIL"),
                // 重复作答同题通过（非独立观测，但同资源纠错成立）
                event(2, "ATTEMPT", "LEARNING_QUESTION", 200, "PASS"));
        FailureStateClassifier.Result r = FailureStateClassifier.classify(events);
        Assertions.assertEquals(FailureStateClassifier.CORRECTED, r.state);
        Assertions.assertNotNull(r.correctedBy);
        Assertions.assertEquals(Long.valueOf(2), r.correctedBy.getId());
    }

    @Test
    void laterActivityOnOtherResourceIsHistorical() {
        List<LearningEvent> events = Arrays.asList(
                event(1, "ATTEMPT", "LEARNING_QUESTION", 200, "FAIL"),
                event(2, "SIMULATION", "SIMULATION", 0, "PASS"));
        Assertions.assertEquals(FailureStateClassifier.HISTORICAL,
                FailureStateClassifier.classify(events).state);
    }

    @Test
    void baselineAndSystemErrorAreNotLearningActivity() {
        LearningEvent baseline = event(2,
                LearningEventTypeEnum.STATE_BASELINE.name(), "STATE_BASELINE", 0,
                LearningResultEnum.BASELINE.name());
        baseline.setIsFirst(0);
        List<LearningEvent> events = new ArrayList<>(Arrays.asList(
                event(1, "ATTEMPT", "LEARNING_QUESTION", 200, "FAIL"),
                baseline,
                event(3, "JUDGE", "QUESTION_SUBMIT", 300, "SYSTEM_ERROR")));
        Assertions.assertEquals(FailureStateClassifier.CURRENT,
                FailureStateClassifier.classify(events).state);
    }

    @Test
    void onlyLatestFailCounts() {
        List<LearningEvent> events = Arrays.asList(
                event(1, "ATTEMPT", "LEARNING_QUESTION", 100, "FAIL"),
                // 更早的失败已被同资源通过纠正
                event(2, "ATTEMPT", "LEARNING_QUESTION", 100, "PASS"),
                // 最新失败（另一资源），之后无活动
                event(3, "JUDGE", "QUESTION_SUBMIT", 900, "FAIL"));
        FailureStateClassifier.Result r = FailureStateClassifier.classify(events);
        Assertions.assertEquals(FailureStateClassifier.CURRENT, r.state);
        Assertions.assertEquals(Long.valueOf(3), r.lastFail.getId());
    }
}
