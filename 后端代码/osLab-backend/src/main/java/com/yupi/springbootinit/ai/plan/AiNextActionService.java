package com.yupi.springbootinit.ai.plan;

import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.diagnosis.DiagnosisService;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.ai.retrieval.HybridRetrievalService;
import com.yupi.springbootinit.ai.session.AiSessionService;
import com.yupi.springbootinit.model.dto.ai.AiRequests;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.LearningReviewItem;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.NextActionsVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 下一步计划服务（P0-5）：首页、图谱与导师共用同一份计划。
 *
 * <p>会话目标可触发短期计划更新：计划写回会话，因此从首页进入课程、
 * 在导师里提问、再回到首页看到的是同一批任务与同一个"当前任务位置"。
 *
 * <p>计划只引用实际存在且可访问的已发布单元、题目与人工核验的预测案例；
 * 证据未知时标"待确认"，不自动判为薄弱。
 */
@Slf4j
@Service
public class AiNextActionService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 5;

    @Resource
    private AiSessionService sessionService;

    @Resource
    private EvidenceReadService evidenceReader;

    @Resource
    private ReviewPlanner reviewPlanner;

    @Resource
    private DiagnosisService diagnosisService;

    @Resource
    private HybridRetrievalService retrieval;

    @Resource
    private NextActionPlanner planner;

    public NextActionsVO nextActions(User user, String courseCode, Integer limit) {
        NextActionsVO vo = new NextActionsVO();
        vo.setGeneratedAt(AiJson.nowIso());
        if (StringUtils.isBlank(courseCode)) {
            vo.setActions(new ArrayList<>());
            return vo;
        }
        String course = courseCode.trim();
        int cap = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        AiLearningSession session = sessionService.latestActive(user.getId(), course);
        if (session == null) {
            AiRequests.CreateSession create = new AiRequests.CreateSession();
            create.setCourseCode(course);
            create.setFocus(AiSessionService.FOCUS_LEARN);
            create.setGoal("");
            sessionService.create(user, create);
            session = sessionService.latestActive(user.getId(), course);
        }
        if (session == null) {
            vo.setActions(new ArrayList<>());
            return vo;
        }
        vo.setSessionId(AiJson.id(session.getId()));
        vo.setFocus(session.getFocus());
        vo.setTimeBudgetMinutes(session.getTimeBudgetMinutes());
        Date now = new Date();
        vo.setDueReviewCount(reviewPlanner.dueCount(user.getId(), course, now));

        List<String> nodeCodes = new ArrayList<>();
        if (StringUtils.isNotBlank(session.getUnitCode())) {
            nodeCodes.add(session.getUnitCode());
        }
        EvidenceReadService.EvidenceSummary evidence = evidenceReader.read(user.getId(), nodeCodes, 12);
        List<LearningReviewItem> due = reviewPlanner.due(user.getId(), course, now, cap);

        DiagnosisService.Result diagnosisResult = null;
        if (evidence.getFailures() > 0) {
            DiagnosisService.Input input = new DiagnosisService.Input();
            input.setUserId(user.getId());
            input.setCourseCode(course);
            List<String> keys = new ArrayList<>(evidence.getWeakNodeCodes());
            keys.addAll(nodeCodes);
            input.setSkillKeys(keys);
            input.setEvidence(evidence);
            diagnosisResult = diagnosisService.diagnose(input);
        }

        HybridRetrievalService.Scope scope = new HybridRetrievalService.Scope();
        scope.setUserId(user.getId());
        scope.setCourseCode(course);
        scope.setNodeCodes(evidence.getWeakNodeCodes().isEmpty() ? nodeCodes : evidence.getWeakNodeCodes());
        String query = StringUtils.defaultIfBlank(session.getGoal(),
                evidence.getWeakNodeCodes().isEmpty() ? course : evidence.getWeakNodeCodes().get(0));
        HybridRetrievalService.Result retrieved = retrieval.retrieve(scope, query);

        NextActionPlanner.PlanInput input = new NextActionPlanner.PlanInput();
        input.setUser(user);
        input.setCourseCode(course);
        input.setFocus(session.getFocus());
        input.setTimeBudgetMinutes(session.getTimeBudgetMinutes() == null ? 0 : session.getTimeBudgetMinutes());
        input.setLimit(cap);
        input.setSessionId(session.getId());
        input.setEvidence(evidence);
        input.setDueReviews(due);
        input.setVerifications(diagnosisResult == null ? new ArrayList<>() : diagnosisResult.getVerifications());
        input.setDiagnosis(diagnosisResult == null ? null : diagnosisResult.getDiagnosis());
        input.setSources(retrieved.getSources());
        input.setUnitCode(session.getUnitCode());
        input.setNodeCodes(nodeCodes);
        NextAction current = AiJson.read(session.getCurrentActionJson(), NextAction.class);
        if (current != null) {
            input.setReturnRef(current.getTargetRef());
        }

        List<NextAction> actions = planner.plan(input);
        NextActionPlanner.assignIds(actions, session.getId());
        sessionService.savePlan(session, actions);
        vo.setActions(actions);
        return vo;
    }
}
