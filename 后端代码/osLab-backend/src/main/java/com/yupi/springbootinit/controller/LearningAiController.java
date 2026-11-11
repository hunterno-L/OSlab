package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.ai.plan.AiNextActionService;
import com.yupi.springbootinit.ai.retrieval.AiMaterialService;
import com.yupi.springbootinit.ai.retrieval.MaterialIndexService;
import com.yupi.springbootinit.ai.session.AiActionService;
import com.yupi.springbootinit.ai.session.AiHistoryService;
import com.yupi.springbootinit.ai.session.AiSessionService;
import com.yupi.springbootinit.ai.session.AiTurnService;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.model.dto.ai.AiRequests;
import com.yupi.springbootinit.model.entity.AiMaterial;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.ActionSubmitResultVO;
import com.yupi.springbootinit.model.vo.ai.ActivityRefVO;
import com.yupi.springbootinit.model.vo.ai.AiMaterialVO;
import com.yupi.springbootinit.model.vo.ai.AiHistoryVO;
import com.yupi.springbootinit.model.vo.ai.AiSessionVO;
import com.yupi.springbootinit.model.vo.ai.AiTurnVO;
import com.yupi.springbootinit.model.vo.ai.NextActionsVO;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.RateLimiter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 学习会话 / 轮次 / 动作 / 下一步 / 资料接口（任务书 §3 冻结契约）。
 *
 * <p>路径相对后端上下文 {@code /api}；沿用既有登录鉴权与
 * {@code BaseResponse{code,data,message}}；长整型 ID 在 JSON 中为字符串。
 *
 * <p>归属校验一律在服务端完成：浏览器只提供定位标识，
 * 服务端重新读取授权数据，不接受前端传来的正确率、掌握度或判题结论。
 *
 * <p>{@code POST /actions/{id}/help} 是本次升级的<b>新增</b>接口（不在冻结样例中）：
 * 计划 §5.5 要求"提示或答案揭示必须在答题事务之前被记录"，
 * 因此必须有一个先于提交、独立提交的帮助记录入口。已按契约扩展记录在交付报告中。
 */
@Slf4j
@RestController
@RequestMapping("/learning/ai")
public class LearningAiController {

    /** 轮次限流：同一用户 1 分钟最多 10 轮（真实模型调用成本保护）。 */
    private final RateLimiter turnLimiter = new RateLimiter(10, 60 * 1000L);

    @Resource
    private UserService userService;

    @Resource
    private AiSessionService sessionService;

    @Resource
    private AiTurnService turnService;

    @Resource
    private AiHistoryService historyService;

    @Resource
    private AiActionService actionService;

    @Resource
    private AiNextActionService nextActionService;

    @Resource
    private AiMaterialService materialService;

    @Resource
    private MaterialIndexService indexService;

    // region 会话

    @PostMapping("/sessions")
    public BaseResponse<AiSessionVO> createSession(@RequestBody AiRequests.CreateSession body,
                                                   HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(sessionService.create(user, body));
    }

    @GetMapping("/sessions/{id}")
    public BaseResponse<AiSessionVO> getSession(@PathVariable("id") String id, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(sessionService.get(user, id));
    }

    /**
     * 当前用户在指定课程下的会话列表（最近对话）。
     * 标题取既有目标或首条用户问题截断，不额外调用模型。
     */
    @GetMapping("/sessions")
    public BaseResponse<AiHistoryVO.SessionPage> listSessions(
            @RequestParam("courseCode") String courseCode,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(historyService.listSessions(user, courseCode, cursor, limit));
    }

    /** 轮次分页：真实持久数据；已关闭会话本人可只读查看；读取不产生新轮次。 */
    @GetMapping("/sessions/{id}/turns")
    public BaseResponse<AiHistoryVO.TurnPage> listTurns(
            @PathVariable("id") String id,
            @RequestParam(value = "before", required = false) String before,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(historyService.listTurns(user, id, before, limit));
    }

    @PatchMapping("/sessions/{id}")
    public BaseResponse<AiSessionVO> updateSession(@PathVariable("id") String id,
                                                   @RequestBody AiRequests.UpdateSession body,
                                                   HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(sessionService.update(user, id, body));
    }

    // endregion

    // region 轮次

    /**
     * 提交一轮提问：立即返回 RUNNING，前端按 &ge;1.5s 间隔轮询 GET /turns/{turnId}。
     * 相同 clientRequestId 返回同一轮次。
     */
    @PostMapping("/sessions/{id}/turns")
    public BaseResponse<AiTurnVO> createTurn(@PathVariable("id") String id,
                                             @RequestBody AiRequests.CreateTurn body,
                                             HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        if (!turnLimiter.tryAcquire("ai-turn:" + user.getId())) {
            return ResultUtils.error(ErrorCode.FORBIDDEN_ERROR, "提问过于频繁，请稍后再试");
        }
        return ResultUtils.success(turnService.submit(user, id, body));
    }

    @GetMapping("/turns/{turnId}")
    public BaseResponse<AiTurnVO> getTurn(@PathVariable("turnId") String turnId, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(turnService.get(user, turnId));
    }

    // endregion

    // region 动作

    /** 开始一个动作：返回提交前允许看到的内容（预测任务只给可见前缀）。 */
    @PostMapping("/actions/{id}/start")
    public BaseResponse<Map<String, Object>> startAction(@PathVariable("id") String id,
                                                         HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ActivityRefVO activityRef = actionService.start(user, id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actionId", activityRef.getActionId());
        data.put("status", "STARTED");
        data.put("activityRef", activityRef);
        return ResultUtils.success(data);
    }

    /** 提交动作：委托原有判分服务与 PageReplacer，返回结果与证据快照。 */
    @PostMapping("/actions/{id}/submit")
    public BaseResponse<ActionSubmitResultVO> submitAction(@PathVariable("id") String id,
                                                           @RequestBody AiRequests.SubmitAction body,
                                                           HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(actionService.submit(user, id, body));
    }

    /**
     * 记录帮助（提示 / 揭示答案 / 自报外部帮助）。
     *
     * <p>必须在提交之前调用：帮助等级以独立事务落库，
     * 随后的提交事务以行锁读取并据此判断证据资格。
     */
    @PostMapping("/actions/{id}/help")
    public BaseResponse<Map<String, Object>> recordHelp(@PathVariable("id") String id,
                                                        @RequestBody HelpRequest body,
                                                        HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        if (body == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        EvidenceQualification qualification = actionService.recordHelp(user, id,
                body.getHelpLevel(), body.getSelfReportedHelp(), body.getNote());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actionId", id);
        data.put("helpLevel", qualification.getHelpLevel());
        data.put("selfReportedHelp", qualification.getSelfReportedHelp());
        data.put("eligibleForObservation", qualification.isEligibleForObservation());
        data.put("eligibilityReason", qualification.getEligibilityReason());
        return ResultUtils.success(data);
    }

    @Data
    public static class HelpRequest implements Serializable {
        /** HINT / ANSWER_REVEALED / NONE */
        private String helpLevel;
        /** NONE / EXTERNAL_AI / PEER / OTHER */
        private String selfReportedHelp;
        private String note;
    }

    // endregion

    // region 下一步

    @GetMapping("/next-actions")
    public BaseResponse<NextActionsVO> nextActions(@RequestParam(value = "courseCode", required = false) String courseCode,
                                                   @RequestParam(value = "limit", required = false) Integer limit,
                                                   HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(nextActionService.nextActions(user, courseCode, limit));
    }

    // endregion

    // region 资料

    @PostMapping("/materials")
    public BaseResponse<AiMaterialVO> createMaterial(@RequestBody AiRequests.CreateMaterial body,
                                                     HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(materialService.create(user, body));
    }

    @GetMapping("/materials")
    public BaseResponse<List<AiMaterialVO>> listMaterials(
            @RequestParam(value = "courseCode", required = false) String courseCode,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(materialService.list(user, courseCode));
    }

    @DeleteMapping("/materials/{id}")
    public BaseResponse<Boolean> deleteMaterial(@PathVariable("id") String id, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(materialService.delete(user, id));
    }

    // endregion

    // region 索引维护

    /**
     * 重建课程已发布正文索引（增量）。
     *
     * <p>课程正文对该课程所有学习者可见，因此只允许管理员/教师触发；
     * 个人资料索引由 {@code POST /materials} 自动完成。
     */
    @PostMapping("/index/rebuild")
    public BaseResponse<Map<String, Object>> rebuildIndex(@RequestBody RebuildRequest body,
                                                          HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        if (!userService.isAdmin(user)) {
            return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "仅管理员可重建课程索引");
        }
        if (body == null || StringUtils.isBlank(body.getCourseCode())) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "courseCode 不能为空");
        }
        MaterialIndexService.IndexReport report = indexService.indexCourse(body.getCourseCode().trim());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseCode", report.getCourseCode());
        data.put("blocks", report.getBlocks());
        data.put("written", report.getWritten());
        data.put("skipped", report.getSkipped());
        data.put("removed", report.getRemoved());
        data.put("embedded", report.getEmbedded());
        data.put("embeddingModel", report.getEmbeddingModel());
        data.put("degraded", report.isDegraded());
        data.put("message", report.getMessage());
        return ResultUtils.success(data);
    }

    /** 重建本人个人资料索引（换 embedding 模型后使用）。 */
    @PostMapping("/index/rebuild-personal")
    public BaseResponse<Map<String, Object>> rebuildPersonalIndex(@RequestBody RebuildRequest body,
                                                                  HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        int count = materialService.reindexPersonal(user, body == null ? null : body.getCourseCode());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("materials", count);
        return ResultUtils.success(data);
    }

    @Data
    public static class RebuildRequest implements Serializable {
        private String courseCode;
    }

    /** 供演示脚本读取的资料明细（校验所有者）。 */
    @GetMapping("/materials/{id}")
    public BaseResponse<Map<String, Object>> getMaterial(@PathVariable("id") String id,
                                                         HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        AiMaterial material = materialService.requireOwn(user, id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.putAll(new LinkedHashMap<>(java.util.Collections.singletonMap("material",
                materialService.toVo(material))));
        data.put("contentMd", material.getContentMd());
        return ResultUtils.success(data);
    }

    // endregion
}
