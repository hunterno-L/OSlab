package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 冻结契约 NextAction（任务书 §3）：字段名与样例一致，不额外暴露内部执行细节。
 *
 * <p>每个任务都要能回答"为什么现在做""预计多久""做完看什么结果"：
 * {@code reason} 是依据，{@code estimatedMinutes} 是可配置启发式估算
 * （不包装成训练过的最优策略），{@code evidenceIds} 指向真实学习事件，
 * {@code returnRef} 支持"补学后返回原任务"。
 *
 * <p>客户端只需要 {@code targetRef} 即可导航；判分所需的活动参数由服务端
 * 在 {@code /actions/{id}/start} 的 activityRef 中按"允许看到的范围"下发，
 * 因此本对象不携带任何预测题的后续轨迹，避免提前泄露答案。
 */
@Data
public class NextAction implements Serializable {

    public static final String TYPE_READ = "READ";
    public static final String TYPE_QUESTION = "QUESTION";
    public static final String TYPE_PREDICT = "PREDICT";
    public static final String TYPE_EXPLAIN = "EXPLAIN";
    public static final String TYPE_RETEST = "RETEST";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** {@code na-<sessionId>-<序号>}：可反解所属会话，避免为计划额外建表。 */
    private String actionId;

    /** READ / QUESTION / PREDICT / EXPLAIN / RETEST */
    private String type;

    private TargetRef targetRef;

    private String reason;

    private List<String> evidenceIds = new ArrayList<>();

    private Integer estimatedMinutes;

    private String status;

    /** 补学完成后要返回的原任务；无则为 null。 */
    private TargetRef returnRef;

    public static boolean isValidType(String type) {
        return TYPE_READ.equals(type) || TYPE_QUESTION.equals(type) || TYPE_PREDICT.equals(type)
                || TYPE_EXPLAIN.equals(type) || TYPE_RETEST.equals(type);
    }

    /** 是否会产生正式评估证据（只有真实判分的题目与预测才算）。 */
    public boolean isAssessed() {
        return TYPE_QUESTION.equals(type) || TYPE_RETEST.equals(type) || TYPE_PREDICT.equals(type);
    }
}
