package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 最近失败证据（指向真实资源，Phase 7 归因在此基础上增强）
 */
@Data
public class LastFailureVO implements Serializable {
    private String resourceType;
    private Long resourceId;
    private String result;
    private String evidence;
    private Date createTime;

    // ---- Phase 7 归因（§11.1）----
    private String errorType;
    private Double confidence;
    private String recommendedAction;
    /** 系统错误为 false（不计学生知识失误） */
    private Boolean knowledgeImpact;

    // ---- Phase 7 §11.3 归因呈现修正 ----
    /** 失败归类：CURRENT 当前失误 / HISTORICAL 历史失误 / CORRECTED 已纠正 / NONE 无失败 */
    private String failureState;
    /** 依据强度标签（不包装成校准概率）："关联明确" / "需要进一步确认" */
    private String certaintyLabel;
    /** 依据条目（映射来源、结果消息、错误类型） */
    private java.util.List<String> attributionBasis;
}
