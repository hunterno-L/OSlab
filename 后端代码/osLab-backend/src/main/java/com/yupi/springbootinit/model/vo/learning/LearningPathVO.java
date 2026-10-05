package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 补救学习路径 VO（第三阶段 §6.3）
 *
 * <p>由后端统一计算：多前置邻接 + 拓扑排序 + 真实资源行动，
 * 工作台、课程目录、图谱路径投影共用，替代图谱页单前置 Map 推荐逻辑。
 */
@Data
public class LearningPathVO implements Serializable {

    /**
     * 目标节点名
     */
    private String targetNodeName;

    /**
     * 路径依据的学习事件 id（薄弱/失败证据，可追溯）
     */
    private List<Long> basisEventIds = new ArrayList<>();

    /**
     * 有序路径项（前置在前、目标在后）
     */
    private List<PathItemVO> items = new ArrayList<>();

    /**
     * 依赖环涉及的节点（无环为空；有环时排序仍返回无环部分，不删除真实边）
     */
    private List<String> cycleNodes = new ArrayList<>();
}
