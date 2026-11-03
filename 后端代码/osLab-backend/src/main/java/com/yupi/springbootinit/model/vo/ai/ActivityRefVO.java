package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code POST /learning/ai/actions/{id}/start} 返回的活动引用。
 *
 * <p>这是"允许看到的范围"的唯一下发点：预测类活动只给出提交前可见的前缀
 * （{@code visibleSteps}），后续轨迹与正确答案一律不出现在本对象中，
 * 提交后才由服务端计算器核对并返回差异步骤。
 *
 * <p>{@code helpLevel} 告知前端当前评估任务的帮助状态；
 * 请求提示或揭示答案会先落库再影响证据资格。
 */
@Data
public class ActivityRefVO implements Serializable {

    private String actionId;

    /** QUESTION / PREDICT / READ / EXPLAIN / RETEST */
    private String type;

    private TargetRef targetRef;

    private String title;

    /** 题面或任务说明（不含答案与解析）。 */
    private String prompt;

    /** 选择题选项（不含正确标记）。 */
    private List<Option> options = new ArrayList<>();

    /** 预测类活动：物理块数。 */
    private Integer frames;

    /** 预测类活动：提交前允许看到的访问序列前缀。 */
    private List<Integer> visibleSequence = new ArrayList<>();

    /** 预测类活动：允许看到的已确定步骤（不含待预测步及其之后）。 */
    private List<PredictStep> visibleSteps = new ArrayList<>();

    /** 待预测的步序号。 */
    private Integer predictStepIndex;

    /** 提交需要的幂等键（服务端生成，回传即可）。 */
    private String submissionKey;

    /** 当前帮助等级：NONE / HINT / ANSWER_REVEALED / UNKNOWN。 */
    private String helpLevel;

    /** 提交入口提示：该活动是否需要文本作答。 */
    private Boolean expectsText;

    @Data
    public static class Option implements Serializable {
        private String key;
        private String label;

        public Option() {
        }

        public Option(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    @Data
    public static class PredictStep implements Serializable {
        private Integer step;
        private Integer page;
        private Boolean fault;
        private Integer evicted;
        private List<Integer> frames = new ArrayList<>();
    }
}
