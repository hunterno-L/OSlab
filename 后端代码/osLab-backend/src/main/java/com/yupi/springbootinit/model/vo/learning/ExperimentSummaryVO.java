package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 节点关联编程实验摘要（含当前用户最近一次提交终态）
 */
@Data
public class ExperimentSummaryVO implements Serializable {
    private Long id;
    private String title;
    /** 最近一次提交终态消息（Accepted/Wrong Answer/编译错误...），未提交为 null */
    private String lastResult;
}
