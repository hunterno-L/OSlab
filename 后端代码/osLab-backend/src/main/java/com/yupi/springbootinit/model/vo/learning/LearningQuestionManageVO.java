package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员题面/编辑 VO（U11）：在学生 VO 基础上增加标准答案与审核历史。
 * 仅 ADMIN 端点返回；学生端点契约不含答案字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningQuestionManageVO extends LearningQuestionVO {
    /** 标准答案 JSON（JUDGE true/false；SINGLE "A"；MULTIPLE ["A","C"]；FILL_BLANK 数组） */
    private String correctAnswer;
    /** 审核记录（时间升序） */
    private List<ReviewRecordVO> reviewHistory;

    @Data
    public static class ReviewRecordVO implements Serializable {
        private Long reviewerId;
        private String reviewerName;
        private String action;
        private String reason;
        private java.util.Date createTime;
    }
}
