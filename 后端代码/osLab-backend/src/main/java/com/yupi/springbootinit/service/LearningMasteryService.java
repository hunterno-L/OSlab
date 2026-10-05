package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.MasteryNodeVO;
import com.yupi.springbootinit.model.vo.learning.MasterySnapshotVO;
import com.yupi.springbootinit.model.vo.learning.NodeWorkbenchVO;
import com.yupi.springbootinit.model.vo.learning.PersonalGraphVO;
import com.yupi.springbootinit.model.vo.learning.RecommendationVO;
import com.yupi.springbootinit.model.vo.learning.TimelineVO;
import com.yupi.springbootinit.model.vo.learning.WeakNodeVO;

import java.util.List;

/**
 * 个人知识图谱与掌握度服务（WP3）
 */
public interface LearningMasteryService {

    /**
     * 个人图谱：图谱结构 + 当前学生的掌握状态叠加（同一节点在不同学生视角颜色不同）
     */
    PersonalGraphVO personalGraph(User user);

    /**
     * 节点工作台：掌握状态、前置关系、关联已发布普通题、本人事件证据、推荐
     */
    NodeWorkbenchVO nodeWorkbench(User user, String nodeName);

    /**
     * 学习推荐（每条含原因）：薄弱节点、前置缺口、练习巩固，上限 5 条
     */
    List<RecommendationVO> recommendations(User user);

    /**
     * 单节点掌握状态（无证据返回 GRAY 零值）
     */
    MasteryNodeVO nodeMastery(Long userId, String nodeName);

    /**
     * 本人学习时间线（Phase 7 §11.1）：按节点/事件类型/时间范围分页查询，
     * 只查当前登录用户，不接收外部 userId。
     */
    TimelineVO myTimeline(User user, String nodeName, String eventType,
                          String startTime, String endTime, long current, long pageSize);

    /**
     * 历史快照回放（Phase 7 §11.2）：按某事件时点各节点最后有效快照生成图谱投影。
     * 只读：不写事件、不重算掌握度；不接受其他用户的事件 id。
     */
    MasterySnapshotVO replaySnapshot(User user, Long atEventId);

    /**
     * 待巩固节点（Phase 7 §11.3）：薄弱（RED/YELLOW）节点 + 失败归类
     * （当前失误/历史失误/已纠正/无失败）。
     */
    List<WeakNodeVO> weaknessNodes(User user);
}
