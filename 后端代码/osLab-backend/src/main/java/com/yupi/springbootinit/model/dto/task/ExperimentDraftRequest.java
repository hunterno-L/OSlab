package com.yupi.springbootinit.model.dto.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 共享实验表单（新建/改稿/应用）。 */
@Data
public class ExperimentDraftRequest implements Serializable {
    private String title;
    private String content;
    private String originalCode;
    private String answer;
    private JsonNode tags;
    private JsonNode judgeCase;
    private JsonNode judgeConfig;
    private List<String> nodeCodes = new ArrayList<>();
    private Boolean apply;

    private static final long serialVersionUID = 1L;
}
