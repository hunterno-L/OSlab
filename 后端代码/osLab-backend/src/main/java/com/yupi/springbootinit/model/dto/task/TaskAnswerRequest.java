package com.yupi.springbootinit.model.dto.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;

/** 任务内客观题作答。 */
@Data
public class TaskAnswerRequest implements Serializable {
    private Integer roundNo;
    private String submissionKey;
    private JsonNode answer;

    private static final long serialVersionUID = 1L;
}
