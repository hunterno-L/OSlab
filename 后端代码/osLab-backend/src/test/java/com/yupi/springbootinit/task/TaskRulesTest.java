package com.yupi.springbootinit.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TaskRulesTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void manualRulesFollowContentConfiguration() {
        Assertions.assertTrue(TaskContentService.manualRequired(TaskConstants.ITEM_TEXT,
                content("requireText", true)));
        Assertions.assertFalse(TaskContentService.manualRequired(TaskConstants.ITEM_TEXT,
                content("requireConfirm", true)));
        Assertions.assertTrue(TaskContentService.manualRequired(TaskConstants.ITEM_MATERIAL,
                content("requireFile", true)));
        Assertions.assertFalse(TaskContentService.manualRequired(TaskConstants.ITEM_MATERIAL,
                content("requireConfirm", true)));
        Assertions.assertTrue(TaskContentService.manualRequired(TaskConstants.ITEM_EXPERIMENT,
                content("manualReview", true)));
        Assertions.assertFalse(TaskContentService.manualRequired(TaskConstants.ITEM_QUESTION,
                content("manualReview", true)));
    }

    @Test
    void experimentPassRuleOnlyAppliesToAutomaticExperiments() {
        Assertions.assertTrue(TaskContentService.requirePass(TaskConstants.ITEM_EXPERIMENT,
                content("requirePass", true)));
        Assertions.assertFalse(TaskContentService.requirePass(TaskConstants.ITEM_EXPERIMENT,
                content("requirePass", false)));
        Assertions.assertFalse(TaskContentService.requirePass(TaskConstants.ITEM_QUESTION,
                content("requirePass", true)));
    }

    private ObjectNode content(String field, boolean value) {
        ObjectNode node = JSON.createObjectNode();
        node.put(field, value);
        return node;
    }
}
