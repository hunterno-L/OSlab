package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * 冻结契约 NextAction.targetRef / returnRef 的目标引用。
 *
 * <p>{@code kind} 仅允许 UNIT / QUESTION / SIMULATION / MATERIAL，
 * 且必须是当前用户可访问的已发布对象——规划器不得引用草稿、下线或越权内容。
 */
@Data
public class TargetRef implements Serializable {

    public static final String KIND_UNIT = "UNIT";
    public static final String KIND_QUESTION = "QUESTION";
    public static final String KIND_SIMULATION = "SIMULATION";
    public static final String KIND_MATERIAL = "MATERIAL";

    private String kind;

    /** 单元编码 / 题目 id 字符串 / 模拟算法键 / 资料块标识。 */
    private String code;

    /** 段落或步骤锚点，可为 null。 */
    private String anchor;

    public TargetRef() {
    }

    public TargetRef(String kind, String code, String anchor) {
        this.kind = kind;
        this.code = code;
        this.anchor = anchor;
    }

    public static boolean isValidKind(String kind) {
        return KIND_UNIT.equals(kind) || KIND_QUESTION.equals(kind)
                || KIND_SIMULATION.equals(kind) || KIND_MATERIAL.equals(kind);
    }
}
