package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * 冻结契约 SourceRef（任务书 §3）：字段名与含义不得变更。
 *
 * <p>每条来源都要能定位到具体段落与来源版本：{@code revision} 是发布修订，
 * {@code locator} 是段落/章节锚点，{@code excerpt} 是实际引用片段。
 */
@Data
public class SourceRef implements Serializable {

    /** 来源标识：课程正文为 chunkCode，个人资料为 {@code material:<id>}。 */
    private String sourceId;

    /** COURSE / PERSONAL */
    private String ownerScope;

    private String courseCode;

    /** 发布修订号；个人资料为自身 revision。 */
    private Integer revision;

    /** 稳定块标识，前端据此高亮段落。 */
    private String blockId;

    /** 定位（章节/段落锚点），可打开到具体段落。 */
    private String locator;

    /** 引用片段（截断后的原文，不改写）。 */
    private String excerpt;

    public static SourceRef of(String sourceId, String ownerScope, String courseCode,
                               Integer revision, String blockId, String locator, String excerpt) {
        SourceRef ref = new SourceRef();
        ref.setSourceId(sourceId);
        ref.setOwnerScope(ownerScope);
        ref.setCourseCode(courseCode);
        ref.setRevision(revision);
        ref.setBlockId(blockId);
        ref.setLocator(locator);
        ref.setExcerpt(excerpt);
        return ref;
    }
}
