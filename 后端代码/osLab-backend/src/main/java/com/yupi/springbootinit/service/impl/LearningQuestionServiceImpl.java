package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.learning.LearningQuestionGrader;
import com.yupi.springbootinit.learning.LearningQuestionScopeService;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.mapper.LearningReviewMapper;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.dto.learning.LearningCatalogRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionAddRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionReviewRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionUpdateRequest;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.LearningReview;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.learning.LearningQuestionTypeEnum;
import com.yupi.springbootinit.model.enums.learning.LearningReviewStatusEnum;
import com.yupi.springbootinit.model.enums.learning.ReviewActionEnum;
import com.yupi.springbootinit.model.vo.learning.ImportPreviewVO;
import com.yupi.springbootinit.model.vo.learning.ImportResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogChapterVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogNodeVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogSectionVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogUnitVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionManageVO;
import com.yupi.springbootinit.model.vo.learning.MasteryChangeVO;
import com.yupi.springbootinit.service.LearningEventService;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 普通题服务实现（WP1）
 */
@Slf4j
@Service
public class LearningQuestionServiceImpl extends ServiceImpl<LearningQuestionMapper, LearningQuestion>
        implements LearningQuestionService {

    @Resource
    private LearningQuestionNodeMapper learningQuestionNodeMapper;

    /** 目录聚合缓存：单次 ~500ms，短 TTL 降低重复计算，作答/审核后失效 */
    private static final long CATALOG_CACHE_TTL_MS = 10_000L;
    private final java.util.concurrent.ConcurrentHashMap<String, CatalogCacheItem> catalogCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final class CatalogCacheItem {
        private final LearningCatalogVO value;
        private final long at;

        private CatalogCacheItem(LearningCatalogVO value, long at) {
            this.value = value;
            this.at = at;
        }
    }

    private void invalidateCatalogCache(Long userId) {
        if (userId == null) {
            catalogCache.clear();
            return;
        }
        String prefix = userId + ":";
        catalogCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Resource
    private LearningAttemptMapper learningAttemptMapper;

    @Resource
    private LearningAssessmentRunMapper learningAssessmentRunMapper;

    @Resource
    private LearningReviewMapper learningReviewMapper;

    @Resource
    private LearningEventService learningEventService;


    @Resource
    private UserService userService;
    @Resource
    private com.yupi.springbootinit.learning.TeachingAssessmentService teachingAssessments;
    @Resource
    private com.yupi.springbootinit.learning.ActivityContextService activityContext;
    @Resource
    private com.yupi.springbootinit.learning.NodeIdentityService nodeIdentities;

    @Resource
    private com.yupi.springbootinit.learning.LearningQuestionScopeService questionScope;

    @Resource
    private com.yupi.springbootinit.task.TaskExecutionContextService taskContext;

    // region CRUD

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addQuestion(LearningQuestionAddRequest request, User author) {
        validateRequest(request);
        LearningQuestion question = new LearningQuestion();
        BeanUtils.copyProperties(request, question);
        question.setOptions(serializeOptions(request.getOptions()));
        question.setAnswer(answerToJson(request.getAnswer()));
        question.setDifficulty(request.getDifficulty() == null ? 2 : request.getDifficulty());
        question.setReviewStatus(LearningReviewStatusEnum.DRAFT.name());
        question.setUserId(author.getId());
        question.setIsDelete(0);
        boolean saved = this.save(question);
        ThrowUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "题目保存失败");
        LearningQuestion stableIdentity = new LearningQuestion();
        stableIdentity.setId(question.getId());
        stableIdentity.setQuestionCode("q.legacy." + question.getId());
        this.updateById(stableIdentity);
        saveNodeMappings(question.getId(), request.getNodeNames());
        return question.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateQuestion(LearningQuestionUpdateRequest request, User user) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        LearningQuestion old = this.getById(request.getId());
        ThrowUtils.throwIf(old == null || old.getIsDelete() == 1, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        ThrowUtils.throwIf(com.yupi.springbootinit.task.TaskConstants.SCOPE_TASK.equals(old.getResourceScope()),
                ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        checkOwnerOrAdmin(old, user);
        if (teachingAssessments != null && (LearningReviewStatusEnum.PUBLISHED.name().equals(old.getReviewStatus())
                || teachingAssessments.draft(old.getId()) != null)) {
            validateRequest(request);
            teachingAssessments.saveEditorRevision(request, old, user.getId());
            return true;
        }
        // 仅 DRAFT/REJECTED/OFFLINE 可编辑；编辑后回到 DRAFT
        String status = old.getReviewStatus();
        boolean editable = LearningReviewStatusEnum.DRAFT.name().equals(status)
                || LearningReviewStatusEnum.REJECTED.name().equals(status)
                || LearningReviewStatusEnum.OFFLINE.name().equals(status);
        ThrowUtils.throwIf(!editable, ErrorCode.OPERATION_ERROR,
                "当前状态（" + status + "）不可编辑，请先下线或等待审核结果");
        validateRequest(request);
        LearningQuestion update = new LearningQuestion();
        BeanUtils.copyProperties(request, update);
        update.setId(old.getId());
        update.setOptions(serializeOptions(request.getOptions()));
        update.setAnswer(answerToJson(request.getAnswer()));
        update.setReviewStatus(LearningReviewStatusEnum.DRAFT.name());
        boolean updated = this.updateById(update);
        ThrowUtils.throwIf(!updated, ErrorCode.SYSTEM_ERROR, "题目更新失败");
        // 重新绑定节点映射
        learningQuestionNodeMapper.delete(new LambdaQueryWrapper<LearningQuestionNode>()
                .eq(LearningQuestionNode::getQuestionId, old.getId()));
        saveNodeMappings(old.getId(), request.getNodeNames());
        return updated;
    }

    // endregion

    // region 查询

    @Override
    public LearningQuestionVO getQuestionVO(long questionId, User user) {
        LearningQuestion question = this.getById(questionId);
        ThrowUtils.throwIf(question == null || question.getIsDelete() == 1, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        ThrowUtils.throwIf(com.yupi.springbootinit.task.TaskConstants.SCOPE_TASK.equals(question.getResourceScope()),
                ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        boolean owner = question.getUserId().equals(user.getId());
        boolean admin = userService.isAdmin(user);
        boolean published = LearningReviewStatusEnum.isStudentVisible(question.getReviewStatus());
        ThrowUtils.throwIf(!published && !owner && !admin, ErrorCode.NOT_FOUND_ERROR, "题目不存在或未发布");
        return toVO(question, user, owner || admin);
    }

    @Override
    public List<LearningQuestionVO> getQuestionVOs(List<Long> questionIds, User user) {
        if (CollectionUtils.isEmpty(questionIds)) {
            return Collections.emptyList();
        }
        List<LearningQuestion> questions = this.list(new LambdaQueryWrapper<LearningQuestion>()
                .in(LearningQuestion::getId, questionIds)
                .eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name())
                .eq(LearningQuestion::getIsDelete, 0)
                .eq(LearningQuestion::getResourceScope, com.yupi.springbootinit.task.TaskConstants.SCOPE_SHARED)
                .orderByAsc(LearningQuestion::getDifficulty)
                .orderByAsc(LearningQuestion::getId));
        if (CollectionUtils.isEmpty(questions)) {
            return Collections.emptyList();
        }

        List<Long> visibleIds = questions.stream().map(LearningQuestion::getId).collect(Collectors.toList());
        Map<Long, List<String>> nodeNamesByQuestion = new LinkedHashMap<>();
        for (LearningQuestionNode mapping : learningQuestionNodeMapper.selectList(
                new LambdaQueryWrapper<LearningQuestionNode>()
                        .in(LearningQuestionNode::getQuestionId, visibleIds))) {
            nodeNamesByQuestion.computeIfAbsent(mapping.getQuestionId(), ignored -> new ArrayList<>())
                    .add(mapping.getNodeName());
        }
        Map<Long, List<LearningAttempt>> attemptsByQuestion = new HashMap<>();
        for (LearningAttempt attempt : learningAttemptMapper.selectList(
                new LambdaQueryWrapper<LearningAttempt>()
                        .eq(LearningAttempt::getUserId, user.getId())
                        .in(LearningAttempt::getQuestionId, visibleIds)
                        .orderByAsc(LearningAttempt::getCreateTime)
                        .orderByAsc(LearningAttempt::getId))) {
            attemptsByQuestion.computeIfAbsent(attempt.getQuestionId(), ignored -> new ArrayList<>()).add(attempt);
        }

        List<LearningQuestionVO> result = new ArrayList<>(questions.size());
        for (LearningQuestion question : questions) {
            LearningQuestionVO vo = new LearningQuestionVO();
            BeanUtils.copyProperties(question, vo);
            vo.setAnalysis(null);
            vo.setMisconception(null);
            vo.setNodeNames(nodeNamesByQuestion.getOrDefault(question.getId(), Collections.emptyList()));
            if(nodeIdentities!=null)vo.setNodeDisplayNames(nodeIdentities.displayNames(vo.getNodeNames()));
            List<LearningAttempt> attempts = attemptsByQuestion.getOrDefault(question.getId(), Collections.emptyList());
            vo.setHasAttempted(!attempts.isEmpty());
            vo.setAttemptCount(attempts.size());
            if (!attempts.isEmpty()) {
                vo.setFirstResult(resultLabel(attempts.get(0).getIsCorrect()));
                vo.setLatestResult(resultLabel(attempts.get(attempts.size() - 1).getIsCorrect()));
                vo.setAnalysis(question.getAnalysis());
                vo.setMisconception(question.getMisconception());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public IPage<LearningQuestionVO> pageVO(LearningQuestionQueryRequest request, User user) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "每页最多 50 条");
        boolean admin = userService.isAdmin(user);
        boolean reviewQueue = Boolean.TRUE.equals(request.getReviewQueue());
        boolean myOnly = Boolean.TRUE.equals(request.getMyOnly());
        ThrowUtils.throwIf(reviewQueue && !admin, ErrorCode.NO_AUTH_ERROR, "仅管理员可查看审核队列");

        LambdaQueryWrapper<LearningQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningQuestion::getResourceScope, com.yupi.springbootinit.task.TaskConstants.SCOPE_SHARED);
        if (reviewQueue) {
            // 审核队列：全部非删除题目（可按状态筛选）
            if (StringUtils.isNotBlank(request.getReviewStatus())) {
                wrapper.eq(LearningQuestion::getReviewStatus, request.getReviewStatus());
            }
        } else if (myOnly) {
            wrapper.eq(LearningQuestion::getUserId, user.getId());
            if (StringUtils.isNotBlank(request.getReviewStatus())) {
                wrapper.eq(LearningQuestion::getReviewStatus, request.getReviewStatus());
            }
        } else {
            // 学生视角：只看已发布
            wrapper.eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name());
        }
        if (StringUtils.isNotBlank(request.getTitle())) {
            wrapper.like(LearningQuestion::getTitle, request.getTitle());
        }
        applyTypeFilter(wrapper, request.getType());
        // 章节过滤（阶段 5 §5.1）：不在 SQL 另写一套，统一在内存走共享谓词 matchesChapter，
        // 与目录统计、测评候选保持同一语义（"未归类"命中 null/空白/字面值章节）
        String chapterFilter = StringUtils.trimToNull(request.getChapter());
        if (request.getDifficulty() != null) {
            wrapper.eq(LearningQuestion::getDifficulty, request.getDifficulty());
        }
        // 节点过滤
        if (StringUtils.isNotBlank(request.getNodeName())) {
            List<Long> ids = learningQuestionNodeMapper.selectList(
                    new LambdaQueryWrapper<LearningQuestionNode>()
                            .eq(LearningQuestionNode::getNodeName, request.getNodeName()))
                    .stream().map(LearningQuestionNode::getQuestionId).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(ids)) {
                return new Page<>(current, size, 0);
            }
            wrapper.in(LearningQuestion::getId, ids);
        }
        // 稳定目录/节点范围：与 catalog、测评候选共用同一解析器（空集直接返回空页）
        LearningQuestionScopeService.Scope scope = questionScope == null ? null : questionScope.resolve(
                request.getCourseCode(), request.getSectionCode(), request.getUnitCode(), request.getNodeCode());
        if (scope != null && scope.isConstrained()) {
            Set<Long> scopedIds = scope.getQuestionIds();
            if (CollectionUtils.isEmpty(scopedIds)) {
                return new Page<>(current, size, 0);
            }
            wrapper.in(LearningQuestion::getId, scopedIds);
        }
        wrapper.orderByDesc(LearningQuestion::getCreateTime);

        String studyState = request.getStudyState();
        boolean studentScope = !reviewQueue && !myOnly;
        boolean studyStateFilter = studentScope && StringUtils.isNotBlank(studyState)
                && !"ALL".equalsIgnoreCase(studyState);
        // 章节/学习状态过滤命中任一内存谓词时：一次取全量候选，谓词过滤后内存分页
        //（题目量级 ≤ 百级，可接受；与测评候选共用同一谓词语义）
        if (chapterFilter != null || studyStateFilter) {
            List<LearningQuestion> candidates = this.list(wrapper);
            if (chapterFilter != null) {
                candidates = candidates.stream()
                        .filter(q -> matchesChapter(q.getChapter(), chapterFilter))
                        .collect(Collectors.toList());
            }
            // 本人作答（排序与 SQL 一致：createTime asc, id asc），学习状态过滤与作答统计共用
            Map<Long, List<LearningAttempt>> byQuestion = new HashMap<>();
            if (studentScope) {
                for (LearningAttempt a : learningAttemptMapper.selectList(
                        new LambdaQueryWrapper<LearningAttempt>()
                                .eq(LearningAttempt::getUserId, user.getId())
                                .orderByAsc(LearningAttempt::getCreateTime)
                                .orderByAsc(LearningAttempt::getId))) {
                    byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
                }
            }
            if (studyStateFilter) {
                candidates = candidates.stream()
                        .filter(q -> matchesStudyState(byQuestion.get(q.getId()), studyState))
                        .collect(Collectors.toList());
            }
            long total = candidates.size();
            long from = Math.min((current - 1) * size, total);
            long to = Math.min(from + size, total);
            Page<LearningQuestionVO> result = new Page<>(current, size, total);
            List<LearningQuestionVO> records = new ArrayList<>();
            for (LearningQuestion q : candidates.subList((int) from, (int) to)) {
                if (studentScope) {
                    boolean attempted = CollectionUtils.isNotEmpty(byQuestion.get(q.getId()));
                    records.add(toVO(q, user, admin || q.getUserId().equals(user.getId()), attempted));
                } else {
                    records.add(toVO(q, user, admin || q.getUserId().equals(user.getId())));
                }
            }
            if (studentScope) {
                fillAttemptFields(records, byQuestion);
            }
            result.setRecords(records);
            return result;
        }

        IPage<LearningQuestion> page = this.page(new Page<>(current, size), wrapper);
        IPage<LearningQuestionVO> voPage = page.convert(q -> toVO(q, user, admin || q.getUserId().equals(user.getId())));
        if (studentScope) {
            fillAttemptFields(voPage.getRecords(), user.getId());
        }
        return voPage;
    }

    /**
     * 学习状态过滤口径（pageVO 与测评候选共用）：按本人最新有效作答判定
     * UNANSWERED/NEEDS_REVIEW/CORRECT，与目录统计 attempted/needsReview/correct 同义。
     */
    private static boolean matchesStudyState(List<LearningAttempt> attempts, String studyState) {
        int count = attempts == null ? 0 : attempts.size();
        boolean latestCorrect = count > 0 && attempts.get(count - 1).getIsCorrect() != null
                && attempts.get(count - 1).getIsCorrect() == 1;
        switch (studyState.toUpperCase()) {
            case "UNANSWERED":
                return count == 0;
            case "NEEDS_REVIEW":
                return count > 0 && !latestCorrect;
            case "CORRECT":
                return count > 0 && latestCorrect;
            default:
                return true;
        }
    }

    /**
     * 测评候选完整 ID 集（阶段 5 §5.2）：学生口径（仅 PUBLISHED 且未删除）+
     * 章节（共享谓词）/节点/题型/难度过滤 + 本人学习状态过滤，
     * 返回全部符合条件题目 id（id 升序，无分页上限；题目量级 ≤ 百级，全量查询可接受）。
     * 保留个人状态过滤：学习状态只看当前用户本人的作答，不串其他用户。
     */
    @Override
    public List<Long> listEligibleQuestionIds(LearningQuestionQueryRequest request, User user) {
        ThrowUtils.throwIf(request == null || user == null, ErrorCode.PARAMS_ERROR);
        LambdaQueryWrapper<LearningQuestion> wrapper = new LambdaQueryWrapper<>();
        // 测评为学生功能：固定学生口径（仅已发布），不开放审核队列/我的题目范围
        wrapper.eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name());
        wrapper.eq(LearningQuestion::getIsDelete, 0);
        wrapper.eq(LearningQuestion::getResourceScope, com.yupi.springbootinit.task.TaskConstants.SCOPE_SHARED);
        wrapper.ne(LearningQuestion::getType, LearningQuestionTypeEnum.COMPREHENSIVE.name());
        applyTypeFilter(wrapper, request.getType());
        if (request.getDifficulty() != null) {
            wrapper.eq(LearningQuestion::getDifficulty, request.getDifficulty());
        }
        if (StringUtils.isNotBlank(request.getNodeName())) {
            List<Long> ids = learningQuestionNodeMapper.selectList(
                    new LambdaQueryWrapper<LearningQuestionNode>()
                            .eq(LearningQuestionNode::getNodeName, request.getNodeName()))
                    .stream().map(LearningQuestionNode::getQuestionId).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(ids)) {
                return Collections.emptyList();
            }
            wrapper.in(LearningQuestion::getId, ids);
        }
        LearningQuestionScopeService.Scope scope = questionScope == null ? null : questionScope.resolve(
                request.getCourseCode(), request.getSectionCode(), request.getUnitCode(), request.getNodeCode());
        if (scope != null && scope.isConstrained()) {
            Set<Long> scopedIds = scope.getQuestionIds();
            if (CollectionUtils.isEmpty(scopedIds)) {
                return Collections.emptyList();
            }
            wrapper.in(LearningQuestion::getId, scopedIds);
        }
        // 服务端稳定顺序：id 升序（抽样 = 取前 count，确定性可复算）
        wrapper.orderByAsc(LearningQuestion::getId);
        List<LearningQuestion> candidates = this.list(wrapper);
        // 章节过滤：与目录统计/题目分页同一共享谓词
        String chapterFilter = StringUtils.trimToNull(request.getChapter());
        if (chapterFilter != null) {
            candidates = candidates.stream()
                    .filter(q -> matchesChapter(q.getChapter(), chapterFilter))
                    .collect(Collectors.toList());
        }
        // 本人学习状态过滤（语义与 pageVO studyState 完全一致）
        String studyState = request.getStudyState();
        if (StringUtils.isNotBlank(studyState) && !"ALL".equalsIgnoreCase(studyState)) {
            List<LearningAttempt> attempts = learningAttemptMapper.selectList(
                    new LambdaQueryWrapper<LearningAttempt>()
                            .eq(LearningAttempt::getUserId, user.getId())
                            .orderByAsc(LearningAttempt::getCreateTime)
                            .orderByAsc(LearningAttempt::getId));
            Map<Long, List<LearningAttempt>> byQuestion = new HashMap<>();
            for (LearningAttempt a : attempts) {
                byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
            }
            candidates = candidates.stream()
                    .filter(q -> matchesStudyState(byQuestion.get(q.getId()), studyState))
                    .collect(Collectors.toList());
        }
        return candidates.stream().map(LearningQuestion::getId).collect(Collectors.toList());
    }

    /** 批量填充当前用户作答统计（attemptCount/latestResult/firstResult），不含答案。 */
    private void fillAttemptFields(List<LearningQuestionVO> records, Long userId) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        List<Long> questionIds = records.stream().map(LearningQuestionVO::getId).collect(Collectors.toList());
        List<LearningAttempt> attempts = learningAttemptMapper.selectList(
                new LambdaQueryWrapper<LearningAttempt>()
                        .eq(LearningAttempt::getUserId, userId)
                        .in(LearningAttempt::getQuestionId, questionIds)
                        .orderByAsc(LearningAttempt::getCreateTime));
        Map<Long, List<LearningAttempt>> byQuestion = new HashMap<>();
        for (LearningAttempt a : attempts) {
            byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
        }
        fillAttemptFields(records, byQuestion);
    }

    private void fillAttemptFields(List<LearningQuestionVO> records, Map<Long, List<LearningAttempt>> byQuestion) {
        for (LearningQuestionVO vo : records) {
            List<LearningAttempt> list = byQuestion.get(vo.getId());
            int count = list == null ? 0 : list.size();
            vo.setAttemptCount(count);
            vo.setHasAttempted(count > 0);
            if (count > 0) {
                vo.setLatestResult(resultLabel(list.get(count - 1).getIsCorrect()));
                vo.setFirstResult(resultLabel(list.get(0).getIsCorrect()));
            }
        }
    }

    /** 管理员 VO 组装：学生字段 + 正确答案 + 审核历史。 */
    private LearningQuestionManageVO toManageVO(LearningQuestion question) {
        LearningQuestionManageVO vo = new LearningQuestionManageVO();
        BeanUtils.copyProperties(question, vo);
        vo.setCorrectAnswer(question.getAnswer());
        vo.setNodeNames(learningQuestionNodeMapper.selectList(
                        new LambdaQueryWrapper<LearningQuestionNode>()
                                .eq(LearningQuestionNode::getQuestionId, question.getId()))
                .stream().map(LearningQuestionNode::getNodeName).collect(Collectors.toList()));
        vo.setAnalysis(question.getAnalysis());
        vo.setMisconception(question.getMisconception());
        List<com.yupi.springbootinit.model.vo.learning.LearningQuestionManageVO.ReviewRecordVO> history =
                new ArrayList<>();
        List<LearningReview> reviews = learningReviewMapper.selectList(
                new LambdaQueryWrapper<LearningReview>()
                        .eq(LearningReview::getQuestionId, question.getId())
                        .orderByAsc(LearningReview::getCreateTime));
        for (LearningReview review : reviews) {
            var record = new com.yupi.springbootinit.model.vo.learning.LearningQuestionManageVO.ReviewRecordVO();
            record.setReviewerId(review.getReviewerId());
            record.setAction(review.getAction());
            record.setReason(review.getReason());
            record.setCreateTime(review.getCreateTime());
            User reviewer = userService.getById(review.getReviewerId());
            record.setReviewerName(reviewer == null ? "未知"
                    : StringUtils.defaultIfBlank(reviewer.getUserName(), reviewer.getUserAccount()));
            history.add(record);
        }
        vo.setReviewHistory(history);
        if (teachingAssessments != null) {
            Map<String,Object> draft = teachingAssessments.draft(question.getId());
            if (draft != null) {
                com.fasterxml.jackson.databind.JsonNode content = (com.fasterxml.jackson.databind.JsonNode) draft.get("content");
                vo.setActiveStatus(question.getReviewStatus());
                vo.setPublishedRevision(question.getRevision());
                vo.setReviewStatus(draft.get("reviewStatus").toString());
                vo.setRevision(((Number)draft.get("revision")).intValue());
                vo.setTitle(content.path("title").asText());
                vo.setContent(content.path("content").asText());
                vo.setType(content.path("type").asText());
                vo.setOptions(content.path("options").toString());
                vo.setCorrectAnswer(content.path("answer").toString());
                vo.setAnalysis(content.path("analysis").asText());
                vo.setMisconception(content.path("misconception").asText());
                vo.setDifficulty(content.path("difficulty").asInt());
                vo.setChapter(content.path("sectionCode").asText());
            }
        }
        return vo;
    }

    @Override
    public LearningQuestionManageVO getManageQuestionVO(long questionId, User user) {
        LearningQuestion question = this.getById(questionId);
        ThrowUtils.throwIf(question == null || question.getIsDelete() == 1,
                ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        ThrowUtils.throwIf(com.yupi.springbootinit.task.TaskConstants.SCOPE_TASK.equals(question.getResourceScope()),
                ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        return toManageVO(question);
    }

    @Override
    public IPage<LearningQuestionManageVO> pageManageVO(LearningQuestionQueryRequest request, User user) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "每页最多 50 条");
        LambdaQueryWrapper<LearningQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningQuestion::getResourceScope, com.yupi.springbootinit.task.TaskConstants.SCOPE_SHARED);
        if (Boolean.TRUE.equals(request.getMyOnly())) {
            wrapper.eq(LearningQuestion::getUserId, user.getId());
        }
        if (StringUtils.isNotBlank(request.getReviewStatus())) {
            if (teachingAssessments == null) wrapper.eq(LearningQuestion::getReviewStatus, request.getReviewStatus());
            else {
                List<Long> revisedIds = teachingAssessments.questionIdsByDraftStatus(request.getReviewStatus());
                wrapper.and(w -> {
                    w.eq(LearningQuestion::getReviewStatus, request.getReviewStatus());
                    if (!revisedIds.isEmpty()) w.or().in(LearningQuestion::getId, revisedIds);
                });
            }
        }
        if (StringUtils.isNotBlank(request.getTitle())) {
            wrapper.like(LearningQuestion::getTitle, request.getTitle());
        }
        applyTypeFilter(wrapper, request.getType());
        if (StringUtils.isNotBlank(request.getChapter())) {
            wrapper.eq(LearningQuestion::getChapter, request.getChapter());
        }
        if (request.getDifficulty() != null) {
            wrapper.eq(LearningQuestion::getDifficulty, request.getDifficulty());
        }
        if (StringUtils.isNotBlank(request.getNodeName())) {
            List<Long> ids = learningQuestionNodeMapper.selectList(
                    new LambdaQueryWrapper<LearningQuestionNode>()
                            .eq(LearningQuestionNode::getNodeName, request.getNodeName()))
                    .stream().map(LearningQuestionNode::getQuestionId).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(ids)) {
                return new Page<>(current, size, 0);
            }
            wrapper.in(LearningQuestion::getId, ids);
        }
        wrapper.orderByDesc(LearningQuestion::getCreateTime);
        IPage<LearningQuestion> page = this.page(new Page<>(current, size), wrapper);
        return page.convert(this::toManageVO);
    }

    // endregion

    // region 目录聚合（阶段 4：章节 -> 知识点 -> 题目数/完成数/待复习数）

    /** 章节为空（null/空白）与字面值"未归类"的统一归类名称，同时作为目录过滤的哨兵值 */
    public static final String UNCATEGORIZED_CHAPTER = "未归类";

    /** 章节与知识点名称排序（中文拼音序，并列时按码点序裁决） */
    private static final Collator NAME_COLLATOR = Collator.getInstance(Locale.CHINA);

    // region 章节共享谓词（阶段 5 §5.1：目录统计/题目分页/测评候选三处必须共用，不得各写一套）

    /**
     * 章节归一化：null、空串、纯空白与字面值"未归类"都归一为"未归类"；
     * 其余章节去除首尾空白后原样返回。
     */
    public static String chapterKey(String chapter) {
        String trimmed = StringUtils.trimToEmpty(chapter);
        if (trimmed.isEmpty() || UNCATEGORIZED_CHAPTER.equals(trimmed)) {
            return UNCATEGORIZED_CHAPTER;
        }
        return trimmed;
    }

    /**
     * 章节过滤匹配（共享谓词）：过滤条件为 null/空白 = 不过滤（全部）；
     * 过滤"未归类"同时命中章节为 null/空白/字面值"未归类"的题目；
     * 其余按归一化章节精确匹配。questionChapter 与 filterChapter 都走 {@link #chapterKey}。
     */
    public static boolean matchesChapter(String questionChapter, String filterChapter) {
        String filter = StringUtils.trimToNull(filterChapter);
        if (filter == null) {
            return true;
        }
        return chapterKey(questionChapter).equals(chapterKey(filter));
    }

    // endregion

    /**
     * 题目练习目录聚合。SQL 只取三份原始数据（题目、节点映射、本人作答各一次查询），
     * 其余全部内存聚合：题目按 questionId 去重，章节/节点计数为去重后的局部口径，
     * attempted/needsReview/correct 与 pageVO 的 studyState 过滤完全同义（本人最新有效作答）。
     */
    @Override
    public LearningCatalogVO questionCatalog(String chapter, User user) {
        LearningCatalogRequest request = new LearningCatalogRequest();
        request.setChapter(chapter);
        return questionCatalog(request, user);
    }

    @Override
    public LearningCatalogVO questionCatalog(LearningCatalogRequest request, User user) {
        LearningCatalogRequest req = request == null ? new LearningCatalogRequest() : request;
        // course isolation: default to operating systems when courseCode is blank
        if (StringUtils.isBlank(req.getCourseCode())) {
            req.setCourseCode("course.operating-systems");
        }
        String chapterFilter = StringUtils.trimToNull(req.getChapter());
        String studyState = StringUtils.trimToNull(req.getStudyState());
        String cacheKey = null;
        if (user != null && user.getId() != null) {
            cacheKey = user.getId() + ":"
                    + StringUtils.trimToEmpty(req.getCourseCode()) + "|"
                    + StringUtils.trimToEmpty(req.getSectionCode()) + "|"
                    + StringUtils.trimToEmpty(req.getUnitCode()) + "|"
                    + StringUtils.trimToEmpty(chapterFilter) + "|"
                    + StringUtils.trimToEmpty(studyState) + "|"
                    + StringUtils.trimToEmpty(req.getType());
            CatalogCacheItem cached = catalogCache.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - cached.at < CATALOG_CACHE_TTL_MS) {
                return cached.value;
            }
        }

        // 目录范围：列表/catalog/测评共用同一稳定代码解析器
        LearningQuestionScopeService.Scope scope = questionScope == null ? null : questionScope.resolve(
                req.getCourseCode(), req.getSectionCode(), req.getUnitCode(), null);
        boolean hasDirectoryScope = scope != null && scope.isDirectoryConstrained();
        boolean narrowed = StringUtils.isNotBlank(req.getSectionCode())
                || StringUtils.isNotBlank(req.getUnitCode());
        // 目录树题量按完整课程统计；当前 section/unit 只收窄“当前范围”的节点与总数
        LearningQuestionScopeService.Scope treeScope = scope;
        if (hasDirectoryScope && narrowed && scope.getCourseCode() != null) {
            treeScope = questionScope.resolve(scope.getCourseCode(), null, null, null);
        }

        List<LearningQuestion> candidates;
        if (hasDirectoryScope) {
            Set<Long> ids = scope.getQuestionIds();
            candidates = CollectionUtils.isEmpty(ids)
                    ? Collections.emptyList()
                    : this.list(studentQuestionWrapper().in(LearningQuestion::getId, ids));
        } else {
            candidates = this.list(studentQuestionWrapper());
        }
        List<LearningQuestion> treeCandidates;
        if (treeScope == scope) {
            treeCandidates = candidates;
        } else if (treeScope.isDirectoryConstrained()) {
            Set<Long> ids = treeScope.getQuestionIds();
            treeCandidates = CollectionUtils.isEmpty(ids)
                    ? Collections.emptyList()
                    : this.list(studentQuestionWrapper().in(LearningQuestion::getId, ids));
        } else {
            treeCandidates = Collections.emptyList();
        }

        Map<String, Integer> typeCounts = catalogTypeCounts(candidates);
        candidates = filterByQuestionType(candidates, req.getType());
        treeCandidates = filterByQuestionType(treeCandidates, req.getType());

        Set<Long> candidateIds = candidates.stream().map(LearningQuestion::getId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> treeIds = treeCandidates.stream().map(LearningQuestion::getId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> allIds = new LinkedHashSet<>(candidateIds);
        allIds.addAll(treeIds);

        Map<Long, List<LearningQuestionNode>> mappingsByQuestion = new LinkedHashMap<>();
        if (!allIds.isEmpty()) {
            for (LearningQuestionNode mapping : learningQuestionNodeMapper.selectList(
                    new LambdaQueryWrapper<LearningQuestionNode>()
                            .in(LearningQuestionNode::getQuestionId, allIds))) {
                if (mapping.getQuestionId() == null) continue;
                mappingsByQuestion.computeIfAbsent(mapping.getQuestionId(), ignored -> new ArrayList<>())
                        .add(mapping);
            }
        }
        Map<Long, LearningAttempt> latestAttemptByQuestion = latestAttempts(allIds, user.getId());

        List<LearningQuestion> visible = filterCatalogQuestions(candidates, chapterFilter, studyState,
                latestAttemptByQuestion);
        Set<Long> visibleIds = visible.stream().map(LearningQuestion::getId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> treeVisibleIds = treeScope == scope
                ? visibleIds
                : filterCatalogQuestions(treeCandidates, chapterFilter, studyState, latestAttemptByQuestion)
                .stream().map(LearningQuestion::getId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LearningCatalogVO vo = new LearningCatalogVO();
        vo.setCourseCode(scope == null ? null : scope.getCourseCode());
        vo.setTotalQuestions(visible.size());
        vo.setChapters(buildLegacyChapters(visible, mappingsByQuestion, latestAttemptByQuestion));
        vo.setNodes(buildCatalogNodes(visible, mappingsByQuestion, latestAttemptByQuestion));
        vo.setSections(buildCatalogSections(treeScope, treeVisibleIds));
        int attempted = 0, needsReview = 0, correct = 0;
        for (LearningQuestion question : visible) {
            LearningAttempt latest = latestAttemptByQuestion.get(question.getId());
            if (latest == null) continue;
            attempted++;
            if (latest.getIsCorrect() != null && latest.getIsCorrect() == 1) correct++;
            else needsReview++;
        }
        vo.setAttempted(attempted);
        vo.setNeedsReview(needsReview);
        vo.setCorrect(correct);
        vo.setTypeCounts(typeCounts);
        if (cacheKey != null) {
            catalogCache.put(cacheKey, new CatalogCacheItem(vo, System.currentTimeMillis()));
        }
        return vo;
    }

    private LambdaQueryWrapper<LearningQuestion> studentQuestionWrapper() {
        return new LambdaQueryWrapper<LearningQuestion>()
                .eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name())
                .eq(LearningQuestion::getIsDelete, 0)
                .eq(LearningQuestion::getResourceScope, com.yupi.springbootinit.task.TaskConstants.SCOPE_SHARED);
    }

    private Map<Long, LearningAttempt> latestAttempts(Set<Long> questionIds, Long userId) {
        Map<Long, LearningAttempt> latest = new HashMap<>();
        if (CollectionUtils.isEmpty(questionIds)) return latest;
        for (LearningAttempt attempt : learningAttemptMapper.selectList(
                new LambdaQueryWrapper<LearningAttempt>()
                        .eq(LearningAttempt::getUserId, userId)
                        .in(LearningAttempt::getQuestionId, questionIds)
                        .orderByAsc(LearningAttempt::getCreateTime)
                        .orderByAsc(LearningAttempt::getId))) {
            latest.put(attempt.getQuestionId(), attempt);
        }
        return latest;
    }

    /** CHOICE 覆盖单选/多选；其余按原题型精确匹配。 */
    private static java.util.Set<String> questionTypesFor(String type) {
        if (StringUtils.isBlank(type) || "ALL".equalsIgnoreCase(type)) {
            return java.util.Collections.emptySet();
        }
        if ("CHOICE".equalsIgnoreCase(type)) {
            return new java.util.LinkedHashSet<>(java.util.Arrays.asList("SINGLE", "MULTIPLE"));
        }
        return java.util.Collections.singleton(type);
    }

    private static List<LearningQuestion> filterByQuestionType(List<LearningQuestion> questions, String type) {
        java.util.Set<String> types = questionTypesFor(type);
        if (types.isEmpty()) return questions;
        return questions.stream()
                .filter(question -> question.getType() != null && types.contains(question.getType()))
                .collect(Collectors.toList());
    }

    private static void applyTypeFilter(LambdaQueryWrapper<LearningQuestion> wrapper, String type) {
        java.util.Set<String> types = questionTypesFor(type);
        if (types.isEmpty()) return;
        if (types.size() == 1) {
            wrapper.eq(LearningQuestion::getType, types.iterator().next());
        } else {
            wrapper.in(LearningQuestion::getType, types);
        }
    }

    private static Map<String, Integer> catalogTypeCounts(List<LearningQuestion> questions) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int single = 0, multiple = 0, judge = 0, fill = 0, comprehensive = 0;
        for (LearningQuestion question : questions) {
            String type = question.getType();
            if (type == null) continue;
            switch (type) {
                case "SINGLE": single++; break;
                case "MULTIPLE": multiple++; break;
                case "JUDGE": judge++; break;
                case "FILL_BLANK": fill++; break;
                case "COMPREHENSIVE": comprehensive++; break;
                default: break;
            }
        }
        counts.put("ALL", questions.size());
        counts.put("CHOICE", single + multiple);
        counts.put("JUDGE", judge);
        counts.put("FILL_BLANK", fill);
        counts.put("COMPREHENSIVE", comprehensive);
        return counts;
    }

    private List<LearningQuestion> filterCatalogQuestions(List<LearningQuestion> questions,
                                                          String chapterFilter,
                                                          String studyState,
                                                          Map<Long, LearningAttempt> latestAttemptByQuestion) {
        return questions.stream()
                .filter(question -> matchesChapter(question.getChapter(), chapterFilter))
                .filter(question -> StringUtils.isBlank(studyState)
                        || "ALL".equalsIgnoreCase(studyState)
                        || matchesStudyState(
                        latestAttemptByQuestion.containsKey(question.getId())
                                ? Collections.singletonList(latestAttemptByQuestion.get(question.getId()))
                                : null,
                        studyState))
                .collect(Collectors.toList());
    }

    /** 旧章节→知识点聚合：兼容旧入口，保持原排序与“未归类”语义。 */
    private List<LearningCatalogChapterVO> buildLegacyChapters(
            List<LearningQuestion> visible,
            Map<Long, List<LearningQuestionNode>> mappingsByQuestion,
            Map<Long, LearningAttempt> latestAttemptByQuestion) {
        Map<String, List<LearningQuestion>> questionsByChapter = new LinkedHashMap<>();
        for (LearningQuestion question : visible) {
            String key = chapterKey(question.getChapter());
            questionsByChapter.computeIfAbsent(key, ignored -> new ArrayList<>()).add(question);
        }
        List<LearningCatalogChapterVO> chapters = new ArrayList<>(questionsByChapter.size());
        for (Map.Entry<String, List<LearningQuestion>> entry : questionsByChapter.entrySet()) {
            LearningCatalogChapterVO chapterVo = new LearningCatalogChapterVO();
            chapterVo.setChapter(entry.getKey());
            chapterVo.setQuestionCount(entry.getValue().size());
            Map<String, LearningCatalogNodeVO> nodeVos = new LinkedHashMap<>();
            for (LearningQuestion question : entry.getValue()) {
                LearningAttempt latest = latestAttemptByQuestion.get(question.getId());
                boolean attempted = latest != null;
                boolean latestCorrect = attempted && latest.getIsCorrect() != null && latest.getIsCorrect() == 1;
                Set<String> seenNodes = new LinkedHashSet<>();
                for (LearningQuestionNode mapping : mappingsByQuestion.getOrDefault(question.getId(), Collections.emptyList())) {
                    String nodeName = StringUtils.trimToEmpty(mapping.getNodeName());
                    if (nodeName.isEmpty() || !seenNodes.add(nodeName)) continue;
                    LearningCatalogNodeVO nodeVo = nodeVos.computeIfAbsent(nodeName, ignored -> {
                        LearningCatalogNodeVO created = new LearningCatalogNodeVO();
                        created.setNodeName(nodeName);
                        created.setQuestionCount(0);
                        created.setAttempted(0);
                        created.setNeedsReview(0);
                        created.setCorrect(0);
                        return created;
                    });
                    if (StringUtils.isBlank(nodeVo.getNodeCode())) nodeVo.setNodeCode(mapping.getNodeCode());
                    nodeVo.setQuestionCount(nodeVo.getQuestionCount() + 1);
                    if (attempted) {
                        nodeVo.setAttempted(nodeVo.getAttempted() + 1);
                        if (latestCorrect) nodeVo.setCorrect(nodeVo.getCorrect() + 1);
                        else nodeVo.setNeedsReview(nodeVo.getNeedsReview() + 1);
                    }
                }
            }
            List<LearningCatalogNodeVO> nodes = new ArrayList<>(nodeVos.values());
            nodes.sort(Comparator.comparing(LearningCatalogNodeVO::getNodeName, NAME_COLLATOR)
                    .thenComparing(LearningCatalogNodeVO::getNodeName));
            chapterVo.setNodes(nodes);
            chapters.add(chapterVo);
        }
        chapters.sort(Comparator
                .comparing((LearningCatalogChapterVO c) -> UNCATEGORIZED_CHAPTER.equals(c.getChapter()))
                .thenComparing(LearningCatalogChapterVO::getChapter, NAME_COLLATOR)
                .thenComparing(LearningCatalogChapterVO::getChapter));
        return chapters;
    }

    /** 当前范围内节点聚合（稳定 nodeCode 优先；旧行回退 nodeName）。 */
    private List<LearningCatalogNodeVO> buildCatalogNodes(
            List<LearningQuestion> visible,
            Map<Long, List<LearningQuestionNode>> mappingsByQuestion,
            Map<Long, LearningAttempt> latestAttemptByQuestion) {
        Map<String, LearningCatalogNodeVO> nodeVos = new LinkedHashMap<>();
        Set<String> storageNames = new LinkedHashSet<>();
        for (LearningQuestion question : visible) {
            LearningAttempt latest = latestAttemptByQuestion.get(question.getId());
            boolean attempted = latest != null;
            boolean latestCorrect = attempted && latest.getIsCorrect() != null && latest.getIsCorrect() == 1;
            Set<String> seenNodes = new LinkedHashSet<>();
            for (LearningQuestionNode mapping : mappingsByQuestion.getOrDefault(question.getId(), Collections.emptyList())) {
                String nodeName = StringUtils.trimToEmpty(mapping.getNodeName());
                String nodeCode = StringUtils.trimToEmpty(mapping.getNodeCode());
                if (nodeName.isEmpty() && nodeCode.isEmpty()) continue;
                String key = nodeCode.isEmpty() ? "n:" + nodeName : "c:" + nodeCode;
                if (!seenNodes.add(key)) continue;
                LearningCatalogNodeVO nodeVo = nodeVos.computeIfAbsent(key, ignored -> {
                    LearningCatalogNodeVO created = new LearningCatalogNodeVO();
                    created.setNodeCode(nodeCode.isEmpty() ? null : nodeCode);
                    created.setNodeName(nodeName.isEmpty() ? null : nodeName);
                    created.setQuestionCount(0);
                    created.setAttempted(0);
                    created.setNeedsReview(0);
                    created.setCorrect(0);
                    return created;
                });
                if (StringUtils.isBlank(nodeVo.getNodeCode()) && !nodeCode.isEmpty()) nodeVo.setNodeCode(nodeCode);
                if (StringUtils.isBlank(nodeVo.getNodeName()) && !nodeName.isEmpty()) nodeVo.setNodeName(nodeName);
                if (!nodeName.isEmpty()) storageNames.add(nodeName);
                nodeVo.setQuestionCount(nodeVo.getQuestionCount() + 1);
                if (attempted) {
                    nodeVo.setAttempted(nodeVo.getAttempted() + 1);
                    if (latestCorrect) nodeVo.setCorrect(nodeVo.getCorrect() + 1);
                    else nodeVo.setNeedsReview(nodeVo.getNeedsReview() + 1);
                }
            }
        }
        Map<String, String> displayByStorage = displayNames(storageNames);
        for (LearningCatalogNodeVO nodeVo : nodeVos.values()) {
            String storage = nodeVo.getNodeName();
            String display = storage == null ? null : displayByStorage.getOrDefault(storage, storage);
            if (StringUtils.isBlank(display)) display = nodeVo.getNodeCode();
            nodeVo.setDisplayName(display);
        }
        List<LearningCatalogNodeVO> nodes = new ArrayList<>(nodeVos.values());
        nodes.sort(Comparator
                .comparing((LearningCatalogNodeVO node) -> StringUtils.defaultIfBlank(node.getDisplayName(), node.getNodeName()), NAME_COLLATOR)
                .thenComparing(node -> StringUtils.defaultIfBlank(node.getDisplayName(), node.getNodeName()))
                .thenComparing(node -> StringUtils.defaultIfBlank(node.getNodeCode(), "")));
        return nodes;
    }

    private Map<String, String> displayNames(Set<String> storageNames) {
        Map<String, String> result = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(storageNames)) return result;
        List<String> names = new ArrayList<>(storageNames);
        if (nodeIdentities == null) {
            for (String name : names) result.put(name, name);
            return result;
        }
        List<String> displays = nodeIdentities.displayNames(names);
        for (int i = 0; i < names.size(); i++) {
            result.put(names.get(i), displays.size() > i ? displays.get(i) : names.get(i));
        }
        return result;
    }

    /** 教材小节树 + 已发布单元题量；题量按 distinct questionId 汇总到祖先小节。 */
    private List<LearningCatalogSectionVO> buildCatalogSections(
            LearningQuestionScopeService.Scope treeScope,
            Set<Long> treeVisibleIds) {
        if (treeScope == null || CollectionUtils.isEmpty(treeScope.getSections())) {
            return new ArrayList<>();
        }
        Map<String, LearningCatalogSectionVO> sectionVos = new LinkedHashMap<>();
        List<LearningCatalogSectionVO> roots = new ArrayList<>();
        for (Map<String, Object> row : treeScope.getSections()) {
            LearningCatalogSectionVO section = new LearningCatalogSectionVO();
            section.setSectionCode(stringValue(row.get("sectionCode")));
            section.setParentSectionCode(stringValue(row.get("parentSectionCode")));
            section.setTitle(stringValue(row.get("title")));
            section.setTextbookNumber(stringValue(row.get("textbookNumber")));
            section.setSectionType(stringValue(row.get("sectionType")));
            section.setSortOrder(intValue(row.get("sortOrder")));
            section.setQuestionCount(0);
            sectionVos.put(section.getSectionCode(), section);
        }
        Map<String, Set<Long>> unitQuestionIds = new LinkedHashMap<>();
        for (LearningQuestionScopeService.UnitQuestions unit : treeScope.getAllUnits()) {
            unitQuestionIds.put(unit.getUnitCode(), unit.getQuestionIds());
            LearningCatalogSectionVO section = sectionVos.get(unit.getSectionCode());
            if (section == null) continue;
            LearningCatalogUnitVO unitVo = new LearningCatalogUnitVO();
            unitVo.setUnitCode(unit.getUnitCode());
            unitVo.setSectionCode(unit.getSectionCode());
            unitVo.setTitle(unit.getTitle());
            unitVo.setSortOrder(unit.getSortOrder());
            unitVo.setQuestionCount((int) unit.getQuestionIds().stream()
                    .filter(treeVisibleIds::contains).count());
            section.getUnits().add(unitVo);
        }
        for (LearningCatalogSectionVO section : sectionVos.values()) {
            section.getUnits().sort(Comparator
                    .comparing(LearningCatalogUnitVO::getSortOrder,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(LearningCatalogUnitVO::getUnitCode));
            LearningCatalogSectionVO parent = sectionVos.get(section.getParentSectionCode());
            if (parent == null) roots.add(section);
            else parent.getChildren().add(section);
        }
        Comparator<LearningCatalogSectionVO> sectionComparator = Comparator
                .comparing(LearningCatalogSectionVO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LearningCatalogSectionVO::getSectionCode);
        roots.sort(sectionComparator);
        for (LearningCatalogSectionVO section : sectionVos.values()) {
            section.getChildren().sort(sectionComparator);
        }
        for (LearningCatalogSectionVO root : roots) {
            fillSectionQuestionCount(root, unitQuestionIds, treeVisibleIds);
        }
        return roots;
    }

    private Set<Long> fillSectionQuestionCount(LearningCatalogSectionVO section,
                                                Map<String, Set<Long>> unitQuestionIds,
                                                Set<Long> treeVisibleIds) {
        Set<Long> ids = new LinkedHashSet<>();
        for (LearningCatalogUnitVO unit : section.getUnits()) {
            Set<Long> unitIds = unitQuestionIds.get(unit.getUnitCode());
            if (unitIds == null) continue;
            for (Long id : unitIds) if (treeVisibleIds.contains(id)) ids.add(id);
        }
        for (LearningCatalogSectionVO child : section.getChildren()) {
            ids.addAll(fillSectionQuestionCount(child, unitQuestionIds, treeVisibleIds));
        }
        section.setQuestionCount(ids.size());
        return ids;
    }

    private static String resultLabel(Integer isCorrect) {
        if (isCorrect == null) return null;
        return isCorrect == 1 ? "CORRECT" : "WRONG";
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intValue(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    // endregion

    // region 作答

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningAnswerResultVO answerQuestion(LearningAnswerRequest request, User user) {
        ThrowUtils.throwIf(request == null || request.getQuestionId() == null, ErrorCode.PARAMS_ERROR);
        Object rawAnswer = request.getAnswer();
        if (rawAnswer instanceof String && ((String) rawAnswer).length() > 20000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "答案过长（上限 20000 字）");
        }
        LearningQuestion question = this.getById(request.getQuestionId());
        ThrowUtils.throwIf(question == null || question.getIsDelete() == 1, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        boolean taskScoped = com.yupi.springbootinit.task.TaskConstants.SCOPE_TASK.equals(question.getResourceScope());
        com.yupi.springbootinit.task.TaskExecutionContextService.Context context = null;
        Long sourceOriginalId = null;
        String evaluationIdentity;
        if (taskScoped) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(request.getSourceTaskCode(), request.getSourceTaskItemCode())
                            || request.getSourceTaskRound() == null,
                    ErrorCode.NO_AUTH_ERROR, "任务题目请从学习任务入口作答");
            context = taskContext.validate(user, request.getSourceTaskCode(), request.getSourceTaskItemCode(),
                    question.getId(), request.getSourceTaskRound(), false);
            evaluationIdentity = context.evaluationIdentity;
            sourceOriginalId = context.sourceQuestionId;
            if (StringUtils.isBlank(evaluationIdentity)) {
                evaluationIdentity = com.yupi.springbootinit.task.EvaluationIdentity.forLearningQuestion(
                        question, nodeCodesFor(question.getId()));
            }
        } else {
            ThrowUtils.throwIf(StringUtils.isNotBlank(request.getSourceTaskCode()),
                    ErrorCode.PARAMS_ERROR, "共享题目不能携带任务上下文");
            evaluationIdentity = com.yupi.springbootinit.task.EvaluationIdentity.forLearningQuestion(
                    question, nodeCodesFor(question.getId()));
            sourceOriginalId = question.getId();
        }
        ThrowUtils.throwIf(!LearningReviewStatusEnum.isStudentVisible(question.getReviewStatus()),
                ErrorCode.OPERATION_ERROR, "题目未发布，不可作答");
        if (LearningQuestionTypeEnum.COMPREHENSIVE.name().equals(question.getType())) {
            ThrowUtils.throwIf(taskScoped || request.getAssessmentRunId() != null
                            || StringUtils.isNotBlank(request.getSourceUnitCode())
                            || StringUtils.isNotBlank(request.getSourceActivityCode()),
                    ErrorCode.PARAMS_ERROR, "综合应用题请从练习入口查看解析，暂不参与自动测评");
            ThrowUtils.throwIf(!(request.getAnswer() instanceof String)
                            || StringUtils.isBlank((String) request.getAnswer()),
                    ErrorCode.PARAMS_ERROR, "请先填写答案");
            // 综合应用题不自动判分，但作答要落库，否则学生写完就丢了
            LearningAttempt comprehensiveAttempt = new LearningAttempt();
            comprehensiveAttempt.setQuestionId(question.getId());
            comprehensiveAttempt.setUserId(user.getId());
            comprehensiveAttempt.setSubmittedAnswer(submittedToJson(request.getAnswer()));
            comprehensiveAttempt.setIsCorrect(null);
            comprehensiveAttempt.setIsFirst(0);
            comprehensiveAttempt.setHasViewedAnalysis(1);
            comprehensiveAttempt.setSubmissionKey(request.getSubmissionKey());
            comprehensiveAttempt.setEvaluationIdentity(evaluationIdentity);
            comprehensiveAttempt.setQuestionRevision(question.getRevision());
            comprehensiveAttempt.setQuestionSnapshot(JSONUtil.toJsonStr(question));
            learningAttemptMapper.insert(comprehensiveAttempt);
            invalidateCatalogCache(user.getId());

            LearningAnswerResultVO result = new LearningAnswerResultVO();
            result.setAttemptId(comprehensiveAttempt.getId());
            result.setCorrect(null);
            result.setAnalysis(question.getAnalysis());
            result.setMisconception(question.getMisconception());
            result.setMasteryChanges(Collections.emptyList());
            return result;
        }
        if (activityContext != null) activityContext.validate(request.getSourceUnitCode(),
                request.getSourceActivityCode(), question.getId(), false);
        if (request.getAssessmentRunId() != null) {
            LearningAssessmentRun run = learningAssessmentRunMapper.selectById(request.getAssessmentRunId());
            ThrowUtils.throwIf(run == null, ErrorCode.NOT_FOUND_ERROR, "测评会话不存在");
            ThrowUtils.throwIf(!Objects.equals(run.getUserId(), user.getId()), ErrorCode.NO_AUTH_ERROR,
                    "无权访问该测评会话");
            ThrowUtils.throwIf(!"ACTIVE".equals(run.getStatus()), ErrorCode.OPERATION_ERROR, "本轮已结束");
            ThrowUtils.throwIf(!JSONUtil.toList(run.getQuestionIdsJson(), Long.class).contains(question.getId()),
                    ErrorCode.PARAMS_ERROR, "题目不在本轮测评中");
        }
        // 提交幂等（Phase 4 §8.3）：同 userId+submissionKey 的重发返回既有作答结果，
        // 不再创建 attempt/事件/更新模型；主动重试由前端使用新键。
        if (StringUtils.isNotBlank(request.getSubmissionKey())) {
            LearningAttempt existing = learningAttemptMapper.selectOne(
                    new LambdaQueryWrapper<LearningAttempt>()
                            .eq(LearningAttempt::getUserId, user.getId())
                            .eq(LearningAttempt::getSubmissionKey, request.getSubmissionKey())
                            .last("limit 1"));
            if (existing != null) {
                ThrowUtils.throwIf(!Objects.equals(existing.getQuestionId(), question.getId())
                                || !Objects.equals(existing.getAssessmentRunId(), request.getAssessmentRunId())
                                || !Objects.equals(existing.getSourceUnitCode(), request.getSourceUnitCode())
                                || !Objects.equals(existing.getSourceActivityCode(), request.getSourceActivityCode())
                                || !Objects.equals(existing.getSourceTaskCode(), request.getSourceTaskCode())
                                || !Objects.equals(existing.getSourceTaskItemCode(), request.getSourceTaskItemCode())
                                || !Objects.equals(existing.getSourceTaskRound(), request.getSourceTaskRound()),
                        ErrorCode.PARAMS_ERROR, "提交标识与本次作答不匹配");
                return rebuildResultFromAttempt(existing, question);
            }
        }
        boolean correct = LearningQuestionGrader.grade(question.getType(), question.getAnswer(), request.getAnswer());

        Long identityAttempts = learningAttemptMapper.selectCount(new LambdaQueryWrapper<LearningAttempt>()
                .eq(LearningAttempt::getUserId, user.getId())
                .eq(LearningAttempt::getEvaluationIdentity, evaluationIdentity));
        Long legacyAttempts = 0L;
        if (sourceOriginalId != null) {
            legacyAttempts = learningAttemptMapper.selectCount(new LambdaQueryWrapper<LearningAttempt>()
                    .eq(LearningAttempt::getUserId, user.getId())
                    .eq(LearningAttempt::getQuestionId, sourceOriginalId)
                    .isNull(LearningAttempt::getEvaluationIdentity));
        }
        boolean isFirst = (identityAttempts == null || identityAttempts == 0)
                && (legacyAttempts == null || legacyAttempts == 0);

        LearningAttempt attempt = new LearningAttempt();
        attempt.setQuestionId(question.getId());
        attempt.setUserId(user.getId());
        attempt.setSubmittedAnswer(submittedToJson(request.getAnswer()));
        attempt.setIsCorrect(correct ? 1 : 0);
        attempt.setIsFirst(isFirst ? 1 : 0);
        attempt.setHasViewedAnalysis(1);
        attempt.setAssessmentRunId(request.getAssessmentRunId());
        attempt.setSubmissionKey(request.getSubmissionKey());
        attempt.setSourceUnitCode(request.getSourceUnitCode());
        attempt.setSourceActivityCode(request.getSourceActivityCode());
        attempt.setSourceTaskCode(request.getSourceTaskCode());
        attempt.setSourceTaskItemCode(request.getSourceTaskItemCode());
        attempt.setSourceTaskRound(request.getSourceTaskRound());
        attempt.setEvaluationIdentity(evaluationIdentity);
        attempt.setQuestionRevision(question.getRevision());
        attempt.setQuestionSnapshot(JSONUtil.toJsonStr(question));
        learningAttemptMapper.insert(attempt);

        List<MasteryChangeVO> changes = learningEventService.recordAttemptEvents(
                user.getId(), question, attempt.getId(), correct, isFirst);
        invalidateCatalogCache(user.getId());

        LearningAnswerResultVO result = new LearningAnswerResultVO();
        result.setAttemptId(attempt.getId());
        result.setCorrect(correct);
        LearningQuestion snapshot = StringUtils.isBlank(attempt.getQuestionSnapshot()) ? question
                : JSONUtil.toBean(attempt.getQuestionSnapshot(), LearningQuestion.class);
        result.setAnalysis(snapshot.getAnalysis());
        result.setMisconception(snapshot.getMisconception());
        result.setIsFirst(isFirst);
        result.setMasteryChanges(changes);
        return result;
    }

    /** 幂等重发：由既有 attempt 重建作答反馈（不产生新事件、不更新模型）。 */
    private LearningAnswerResultVO rebuildResultFromAttempt(LearningAttempt attempt,
                                                            LearningQuestion question) {
        LearningAnswerResultVO result = new LearningAnswerResultVO();
        result.setAttemptId(attempt.getId());
        Integer storedCorrect = attempt.getIsCorrect();
        result.setCorrect(storedCorrect == null ? null : storedCorrect == 1);
        LearningQuestion snapshot = StringUtils.isBlank(attempt.getQuestionSnapshot()) ? question
                : JSONUtil.toBean(attempt.getQuestionSnapshot(), LearningQuestion.class);
        result.setAnalysis(snapshot.getAnalysis());
        result.setMisconception(snapshot.getMisconception());
        result.setIsFirst(attempt.getIsFirst() == 1);
        result.setMasteryChanges(Collections.emptyList());
        return result;
    }

    // endregion

    // region 审核状态机

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewQuestion(LearningQuestionReviewRequest request, User operator) {
        invalidateCatalogCache(null);
        ThrowUtils.throwIf(request == null || request.getQuestionId() == null, ErrorCode.PARAMS_ERROR);
        ReviewActionEnum action = parseAction(request.getAction());
        LearningQuestion question = this.getById(request.getQuestionId());
        ThrowUtils.throwIf(question == null || question.getIsDelete() == 1, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        ThrowUtils.throwIf(com.yupi.springbootinit.task.TaskConstants.SCOPE_TASK.equals(question.getResourceScope()),
                ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        boolean admin = userService.isAdmin(operator);
        boolean owner = question.getUserId().equals(operator.getId());
        if (teachingAssessments != null) {
            // 已有修订账本的题目，活动行状态必须与最新修订保持一致
            if (teachingAssessments.draft(question.getId()) != null) {
                if (action == ReviewActionEnum.SUBMIT) {
                    ThrowUtils.throwIf(!owner, ErrorCode.NO_AUTH_ERROR, "仅作者可提交审核");
                } else {
                    requireAdmin(admin);
                }
                ThrowUtils.throwIf(!teachingAssessments.review(question.getId(), action.name(),
                                request.getReason(), operator.getId()),
                        ErrorCode.OPERATION_ERROR, "当前状态（" + action.name() + "）不可执行");
                return true;
            }
            if (admin && teachingAssessments.review(request.getQuestionId(), request.getAction(),
                    request.getReason(), operator.getId())) return true;
        }

        String from = question.getReviewStatus();
        String to;
        switch (action) {
            case SUBMIT:
                ThrowUtils.throwIf(!owner, ErrorCode.NO_AUTH_ERROR, "仅作者可提交审核");
                ThrowUtils.throwIf(!fromDrafOrOfflineOrRejected(from), ErrorCode.OPERATION_ERROR,
                        "当前状态（" + from + "）不能提交审核");
                to = LearningReviewStatusEnum.SUBMITTED.name();
                break;
            case APPROVE:
                requireAdmin(admin);
                ThrowUtils.throwIf(!LearningReviewStatusEnum.SUBMITTED.name().equals(from),
                        ErrorCode.OPERATION_ERROR, "当前状态（" + from + "）不能审核通过");
                to = LearningReviewStatusEnum.APPROVED.name();
                break;
            case REJECT:
                requireAdmin(admin);
                ThrowUtils.throwIf(!LearningReviewStatusEnum.SUBMITTED.name().equals(from),
                        ErrorCode.OPERATION_ERROR, "当前状态（" + from + "）不能驳回");
                ThrowUtils.throwIf(StringUtils.isBlank(request.getReason()), ErrorCode.PARAMS_ERROR, "驳回必须填写理由");
                to = LearningReviewStatusEnum.REJECTED.name();
                break;
            case PUBLISH:
                requireAdmin(admin);
                ThrowUtils.throwIf(!LearningReviewStatusEnum.APPROVED.name().equals(from),
                        ErrorCode.OPERATION_ERROR, "仅审核通过的题目可发布");
                to = LearningReviewStatusEnum.PUBLISHED.name();
                break;
            case OFFLINE:
                requireAdmin(admin);
                ThrowUtils.throwIf(!LearningReviewStatusEnum.PUBLISHED.name().equals(from),
                        ErrorCode.OPERATION_ERROR, "仅已发布的题目可下线");
                to = LearningReviewStatusEnum.OFFLINE.name();
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的动作");
        }
        LearningQuestion update = new LearningQuestion();
        update.setId(question.getId());
        update.setReviewStatus(to);
        boolean updated = this.updateById(update);
        ThrowUtils.throwIf(!updated, ErrorCode.SYSTEM_ERROR, "状态更新失败");
        saveReviewRecord(question.getId(), operator.getId(), action, request.getReason());
        return updated;
    }

    private void requireAdmin(boolean admin) {
        ThrowUtils.throwIf(!admin, ErrorCode.NO_AUTH_ERROR, "仅管理员可执行该审核操作");
    }

    private boolean fromDrafOrOfflineOrRejected(String status) {
        return LearningReviewStatusEnum.DRAFT.name().equals(status)
                || LearningReviewStatusEnum.OFFLINE.name().equals(status)
                || LearningReviewStatusEnum.REJECTED.name().equals(status);
    }

    private ReviewActionEnum parseAction(String action) {
        ThrowUtils.throwIf(!ReviewActionEnum.isValid(action), ErrorCode.PARAMS_ERROR, "非法审核动作");
        return ReviewActionEnum.valueOf(action);
    }

    private void saveReviewRecord(Long questionId, Long operatorId, ReviewActionEnum action, String reason) {
        LearningReview review = new LearningReview();
        review.setQuestionId(questionId);
        review.setReviewerId(operatorId);
        review.setAction(action.name());
        review.setReason(reason);
        learningReviewMapper.insert(review);
    }

    // endregion

    // region JSON 导入（Phase 8 §12.2：预览校验 → 确认导入草稿）

    /** 单次导入条目上限 */
    private static final int MAX_IMPORT_ITEMS = 100;

    @Override
    public ImportPreviewVO importPreview(String content) {
        List<LearningQuestionAddRequest> items = parseImportItems(content);
        ImportPreviewVO vo = new ImportPreviewVO();
        vo.setTotal(items.size());
        List<ImportPreviewVO.ImportItemVO> itemVos = new ArrayList<>();
        int valid = 0;
        for (int i = 0; i < items.size(); i++) {
            ImportPreviewVO.ImportItemVO itemVo = new ImportPreviewVO.ImportItemVO();
            itemVo.setIndex(i + 1);
            LearningQuestionAddRequest item = items.get(i);
            List<String> errors = collectRequestErrors(item);
            itemVo.setValid(errors.isEmpty());
            itemVo.setError(errors.isEmpty() ? null : String.join("；", errors));
            if (errors.isEmpty()) {
                valid++;
            }
            if (item != null) {
                itemVo.setTitle(item.getTitle());
                itemVo.setType(item.getType());
                itemVo.setChapter(item.getChapter());
                itemVo.setNodeNames(item.getNodeNames());
            }
            itemVos.add(itemVo);
        }
        vo.setValidCount(valid);
        vo.setInvalidCount(items.size() - valid);
        vo.setItems(itemVos);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importCommit(String content, User author) {
        List<LearningQuestionAddRequest> items = parseImportItems(content);
        ImportResultVO vo = new ImportResultVO();
        List<Long> createdIds = new ArrayList<>();
        int skipped = 0;
        for (LearningQuestionAddRequest item : items) {
            if (!collectRequestErrors(item).isEmpty()) {
                skipped++;
                continue;
            }
            // 复用创建流程：DRAFT + 节点映射
            createdIds.add(addQuestion(item, author));
        }
        vo.setCreatedCount(createdIds.size());
        vo.setSkippedCount(skipped);
        vo.setQuestionIds(createdIds);
        return vo;
    }

    /**
     * 解析导入 JSON 数组为请求列表（结构同 LearningQuestionAddRequest）
     */
    private List<LearningQuestionAddRequest> parseImportItems(String content) {
        ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "导入内容不能为空");
        List<LearningQuestionAddRequest> items;
        try {
            items = JSONUtil.toList(content, LearningQuestionAddRequest.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "导入内容不是合法的 JSON 数组");
        }
        ThrowUtils.throwIf(CollectionUtils.isEmpty(items), ErrorCode.PARAMS_ERROR, "导入内容为空数组");
        ThrowUtils.throwIf(items.size() > MAX_IMPORT_ITEMS, ErrorCode.PARAMS_ERROR,
                "单次最多导入 " + MAX_IMPORT_ITEMS + " 条");
        return items;
    }

    // endregion

    // region 私有工具

    private void validateRequest(LearningQuestionAddRequest request) {
        List<String> errors = collectRequestErrors(request);
        ThrowUtils.throwIf(!errors.isEmpty(), ErrorCode.PARAMS_ERROR, String.join("；", errors));
    }

    /**
     * 收集请求校验错误（不抛出，供导入预览逐项展示）
     */
    private List<String> collectRequestErrors(LearningQuestionAddRequest request) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("题目内容为空");
            return errors;
        }
        if (StringUtils.isAnyBlank(request.getTitle(), request.getContent())) {
            errors.add("标题和题干不能为空");
        }
        if (!LearningQuestionTypeEnum.isValid(request.getType())) {
            errors.add("非法题型");
        }
        if (request.getAnswer() == null) {
            errors.add("标准答案不能为空");
        }
        String type = request.getType();
        if (LearningQuestionTypeEnum.SINGLE.name().equals(type)
                || LearningQuestionTypeEnum.MULTIPLE.name().equals(type)) {
            List<LearningQuestionAddRequest.QuestionOption> options = request.getOptions();
            if (CollectionUtils.isEmpty(options) || options.size() < 2) {
                errors.add("选择题至少两个选项");
            } else {
                Set<String> keys = new HashSet<>();
                for (LearningQuestionAddRequest.QuestionOption option : options) {
                    if (StringUtils.isAnyBlank(option.getKey(), option.getValue())) {
                        errors.add("选项 key/value 不能为空");
                    }
                    if (!keys.add(option.getKey())) {
                        errors.add("选项 key 重复：" + option.getKey());
                    }
                }
                // 答案 key 必须存在于选项
                List<String> answerKeys = extractAnswerKeys(request);
                if (CollectionUtils.isEmpty(answerKeys)) {
                    errors.add("答案不能为空");
                } else {
                    for (String key : answerKeys) {
                        if (!keys.contains(key)) {
                            errors.add("答案 key 不在选项中：" + key);
                        }
                    }
                }
                if (LearningQuestionTypeEnum.SINGLE.name().equals(type)
                        && CollectionUtils.isNotEmpty(answerKeys) && answerKeys.size() > 1) {
                    errors.add("单选题答案只能有一个");
                }
            }
        }
        if (request.getDifficulty() != null
                && (request.getDifficulty() < 1 || request.getDifficulty() > 5)) {
            errors.add("难度取值 1-5");
        }
        // 节点必须为图谱真实节点
        if (CollectionUtils.isNotEmpty(request.getNodeNames())) {
            for (String nodeName : request.getNodeNames()) {
                if (StringUtils.isBlank(nodeName)) {
                    errors.add("节点名不能为空");
                } else if (!learningEventService.isValidNode(nodeName)) {
                    errors.add("知识节点不存在：" + nodeName);
                }
            }
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractAnswerKeys(LearningQuestionAddRequest request) {
        Object answer = request.getAnswer();
        List<String> keys = new ArrayList<>();
        if (answer instanceof List) {
            for (Object o : (List<Object>) answer) {
                keys.add(String.valueOf(o));
            }
        } else if (answer instanceof String) {
            keys.add((String) answer);
        } else {
            keys.add(String.valueOf(answer));
        }
        return keys;
    }

    private List<String> nodeCodesFor(Long questionId) {
        List<String> codes = new ArrayList<>();
        for (LearningQuestionNode mapping : learningQuestionNodeMapper.selectList(
                new LambdaQueryWrapper<LearningQuestionNode>()
                        .eq(LearningQuestionNode::getQuestionId, questionId))) {
            codes.add(mapping.getNodeCode() == null ? mapping.getNodeName() : mapping.getNodeCode());
        }
        return codes;
    }

    private void saveNodeMappings(Long questionId, List<String> nodeNames) {
        if (CollectionUtils.isEmpty(nodeNames)) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String nodeName : nodeNames) {
            if (!seen.add(nodeName)) {
                continue;
            }
            LearningQuestionNode mapping = new LearningQuestionNode();
            mapping.setQuestionId(questionId);
            com.yupi.springbootinit.learning.NodeIdentityService.Identity identity = nodeIdentities == null ? null : nodeIdentities.resolve(nodeName);
            mapping.setNodeName(identity == null ? nodeName : identity.storageKey);
            mapping.setNodeCode(identity == null ? null : identity.nodeCode);
            mapping.setRole("PRIMARY");
            learningQuestionNodeMapper.insert(mapping);
        }
    }

    private String serializeOptions(List<LearningQuestionAddRequest.QuestionOption> options) {
        if (CollectionUtils.isEmpty(options)) {
            return null;
        }
        return JSONUtil.toJsonStr(options);
    }

    /**
     * 答案序列化为 JSON 字面量。
     * hutool 的 toJsonStr 对纯字符串不会加引号（输出裸 A），而 learning_question.answer
     * 是 MySQL JSON 列，必须收到合法 JSON 字面量（"A" / true / ["A","C"]）。
     */
    private String answerToJson(Object answer) {
        if (answer == null) {
            return "null";
        }
        if (answer instanceof String) {
            String s = (String) answer;
            String t = s.trim();
            if (t.startsWith("[") || t.startsWith("{") || t.startsWith("\"")
                    || "true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t)) {
                return s;
            }
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return JSONUtil.toJsonStr(answer);
    }

    private String submittedToJson(Object submitted) {
        if (submitted instanceof String) {
            return JSONUtil.toJsonStr(submitted);
        }
        return JSONUtil.toJsonStr(submitted == null ? "" : submitted);
    }

    private void checkOwnerOrAdmin(LearningQuestion question, User user) {
        boolean owner = question.getUserId().equals(user.getId());
        ThrowUtils.throwIf(!owner && !userService.isAdmin(user), ErrorCode.NO_AUTH_ERROR, "无权限操作该题目");
    }

    /**
     * 实体转 VO。契约：永不下发 answer；analysis/misconception 仅在已作答或管理视角下发。
     */
    private LearningQuestionVO toVO(LearningQuestion question, User user, boolean managerView) {
        return toVO(question, user, managerView, null);
    }

    private LearningQuestionVO toVO(LearningQuestion question, User user, boolean managerView, Boolean hasAttempted) {
        LearningQuestionVO vo = new LearningQuestionVO();
        BeanUtils.copyProperties(question, vo);
        // copyProperties 会带出同名敏感字段，先清空再按权限回填（防学生视角泄漏解析/答案）
        vo.setAnalysis(null);
        vo.setMisconception(null);
        List<String> nodeNames = learningQuestionNodeMapper.selectList(
                new LambdaQueryWrapper<LearningQuestionNode>()
                        .eq(LearningQuestionNode::getQuestionId, question.getId()))
                .stream().map(LearningQuestionNode::getNodeName).collect(Collectors.toList());
        vo.setNodeNames(nodeNames);
        if(nodeIdentities!=null)vo.setNodeDisplayNames(nodeIdentities.displayNames(nodeNames));
        boolean attempted = hasAttempted != null ? hasAttempted : learningAttemptMapper.selectCount(new LambdaQueryWrapper<LearningAttempt>()
                .eq(LearningAttempt::getUserId, user.getId())
                .eq(LearningAttempt::getQuestionId, question.getId())) > 0;
        vo.setHasAttempted(attempted);
        if (attempted || managerView) {
            vo.setAnalysis(question.getAnalysis());
            vo.setMisconception(question.getMisconception());
        }
        return vo;
    }

    // endregion
}
