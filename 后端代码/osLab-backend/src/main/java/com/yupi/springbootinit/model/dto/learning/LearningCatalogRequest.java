package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目练习目录聚合请求（阶段 4）：请求体可选。
 * 约定：
 * - 缺省/空体/空白 chapter：返回全部章节；
 * - chapter 为题目章节原值，精确匹配；
 * - chapter 传"未归类"时返回章节为空（null/空白）的题目。
 */
@Data
public class LearningCatalogRequest implements Serializable {

    /**
     * 章节过滤（可选）
     */
    private String chapter;
}
