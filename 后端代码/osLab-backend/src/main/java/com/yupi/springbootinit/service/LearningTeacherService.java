package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.vo.learning.ChapterCoverageVO;
import com.yupi.springbootinit.model.vo.learning.ClassOptionVO;
import com.yupi.springbootinit.model.vo.learning.NodeDrillVO;
import com.yupi.springbootinit.model.vo.learning.TeacherDashboardVO;
import com.yupi.springbootinit.model.vo.learning.TimelineVO;

import java.util.List;

/**
 * 教师班级看板服务（契约 §3.5 / Phase 8）。所有端点须校验管理员权限（Controller 层 @AuthCheck）。
 * 班级成员解析使用现有 students 表（userId/className）。
 */
public interface LearningTeacherService {

    /**
     * 班级大屏：节点热力、薄弱 Top5、题型正确率、实验通过率、最近事件
     */
    TeacherDashboardVO dashboard(String className);

    /**
     * 成长时间轴：回放不可变事件（不重算），支持按学生/节点/类型筛选。
     * className 必填：未指定 userId 时限定班级成员集合，不允许落到全库事件。
     */
    TimelineVO timeline(Long userId, String className, String nodeName, String eventType, long current, long pageSize);

    /**
     * 真实可选班级列表（students 表聚合，不硬编码）
     */
    List<ClassOptionVO> classes();

    /**
     * 节点下钻：映射表/标签精确关联，首次正确率与实验通过率口径见 VO 注释
     */
    NodeDrillVO nodeDetail(String className, String nodeName);

    /**
     * 章节资源完整度：节点数、已发布素材、测评覆盖、实验覆盖、未关联资源
     */
    List<ChapterCoverageVO> chapterCoverage();
}
