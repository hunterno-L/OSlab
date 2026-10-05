package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 测评题 JSON 导入提交结果 VO（Phase 8 §12.2：仅导入校验通过项为草稿）
 */
@Data
public class ImportResultVO implements Serializable {

    /**
     * 成功导入草稿数
     */
    private Integer createdCount;

    /**
     * 跳过数（提交时校验不通过）
     */
    private Integer skippedCount;

    /**
     * 新建题目 id
     */
    private List<Long> questionIds;

    private static final long serialVersionUID = 1L;
}
