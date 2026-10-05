package com.yupi.springbootinit.model.vo.learning;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 普通题 VO —— 学生可见视图。
 * 契约：本 VO 永不包含 answer；analysis/misconception 仅在该用户已作答后由服务端填充。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LearningQuestionVO implements Serializable {

    private Long id;

    private String title;

    private String content;

    /**
     * 题型：JUDGE/SINGLE/MULTIPLE/FILL_BLANK
     */
    private String type;

    /**
     * 选项 [{"key":"A","value":"..."}]（JSON 字符串，仅 SINGLE/MULTIPLE）
     */
    private String options;

    /**
     * 章节
     */
    private String chapter;

    private Integer difficulty;

    /**
     * 审核状态
     */
    private String reviewStatus;

    /**
     * 作者用户 id
     */
    private Long userId;

    /**
     * 作者用户名（管理/审核视图）
     */
    private String authorName;

    private Date createTime;

    private Date updateTime;

    /**
     * 关联知识节点业务键
     */
    private List<String> nodeNames;

    /** 当前用户作答次数（未作答为 0） */
    private Integer attemptCount;
    /** 最近一次作答结果：CORRECT/WRONG（未作答为 null） */
    private String latestResult;
    /** 首次作答结果：CORRECT/WRONG（未作答为 null） */
    private String firstResult;

    /**
     * 当前用户是否已作答（决定是否下发解析）
     */
    private Boolean hasAttempted;

    /**
     * 解析（仅已作答后填充，否则为 null）
     */
    private String analysis;

    /**
     * 常见误区（仅已作答后填充，否则为 null）
     */
    private String misconception;

}
