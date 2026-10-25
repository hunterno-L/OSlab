package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 题目练习目录-教材小节节点（稳定 sectionCode，按父级递归）
 */
@Data
public class LearningCatalogSectionVO implements Serializable {

    private String sectionCode;
    private String parentSectionCode;
    private String title;
    private String textbookNumber;
    private String sectionType;
    private Integer sortOrder;

    /**
     * 该小节及后代小节下全部已发布单元的去重题数
     */
    private Integer questionCount;

    private List<LearningCatalogUnitVO> units = new ArrayList<>();
    private List<LearningCatalogSectionVO> children = new ArrayList<>();
}
