package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 到期复习项（v12）：1 / 3 / 7 天阶梯。
 *
 * <p>与 learning_mastery 相互独立：答对一道重复题不能把知识点刷成掌握。
 * 自我评价影响复习安排，但不作为判分正确或独立能力证据。
 *
 * @TableName learning_review_item
 */
@TableName(value = "learning_review_item")
@Data
public class LearningReviewItem implements Serializable {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** 阶梯间隔（天）：阶段 0=1 天、1=3 天、2=7 天。 */
    public static final int[] LADDER_DAYS = {1, 3, 7};

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String courseCode;

    /** 幂等键：{@code <nodeCode|skillCode>:<sourceEventId>:<stage>}。 */
    private String reviewKey;

    private String skillCode;

    private String nodeCode;

    private String activityKind;

    private String activityCode;

    private Long sourceEventId;

    /** 0/1/2 对应 1/3/7 天。 */
    private Integer stage;

    private Date dueAt;

    private String status;

    private String resultJson;

    private Integer isDelete;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public static int ladderDays(int stage) {
        if (stage < 0) {
            return LADDER_DAYS[0];
        }
        return stage >= LADDER_DAYS.length ? LADDER_DAYS[LADDER_DAYS.length - 1] : LADDER_DAYS[stage];
    }
}
