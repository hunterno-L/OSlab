package com.yupi.springbootinit.model.dto.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 任务内容块。content 的结构由 itemType 决定，服务端严格校验。 */
@Data
public class TaskItemRequest implements Serializable {
    private String itemCode;
    private String itemType;
    private String sourceMode;
    private Boolean required;
    private BigDecimal maxScore;
    private JsonNode content;

    private static final long serialVersionUID = 1L;
}
