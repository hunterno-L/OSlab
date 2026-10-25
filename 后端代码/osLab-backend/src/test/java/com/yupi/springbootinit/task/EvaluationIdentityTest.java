package com.yupi.springbootinit.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EvaluationIdentityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ObjectNode question() {
        ObjectNode content = JSON.createObjectNode();
        content.put("title", "并发是什么");
        content.put("content", "选择正确描述");
        content.put("type", "MULTIPLE");
        content.set("options", JSON.createArrayNode()
                .add(JSON.createObjectNode().put("key", "B").put("value", "只有一个任务"))
                .add(JSON.createObjectNode().put("key", "A").put("value", "多个任务交替推进")));
        content.set("answer", JSON.createArrayNode().add("C").add("A"));
        content.set("nodeCodes", JSON.createArrayNode().add("os.overview.concurrency"));
        return content;
    }

    @Test
    void sameContentAndSourceKeepIdentityRegardlessOfOrdering() {
        String first = EvaluationIdentity.forQuestion(question(), "q.ext.001");
        ObjectNode reordered = question();
        reordered.set("options", JSON.createArrayNode()
                .add(JSON.createObjectNode().put("key", "A").put("value", "多个任务交替推进"))
                .add(JSON.createObjectNode().put("key", "B").put("value", "只有一个任务")));
        reordered.set("answer", JSON.createArrayNode().add("A").add("C"));
        String second = EvaluationIdentity.forQuestion(reordered, "q.ext.001");
        Assertions.assertEquals(first, second);
        Assertions.assertTrue(first.startsWith("q:q.ext.001:"));
    }

    @Test
    void changedJudgingContentChangesIdentity() {
        String base = EvaluationIdentity.forQuestion(question(), "q.ext.001");
        ObjectNode changed = question();
        changed.put("answer", "A");
        Assertions.assertNotEquals(base, EvaluationIdentity.forQuestion(changed, "q.ext.001"));
        ObjectNode otherSource = question();
        Assertions.assertNotEquals(base, EvaluationIdentity.forQuestion(otherSource, "q.ext.002"));
    }

    @Test
    void authoredQuestionUsesAuthoredPrefix() {
        String identity = EvaluationIdentity.forQuestion(question(), null);
        Assertions.assertTrue(identity.startsWith("q:authored:"));
    }

    @Test
    void experimentIdentityTracksFrozenJudgeCase() {
        ObjectNode content = JSON.createObjectNode();
        content.put("title", "求和");
        content.put("content", "输出 a+b");
        content.set("judgeCase", JSON.createArrayNode()
                .add(JSON.createObjectNode().put("input", "1 2").put("output", "3")));
        content.set("judgeConfig", JSON.createObjectNode().put("timeLimit", 1000).put("memoryLimit", 256));
        content.set("languages", JSON.createArrayNode().add("java"));
        String base = EvaluationIdentity.forExperiment(content);
        ObjectNode changed = content.deepCopy();
        ((ObjectNode) changed.path("judgeCase").path(0)).put("output", "4");
        Assertions.assertNotEquals(base, EvaluationIdentity.forExperiment(changed));
        Assertions.assertTrue(base.startsWith("x:"));
    }
}
