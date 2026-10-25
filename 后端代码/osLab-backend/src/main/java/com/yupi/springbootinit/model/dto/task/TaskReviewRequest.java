package com.yupi.springbootinit.model.dto.task;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 教师批阅：按项给分/评语。 */
@Data
public class TaskReviewRequest implements Serializable {
    private List<ReviewItem> items = new ArrayList<>();
    private String overallComment;

    @Data
    public static class ReviewItem implements Serializable {
        private String itemCode;
        private BigDecimal score;
        private String comment;
        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
