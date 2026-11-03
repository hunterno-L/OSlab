package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 个人/课程资料（v11）：粘贴文本与 UTF-8 Markdown 接入，按课程隔离。
 *
 * @TableName ai_material
 */
@TableName(value = "ai_material")
@Data
public class AiMaterial implements Serializable {

    public static final String SCOPE_PERSONAL = "PERSONAL";
    public static final String SCOPE_COURSE = "COURSE";

    public static final String INDEX_PENDING = "PENDING";
    public static final String INDEX_KEYWORD_ONLY = "KEYWORD_ONLY";
    public static final String INDEX_EMBEDDED = "EMBEDDED";
    public static final String INDEX_FAILED = "FAILED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String courseCode;

    /** PERSONAL（本人可见）/ COURSE（课程共享，仅教师/管理员可写）。 */
    private String ownerScope;

    private String title;

    private String contentMd;

    /** 正文哈希：增量重建时内容未变则跳过。 */
    private String textHash;

    private Integer revision;

    private Integer chunkCount;

    /** 用户标注的知识点编码 JSON 数组，用于图谱约束召回。 */
    private String nodeCodesJson;

    /** PENDING / KEYWORD_ONLY / EMBEDDED / FAILED —— 索引状态必须诚实。 */
    private String indexStatus;

    private Integer isDelete;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
