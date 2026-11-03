package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * 冻结契约 toolTrace 条目（任务书 §3）：{@code {tool,status,ms,summary}}。
 *
 * <p>展示真实工具名称、耗时与核验结论（计划 §5.3）；
 * 不展示也不编造模型内部思维链。
 */
@Data
public class ToolTraceEntry implements Serializable {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    /** 超出每轮 3 次工具预算而被拒绝执行。 */
    public static final String STATUS_BUDGET_EXCEEDED = "BUDGET_EXCEEDED";

    private String tool;

    private String status;

    private Long ms;

    private String summary;

    public ToolTraceEntry() {
    }

    public ToolTraceEntry(String tool, String status, long ms, String summary) {
        this.tool = tool;
        this.status = status;
        this.ms = ms;
        this.summary = summary;
    }
}
