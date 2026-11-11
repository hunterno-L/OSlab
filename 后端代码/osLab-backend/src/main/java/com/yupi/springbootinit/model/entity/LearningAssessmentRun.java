package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 知识测评会话（Phase 4 §8.3）：队列固定在服务端，顺序与首次结果由作答记录读取。
 *
 * @TableName learning_assessment_run
 */
@TableName(value = "learning_assessment_run")
@Data
public class LearningAssessmentRun implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 学生用户 id
     */
    private Long userId;

    /**
     * 章节（可空=混合）
     */
    private String chapter;

    /**
     * 知识节点（可空=多节点）
     */
    private String nodeName;

    /**
     * 本轮题目队列 JSON（服务端固定顺序）
     */
    private String questionIdsJson;

    /**
     * 状态：ACTIVE/FINISHED
     */
    private String status;

    /** 模式：PRACTICE / SELF_TEST；历史 run 为 NULL，按 PRACTICE 处理。 */
    private String mode;

    /** 草稿乐观锁版本（整组自测）。 */
    private Integer rowVersion;

    /** 开始时的题目版本快照 {questionId: revision}。 */
    private String questionRevisionsJson;

    private String courseCode;

    /** 最近一次草稿保存的客户端幂等键与时间。 */
    private String lastSaveId;

    private Date lastSaveAt;

    /** 交卷幂等键与交卷时的草稿版本。 */
    private String submissionKey;

    private Integer submissionRowVersion;

    /** 服务端最终结果 JSON（FINISHED 后只读回放）。 */
    private String finishResultJson;

    private Date createTime;

    private Date finishTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
