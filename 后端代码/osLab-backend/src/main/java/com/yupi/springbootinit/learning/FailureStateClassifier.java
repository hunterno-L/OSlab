package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.enums.learning.LearningEventTypeEnum;
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 失败归类（Phase 7 §11.3）：把节点上的失败证据分成
 * 当前失误 / 历史失误 / 已纠正 / 无失败记录，避免“已成功纠错仍显示当前失败”。
 *
 * <p>规则（纯函数，事件按 createTime,id 升序传入）：
 * <ul>
 *   <li>CORRECTED 已纠正：最近失败资源之后出现同资源（resourceType+resourceId）PASS；</li>
 *   <li>HISTORICAL 历史失误：失败后仍有学习活动（非基线、非系统错误事件）但失败资源未再通过；</li>
 *   <li>CURRENT 当前失误：最近的有效学习记录就是该次失败；</li>
 *   <li>NONE 无失败记录：节点没有知识观测失败事件。</li>
 * </ul>
 * 状态基线（STATE_BASELINE）与系统错误（SYSTEM_ERROR）不算学习活动。
 */
public final class FailureStateClassifier {

    public static final String CURRENT = "CURRENT";
    public static final String HISTORICAL = "HISTORICAL";
    public static final String CORRECTED = "CORRECTED";
    public static final String NONE = "NONE";

    private FailureStateClassifier() {
    }

    /**
     * 归类结果
     */
    public static final class Result {
        /** CURRENT / HISTORICAL / CORRECTED / NONE */
        public final String state;
        /** 最近一次知识观测失败事件（NONE 时为 null） */
        public final LearningEvent lastFail;
        /** 同资源后续通过事件（仅 CORRECTED 非空） */
        public final LearningEvent correctedBy;

        public Result(String state, LearningEvent lastFail, LearningEvent correctedBy) {
            this.state = state;
            this.lastFail = lastFail;
            this.correctedBy = correctedBy;
        }
    }

    /**
     * @param eventsAsc 该学生该节点全部事件，按 createTime,id 升序
     */
    public static Result classify(List<LearningEvent> eventsAsc) {
        if (CollectionUtils.isEmpty(eventsAsc)) {
            return new Result(NONE, null, null);
        }
        int lastFailIndex = -1;
        for (int i = eventsAsc.size() - 1; i >= 0; i--) {
            LearningEvent e = eventsAsc.get(i);
            if (LearningResultEnum.FAIL.name().equals(e.getResult())
                    && !LearningEventTypeEnum.STATE_BASELINE.name().equals(e.getEventType())) {
                lastFailIndex = i;
                break;
            }
        }
        if (lastFailIndex < 0) {
            return new Result(NONE, null, null);
        }
        LearningEvent lastFail = eventsAsc.get(lastFailIndex);
        LearningEvent correctedBy = null;
        boolean laterActivity = false;
        for (int i = lastFailIndex + 1; i < eventsAsc.size(); i++) {
            LearningEvent e = eventsAsc.get(i);
            if (LearningEventTypeEnum.STATE_BASELINE.name().equals(e.getEventType())
                    || LearningResultEnum.SYSTEM_ERROR.name().equals(e.getResult())
                    || LearningResultEnum.BASELINE.name().equals(e.getResult())) {
                continue;
            }
            laterActivity = true;
            if (LearningResultEnum.PASS.name().equals(e.getResult())
                    && sameResource(e, lastFail)) {
                correctedBy = e;
            }
        }
        if (correctedBy != null) {
            return new Result(CORRECTED, lastFail, correctedBy);
        }
        return new Result(laterActivity ? HISTORICAL : CURRENT, lastFail, null);
    }

    private static boolean sameResource(LearningEvent a, LearningEvent b) {
        if (a.getResourceType() == null || b.getResourceType() == null
                || a.getResourceId() == null || b.getResourceId() == null) {
            return false;
        }
        return a.getResourceType().equals(b.getResourceType())
                && a.getResourceId().equals(b.getResourceId());
    }
}
