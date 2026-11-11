package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 整组自测草稿保存：整组覆盖式提交，服务端只落草稿，不判分、不发事件。
 */
@Data
public class SelfTestDraftSaveRequest implements Serializable {

    /** 本次保存的客户端幂等键；重发同键返回同一版本。 */
    private String clientSaveId;

    /** 期望的当前 run 版本；不一致说明另一标签页已更新。 */
    private Integer rowVersion;

    private List<Draft> drafts;

    @Data
    public static class Draft implements Serializable {
        private Long questionId;
        /** string / string[] / null */
        private Object answer;
        private Boolean markedUnsure;
    }
}
