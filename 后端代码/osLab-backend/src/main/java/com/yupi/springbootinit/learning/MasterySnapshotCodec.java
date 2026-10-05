package com.yupi.springbootinit.learning;

import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.bkt.BktModel;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.enums.learning.MasteryStatusEnum;
import org.apache.commons.lang3.StringUtils;

/**
 * 掌握快照编解码（Phase 7 §11.2）：learning_event.snapshotBefore/After 的 JSON 契约。
 *
 * <p>快照固定 7 字段：pKnown, confidence, conceptScore, practiceScore,
 * conceptEvidenceCount, practiceEvidenceCount, status。
 * 纯函数、确定性；历史事件缺快照时展示已记录数值并标注“历史细分状态未记录”，
 * 不按分数阈值凭空推导状态（GREEN 依赖两类证据，无法从单一分数还原）。
 */
public final class MasterySnapshotCodec {

    private MasterySnapshotCodec() {
    }

    /**
     * 快照结构（公共字段直接序列化，与 SQL 注释中的 7 字段一一对应）
     */
    public static class MasterySnapshot {
        public Double pKnown;
        public Double confidence;
        public Double conceptScore;
        public Double practiceScore;
        public Integer conceptEvidenceCount;
        public Integer practiceEvidenceCount;
        public String status;
    }

    /**
     * 从掌握状态行提取完整快照；行不存在时返回默认未学习状态（GRAY / 初始值）。
     */
    public static MasterySnapshot snapshotOf(LearningMastery mastery) {
        MasterySnapshot snapshot = new MasterySnapshot();
        if (mastery == null) {
            snapshot.pKnown = BktModel.DEFAULT_P_INIT;
            snapshot.confidence = 0.0;
            snapshot.conceptScore = 0.0;
            snapshot.practiceScore = 0.0;
            snapshot.conceptEvidenceCount = 0;
            snapshot.practiceEvidenceCount = 0;
            snapshot.status = MasteryStatusEnum.GRAY.name();
            return snapshot;
        }
        snapshot.pKnown = mastery.getPKnown() == null ? BktModel.DEFAULT_P_INIT : mastery.getPKnown();
        snapshot.confidence = mastery.getConfidence() == null ? 0.0 : mastery.getConfidence();
        snapshot.conceptScore = mastery.getConceptScore() == null ? 0.0 : mastery.getConceptScore();
        snapshot.practiceScore = mastery.getPracticeScore() == null ? 0.0 : mastery.getPracticeScore();
        snapshot.conceptEvidenceCount = mastery.getConceptEvidenceCount() == null
                ? 0 : mastery.getConceptEvidenceCount();
        snapshot.practiceEvidenceCount = mastery.getPracticeEvidenceCount() == null
                ? 0 : mastery.getPracticeEvidenceCount();
        snapshot.status = StringUtils.isBlank(mastery.getStatus())
                ? MasteryStatusEnum.GRAY.name() : mastery.getStatus();
        return snapshot;
    }

    public static String toJson(MasterySnapshot snapshot) {
        return JSONUtil.toJsonStr(snapshot);
    }

    /**
     * 解析快照 JSON；空串或格式异常返回 null（调用方按“历史细分状态未记录”处理）。
     */
    public static MasterySnapshot parse(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.toBean(json, MasterySnapshot.class);
        } catch (Exception e) {
            return null;
        }
    }
}
