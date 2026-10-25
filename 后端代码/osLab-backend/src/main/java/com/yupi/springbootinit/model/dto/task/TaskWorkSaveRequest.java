package com.yupi.springbootinit.model.dto.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 保存某内容块当前轮次草稿。 */
@Data
public class TaskWorkSaveRequest implements Serializable {
    private Integer roundNo;
    private JsonNode answer;
    private String textResponse;
    private List<Long> fileIds;

    private static final long serialVersionUID = 1L;
}
