package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 冻结契约 AiCourseContext（任务书 §3 / 计划 §6.2）：
 * {@code courseCode, unitCode?, nodeCodes[], resourceRef?, eventIds[], selectedSourceIds[]}。
 *
 * <p>浏览器只传定位标识；后端校验归属并重新读取授权数据，
 * 绝不相信前端提供的正确率、掌握度或判题结论。
 */
@Data
public class AiCourseContext implements Serializable {

    private String courseCode;

    private String unitCode;

    private List<String> nodeCodes = new ArrayList<>();

    /** 当前题目/实验/资料引用，例如 {@code question:2105926034106609665}。 */
    private String resourceRef;

    /** 前端只提供 id，后端按当前用户重新读取事件本体。 */
    private List<String> eventIds = new ArrayList<>();

    /** 用户在资料中选中的来源块 id。 */
    private List<String> selectedSourceIds = new ArrayList<>();

    /**
     * 服务端建立的答题证据上下文（B 的普通尝试接口返回）。
     * 由 TutorAssessmentGuard 校验归属与帮助资格；前端不能自造成交或资格。
     */
    private String attemptContextId;

    /** 服务端评估运行 id（自测/测评）。守卫据此识别 ACTIVE 自测限制。 */
    private String assessmentRunId;
}
