package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 资料段落块 + embedding（v11）。
 *
 * <p>{@code blockId} 全局唯一：一个块只保留一份向量，重建时整行替换，
 * 因此不同 embedding 模型的向量在物理上不可能混用。
 * {@code embeddingModel} 与 {@code embeddingDims} 记录向量出处，
 * 检索时按当前平台模型过滤，模型不一致的块一律不参与语义召回。
 *
 * @TableName ai_material_chunk
 */
@TableName(value = "ai_material_chunk")
@Data
public class AiMaterialChunk implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 稳定块标识：{@code <courseCode>::<sourceId>::<序号>}。 */
    private String blockId;

    /** 来源标识：课程正文为 chunkCode，个人资料为 {@code material:<id>}。 */
    private String sourceId;

    /** 个人资料 id；课程正文块为 0。 */
    private Long materialId;

    private String ownerScope;

    /** PERSONAL 块的所有者；COURSE 块为 0。 */
    private Long userId;

    private String courseCode;

    private Integer revision;

    /** 定位锚点，例如 {@code #chunk-xxx} 或 {@code sec.overview>p3}。 */
    private String locator;

    private String title;

    /** 图谱节点映射 JSON 数组，用于图谱约束召回。 */
    private String nodeCodesJson;

    private String contentMd;

    private String textHash;

    private String embeddingModel;

    private Integer embeddingDims;

    private Integer embeddingVersion;

    /** 向量的 JSON 数组文本；应用内计算相似度，不依赖向量数据库。 */
    private String vectorJson;

    private Date indexedAt;

    private Integer isDelete;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
