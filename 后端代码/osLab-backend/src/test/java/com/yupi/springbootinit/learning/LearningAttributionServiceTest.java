package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.learning.LearningAttributionService.AttributionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 7 契约测试（§11.1）：规则归因统一区分普通题错误/WA/RE/TLE/编译错误/系统错误，
 * 系统错误不计学生知识失误（knowledgeImpact=false），推荐动作必须具体。
 */
class LearningAttributionServiceTest {

    @Test
    void attemptWrong_mapsToWrongAnswer() {
        AttributionResult r = LearningAttributionService.attribute(
                "ATTEMPT", null, "LRU 页面置换算法");
        Assertions.assertEquals("WRONG_ANSWER", r.getErrorType());
        Assertions.assertTrue(r.getConfidence() > 0.5);
        Assertions.assertTrue(r.getKnowledgeImpact());
        Assertions.assertFalse(r.getEvidence().isEmpty());
        Assertions.assertFalse(r.getRecommendedAction().isBlank());
    }

    @Test
    void judgeWrongAnswer_mapsToWrongAnswer() {
        AttributionResult r = LearningAttributionService.attribute(
                "JUDGE", "Wrong Answer", "LRU 页面置换算法");
        Assertions.assertEquals("WRONG_ANSWER", r.getErrorType());
        Assertions.assertTrue(r.getKnowledgeImpact());
    }

    @Test
    void judgeRuntimeError_mapsToRuntimeError() {
        AttributionResult r = LearningAttributionService.attribute(
                "JUDGE", "Runtime Error", "LRU 页面置换算法");
        Assertions.assertEquals("RUNTIME_ERROR", r.getErrorType());
        Assertions.assertTrue(r.getKnowledgeImpact());
    }

    @Test
    void judgeTimeLimit_mapsToTimeLimit() {
        AttributionResult r = LearningAttributionService.attribute(
                "JUDGE", "Time Limit Exceed", "LRU 页面置换算法");
        Assertions.assertEquals("TIME_LIMIT", r.getErrorType());
    }

    @Test
    void judgeCompileError_mapsToCompileError() {
        AttributionResult r = LearningAttributionService.attribute(
                "JUDGE", "Compile Error", "LRU 页面置换算法");
        Assertions.assertEquals("COMPILE_ERROR", r.getErrorType());
    }

    @Test
    void systemError_hasNoKnowledgeImpact() {
        AttributionResult r = LearningAttributionService.attribute(
                "JUDGE", "SYSTEM_ERROR", "LRU 页面置换算法");
        Assertions.assertEquals("SYSTEM_ERROR", r.getErrorType());
        Assertions.assertFalse(r.getKnowledgeImpact(),
                "系统故障不得作为学生知识失误证据");
    }

    @Test
    void unknownMessage_fallsBackToWrongAnswerForJudge() {
        AttributionResult r = LearningAttributionService.attribute(
                "JUDGE", "SomeOtherMessage", "LRU 页面置换算法");
        Assertions.assertEquals("WRONG_ANSWER", r.getErrorType());
    }

    @Test
    void resultContainsNodeAndStructuredEvidence() {
        AttributionResult r = LearningAttributionService.attribute(
                "ATTEMPT", null, "LRU 页面置换算法");
        Assertions.assertEquals("LRU 页面置换算法", r.getNodeName());
        Assertions.assertTrue(r.getEvidence().stream().anyMatch(e -> e.contains("LRU 页面置换算法")));
    }
}
