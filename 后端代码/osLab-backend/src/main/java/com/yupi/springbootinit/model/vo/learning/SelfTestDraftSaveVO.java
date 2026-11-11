package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 草稿保存结果；版本冲突时返回服务端草稿，供前端选择重新载入。 */
@Data
public class SelfTestDraftSaveVO implements Serializable {

    private Integer rowVersion;
    private Long savedAt;
    private Boolean conflict = false;
    private List<ServerDraft> serverDrafts = new ArrayList<>();

    @Data
    public static class ServerDraft implements Serializable {
        private String questionId;
        private Object answer;
        private Boolean markedUnsure;
    }
}
