package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.bkt.BktModel;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.enums.learning.MasteryStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 7 §11.2 快照编解码契约测试：7 字段完整、往返无损、缺省状态正确。
 */
class MasterySnapshotCodecTest {

    @Test
    void snapshotOfNullIsDefaultUnlearned() {
        MasterySnapshotCodec.MasterySnapshot snap = MasterySnapshotCodec.snapshotOf(null);
        Assertions.assertEquals(BktModel.DEFAULT_P_INIT, snap.pKnown);
        Assertions.assertEquals(0.0, snap.confidence);
        Assertions.assertEquals(0.0, snap.conceptScore);
        Assertions.assertEquals(0.0, snap.practiceScore);
        Assertions.assertEquals(0, snap.conceptEvidenceCount);
        Assertions.assertEquals(0, snap.practiceEvidenceCount);
        Assertions.assertEquals(MasteryStatusEnum.GRAY.name(), snap.status);
    }

    @Test
    void snapshotOfMasteryCopiesAllSevenFields() {
        LearningMastery mastery = new LearningMastery();
        mastery.setPKnown(0.66);
        mastery.setConfidence(0.5);
        mastery.setConceptScore(0.8);
        mastery.setPracticeScore(0.4);
        mastery.setConceptEvidenceCount(3);
        mastery.setPracticeEvidenceCount(2);
        mastery.setStatus(MasteryStatusEnum.YELLOW.name());
        MasterySnapshotCodec.MasterySnapshot snap = MasterySnapshotCodec.snapshotOf(mastery);
        Assertions.assertEquals(0.66, snap.pKnown);
        Assertions.assertEquals(0.5, snap.confidence);
        Assertions.assertEquals(0.8, snap.conceptScore);
        Assertions.assertEquals(0.4, snap.practiceScore);
        Assertions.assertEquals(3, snap.conceptEvidenceCount);
        Assertions.assertEquals(2, snap.practiceEvidenceCount);
        Assertions.assertEquals("YELLOW", snap.status);
    }

    @Test
    void roundTripKeepsAllFields() {
        LearningMastery mastery = new LearningMastery();
        mastery.setPKnown(0.42);
        mastery.setConfidence(0.25);
        mastery.setConceptScore(0.6);
        mastery.setPracticeScore(0.1);
        mastery.setConceptEvidenceCount(1);
        mastery.setPracticeEvidenceCount(1);
        mastery.setStatus("RED");
        String json = MasterySnapshotCodec.toJson(MasterySnapshotCodec.snapshotOf(mastery));
        MasterySnapshotCodec.MasterySnapshot parsed = MasterySnapshotCodec.parse(json);
        Assertions.assertNotNull(parsed);
        Assertions.assertEquals(0.42, parsed.pKnown);
        Assertions.assertEquals(0.25, parsed.confidence);
        Assertions.assertEquals(0.6, parsed.conceptScore);
        Assertions.assertEquals(0.1, parsed.practiceScore);
        Assertions.assertEquals(1, parsed.conceptEvidenceCount);
        Assertions.assertEquals(1, parsed.practiceEvidenceCount);
        Assertions.assertEquals("RED", parsed.status);
    }

    @Test
    void parseBlankOrInvalidReturnsNull() {
        Assertions.assertNull(MasterySnapshotCodec.parse(null));
        Assertions.assertNull(MasterySnapshotCodec.parse(""));
        Assertions.assertNull(MasterySnapshotCodec.parse("   "));
        Assertions.assertNull(MasterySnapshotCodec.parse("not-a-json{"));
    }
}
