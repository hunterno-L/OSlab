package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 普通题更新请求（仅作者可改自己的题；REJECTED 后修改可重提）
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class LearningQuestionUpdateRequest extends LearningQuestionAddRequest {

    /**
     * 题目 id
     */
    private Long id;
}
