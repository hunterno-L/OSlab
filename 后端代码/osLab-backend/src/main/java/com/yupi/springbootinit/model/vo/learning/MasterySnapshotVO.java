package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 历史快照回放 VO（Phase 7 §11.2）：按某事件时点各节点最后有效快照生成的图谱投影。
 *
 * <p>回放只读：不写事件、不重算掌握度。底图使用当前课程结构，
 * 不宣称已重建历史课程拓扑。
 */
@Data
public class MasterySnapshotVO implements Serializable {

    /**
     * 回放锚定事件 id
     */
    private Long atEventId;

    /**
     * 回放时点（锚定事件时间）
     */
    private Date replayAt;

    /**
     * 锚定事件组键（同组多节点事件一并计入该时点，避免停在部分更新状态）
     */
    private String eventGroupKey;

    /**
     * 图谱投影（节点带该时点状态；snapshotAvailable=false 表示历史细分状态未记录）
     */
    private PersonalGraphVO graph;
}
