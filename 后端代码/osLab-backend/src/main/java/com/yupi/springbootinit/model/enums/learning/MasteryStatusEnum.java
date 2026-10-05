package com.yupi.springbootinit.model.enums.learning;

/**
 * 知识节点掌握状态枚举（个人图谱节点颜色）
 * 契约 §3.3 固定阈值：
 * GRAY 灰（未学习）：无任何证据；
 * RED 红（薄弱）：pKnown &lt; 0.35；
 * YELLOW 琥珀（待巩固）：0.35 &le; pKnown &lt; 0.70；
 * GREEN 绿（已掌握）：pKnown &ge; 0.70 且概念/实践两类证据齐全。
 */
public enum MasteryStatusEnum {

    GRAY("未学习"),
    RED("薄弱"),
    YELLOW("待巩固"),
    GREEN("已掌握");

    /** 状态阈值：低于此为 RED */
    public static final double RED_THRESHOLD = 0.35;
    /** 状态阈值：达到此且两类证据齐全为 GREEN */
    public static final double GREEN_THRESHOLD = 0.70;

    private final String text;

    MasteryStatusEnum(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /**
     * 由综合掌握概率与两类证据数推导节点状态（确定性规则，可复算）。
     * 契约要求 GREEN 必须至少有两类证据；证据数为空视为无证据。
     *
     * @param pKnown                综合 BKT 掌握概率（可空）
     * @param conceptEvidenceCount  概念证据数（可空）
     * @param practiceEvidenceCount 实践证据数（可空）
     */
    public static MasteryStatusEnum derive(Double pKnown,
                                           Integer conceptEvidenceCount,
                                           Integer practiceEvidenceCount) {
        int concept = conceptEvidenceCount == null ? 0 : conceptEvidenceCount;
        int practice = practiceEvidenceCount == null ? 0 : practiceEvidenceCount;
        int totalEvidence = concept + practice;
        if (pKnown == null || totalEvidence <= 0) {
            return GRAY;
        }
        if (pKnown < RED_THRESHOLD) {
            return RED;
        }
        if (pKnown < GREEN_THRESHOLD) {
            return YELLOW;
        }
        // 达到 GREEN 阈值还需要两类证据（概念 + 实践）齐全
        boolean bothKinds = concept > 0 && practice > 0;
        return bothKinds ? GREEN : YELLOW;
    }
}
