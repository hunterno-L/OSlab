package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 测评题 JSON 导入预览 VO（Phase 8 §12.2：逐项验证，不落库）
 */
@Data
public class ImportPreviewVO implements Serializable {

    /**
     * 提交条目总数
     */
    private Integer total;

    /**
     * 校验通过条目数
     */
    private Integer validCount;

    /**
     * 校验失败条目数
     */
    private Integer invalidCount;

    /**
     * 逐条校验结果（顺序与提交一致）
     */
    private List<ImportItemVO> items;

    /**
     * 单条导入项校验结果
     */
    @Data
    public static class ImportItemVO implements Serializable {

        /**
         * 序号（从 1 开始）
         */
        private Integer index;

        /**
         * 是否通过校验
         */
        private Boolean valid;

        /**
         * 不通过原因（多条以分号连接；通过时为 null）
         */
        private String error;

        /**
         * 标题预览
         */
        private String title;

        /**
         * 题型预览
         */
        private String type;

        /**
         * 章节预览
         */
        private String chapter;

        /**
         * 节点映射预览
         */
        private List<String> nodeNames;

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
