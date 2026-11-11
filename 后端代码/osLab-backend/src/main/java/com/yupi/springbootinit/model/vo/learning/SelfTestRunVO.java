package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 整组自测进行中的安全视图：只含题面、本人草稿与保存状态。
 * 不含答案、解析、误区、正确性、本轮或历史正确率提示。
 */
@Data
public class SelfTestRunVO implements Serializable {

    private String runId;
    private String mode;
    private String status;
    /** 草稿版本；保存/交卷时带回。 */
    private Integer rowVersion;
    /** 最近一次保存时间（epoch millis），未保存为 null。 */
    private Long savedAt;
    private List<Question> questions = new ArrayList<>();

    @Data
    public static class Question implements Serializable {
        private String id;
        private String title;
        private String content;
        private String type;
        private String options;
        private String chapter;
        private Integer difficulty;
        private Integer questionRevision;
        /** 题目已下线/删除：不静默换题，交卷时单独说明。 */
        private Boolean offline;
        /** 题目在自测开始后被修改：交卷时不参与有效分母。 */
        private Boolean revisionChanged;
        private String offlineReason;
        /** 本人草稿（string / string[] / null）。 */
        private Object draftAnswer;
        private Boolean markedUnsure;
    }
}
