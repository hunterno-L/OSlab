package com.yupi.springbootinit.ai.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.profile.ModelProfileService;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiLearningSessionMapper;
import com.yupi.springbootinit.model.dto.ai.AiRequests;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiSessionVO;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * AI 学习会话服务（P0-1：统一课程上下文与持久会话）。
 *
 * <p>会话是持久化的任务上下文：课程作用域、目标、学习/复习侧重、时间预算、
 * 任务位置与计划 JSON、模型引用与版本号。刷新、跳转、切换模型、服务重启后
 * 都要能按同一份数据继续，不允许只存在于内存。
 *
 * <p>安全边界：所有读写都先校验会话属于当前用户；写操作必须带 rowVersion，
 * 版本不匹配即拒绝，过期上下文要先刷新再执行。切换课程必须切换会话作用域，
 * 避免把一门课的错误带进另一门课的诊断。
 */
@Slf4j
@Service
public class AiSessionService {

    public static final String FOCUS_LEARN = "LEARN";
    public static final String FOCUS_REVIEW = "REVIEW";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";

    private static final int MAX_GOAL_CHARS = 500;

    @Resource
    private AiLearningSessionMapper sessionMapper;

    @Resource private com.yupi.springbootinit.course.EnabledCourses enabledCourses;
    @Resource private com.yupi.springbootinit.learning.PublishedCourseScopeService courseScope;

    @Resource
    private ModelProfileService modelProfileService;

    @Transactional(rollbackFor = Exception.class)
    public AiSessionVO create(User user, AiRequests.CreateSession request) {
        if (request == null || StringUtils.isBlank(request.getCourseCode())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "courseCode 不能为空");
        }
        String courseCode = request.getCourseCode().trim();
        enabledCourses.require(courseCode);
        courseScope.requireUnit(courseCode, request.getUnitCode());
        String focus = normalizeFocus(request.getFocus());
        AiLearningSession session = new AiLearningSession();
        session.setUserId(user.getId());
        session.setCourseCode(courseCode);
        session.setGoal(StringUtils.abbreviate(StringUtils.trimToEmpty(request.getGoal()), MAX_GOAL_CHARS));
        session.setFocus(focus);
        session.setTimeBudgetMinutes(normalizeBudget(request.getTimeBudgetMinutes()));
        session.setUnitCode(StringUtils.trimToNull(request.getUnitCode()));
        // 模型引用先校验可用，避免把不可用配置写进会话后每轮都失败
        session.setModelProfileId(modelProfileService.validateProfileForSession(user.getId(),
                request.getModelProfileId()));
        session.setStatus(STATUS_ACTIVE);
        session.setRowVersion(0);
        session.setIsDelete(0);
        // 显式写入创建/更新时间：DB 默认值不会回填到实体，
        // 否则创建响应里的 createdAt 会是 null，违反冻结契约样例。
        Date now = new Date();
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);
        log.info("[ai-session] created id={} user={} course={} focus={}",
                session.getId(), user.getId(), courseCode, focus);
        return toVo(session);
    }

    public AiSessionVO get(User user, String sessionId) {
        return toVo(requireOwned(user, parseId(sessionId)));
    }

    /**
     * 修改目标 / 侧重 / 时间预算 / 模型 / 当前任务位置。
     * 写操作必须带 rowVersion；成功后版本 +1。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiSessionVO update(User user, String sessionId, AiRequests.UpdateSession request) {
        AiLearningSession session = requireOwned(user, parseId(sessionId));
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        if (request.getRowVersion() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "写操作必须携带 rowVersion");
        }
        if (!request.getRowVersion().equals(session.getRowVersion())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "会话已被其他操作更新，请刷新后重试（当前版本 " + session.getRowVersion() + "）");
        }
        if (StringUtils.isNotBlank(request.getGoal())) {
            session.setGoal(StringUtils.abbreviate(request.getGoal().trim(), MAX_GOAL_CHARS));
        }
        if (StringUtils.isNotBlank(request.getFocus())) {
            session.setFocus(normalizeFocus(request.getFocus()));
        }
        if (request.getTimeBudgetMinutes() != null) {
            session.setTimeBudgetMinutes(normalizeBudget(request.getTimeBudgetMinutes()));
        }
        if (StringUtils.isNotBlank(request.getUnitCode())) {
            enabledCourses.require(session.getCourseCode());
            courseScope.requireUnit(session.getCourseCode(), request.getUnitCode());
            session.setUnitCode(request.getUnitCode().trim());
        }
        if (StringUtils.isNotBlank(request.getModelProfileId())) {
            // 切换模型只改变后续推理：课程、资料、任务与证据全部保留
            session.setModelProfileId(modelProfileService.validateProfileForSession(user.getId(),
                    request.getModelProfileId()));
        }
        if (StringUtils.isNotBlank(request.getStatus())) {
            String status = request.getStatus().trim().toUpperCase();
            if (!STATUS_ACTIVE.equals(status) && !STATUS_CLOSED.equals(status)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法会话状态：" + status);
            }
            session.setStatus(status);
        }
        if (StringUtils.isNotBlank(request.getCurrentActionId())) {
            session.setCurrentActionJson(AiJson.write(findPlanAction(session, request.getCurrentActionId())));
        }
        session.setRowVersion(session.getRowVersion() + 1);
        session.setUpdateTime(new Date());
        int updated = sessionMapper.updateById(session);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "会话更新失败，请刷新后重试");
        }
        return toVo(session);
    }

    /** 内部使用：取本人会话实体（已校验归属）。 */
    public AiLearningSession requireOwned(User user, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "sessionId 不能为空");
        }
        AiLearningSession session = sessionMapper.selectById(sessionId);
        if (session == null || (session.getIsDelete() != null && session.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "学习会话不存在");
        }
        if (!session.getUserId().equals(user.getId())) {
            // 不泄露他人会话是否存在
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该学习会话");
        }
        return session;
    }

    /**
     * 该课程下最近一次进行中的会话；没有则返回 null。
     * next-actions 用它复用同一份计划，避免首页与导师各生成一套任务。
     */
    public AiLearningSession latestActive(Long userId, String courseCode) {
        if (userId == null || StringUtils.isBlank(courseCode)) {
            return null;
        }
        List<AiLearningSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<AiLearningSession>()
                        .eq(AiLearningSession::getUserId, userId)
                        .eq(AiLearningSession::getCourseCode, courseCode)
                        .eq(AiLearningSession::getStatus, STATUS_ACTIVE)
                        .orderByDesc(AiLearningSession::getCreateTime)
                        .orderByDesc(AiLearningSession::getId)
                        .last("limit 1"));
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    /** 读计划（NextAction[]）。 */
    public List<NextAction> plan(AiLearningSession session) {
        return AiJson.readList(session == null ? null : session.getPlanJson(), NextAction.class);
    }

    /**
     * 写计划：动作 id 由 sessionId 派生，可反解归属，无需为计划额外建表。
     * 同时写入当前任务位置（可能为 null，表示已回到未完成队列）。
     */
    public void savePlan(AiLearningSession session, List<NextAction> actions) {
        session.setPlanJson(AiJson.write(actions));
        sessionMapper.updatePlan(session.getId(), session.getUserId(),
                session.getPlanJson(), session.getCurrentActionJson());
    }

    /** 动作 id 形如 {@code na-<sessionId>-<序号>}。 */
    public static String actionId(Long sessionId, int index) {
        return "na-" + sessionId + "-" + index;
    }

    /** 从动作 id 反解会话 id；非法格式返回 null。 */
    public static Long sessionIdOf(String actionId) {
        if (StringUtils.isBlank(actionId) || !actionId.startsWith("na-")) {
            return null;
        }
        String[] parts = actionId.split("-");
        if (parts.length != 3) {
            return null;
        }
        try {
            return Long.valueOf(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int actionIndexOf(String actionId) {
        if (StringUtils.isBlank(actionId)) {
            return -1;
        }
        String[] parts = actionId.split("-");
        if (parts.length != 3) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private NextAction findPlanAction(AiLearningSession session, String actionId) {
        for (NextAction action : plan(session)) {
            if (actionId.equals(action.getActionId())) {
                return action;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在或已过期：" + actionId);
    }

    public AiSessionVO toVo(AiLearningSession session) {
        AiSessionVO vo = new AiSessionVO();
        vo.setSessionId(AiJson.id(session.getId()));
        vo.setCourseCode(session.getCourseCode());
        vo.setGoal(session.getGoal());
        vo.setFocus(session.getFocus());
        vo.setTimeBudgetMinutes(session.getTimeBudgetMinutes());
        vo.setCurrentAction(AiJson.read(session.getCurrentActionJson(), NextAction.class));
        vo.setModelProfileId(AiJson.id(session.getModelProfileId()));
        vo.setRowVersion(session.getRowVersion());
        vo.setStatus(session.getStatus());
        vo.setUnitCode(session.getUnitCode());
        vo.setCreatedAt(AiJson.iso(session.getCreateTime()));
        vo.setUpdatedAt(AiJson.iso(session.getUpdateTime()));
        return vo;
    }

    private static Long parseId(String sessionId) {
        try {
            return AiJson.parseId(sessionId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "sessionId 非法");
        }
    }

    static String normalizeFocus(String focus) {
        String value = StringUtils.upperCase(StringUtils.trimToEmpty(focus));
        return FOCUS_REVIEW.equals(value) ? FOCUS_REVIEW : FOCUS_LEARN;
    }

    static int normalizeBudget(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return 0;
        }
        return Math.min(minutes, 600);
    }
}
