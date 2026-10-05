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
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.mapper.LearningReviewMapper;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
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
        saveNodeMappings(question.getId(), request.getNodeNames());
        return question.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateQuestion(LearningQuestionUpdateRequest request, User user) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        LearningQuestion old = this.getById(request.getId());
        ThrowUtils.throwIf(old == null || old.getIsDelete() == 1, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        checkOwnerOrAdmin(old, user);
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
            List<LearningAttempt> attempts = attemptsByQuestion.getOrDefault(question.getId(), Collections.emptyList());
            vo.setHasAttempted(!attempts.isEmpty());
            vo.setAttemptCount(attempts.size());
            if (!attempts.isEmpty()) {
                vo.setFirstResult(attempts.get(0).getIsCorrect() == 1 ? "CORRECT" : "WRONG");
                vo.setLatestResult(attempts.get(attempts.size() - 1).getIsCorrect() == 1
                        ? "CORRECT" : "WRONG");
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
        if (StringUtils.isNotBlank(request.getType())) {
            wrapper.eq(LearningQuestion::getType, request.getType());
        }
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
        if (StringUtils.isNotBlank(request.getType())) {
            wrapper.eq(LearningQuestion::getType, request.getType());
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
                return Collections.emptyList();
            }
            wrapper.in(LearningQuestion::getId, ids);
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
                vo.setLatestResult(list.get(count - 1).getIsCorrect() == 1 ? "CORRECT" : "WRONG");
                vo.setFirstResult(list.get(0).getIsCorrect() == 1 ? "CORRECT" : "WRONG");
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
        return vo;
    }

    @Override
    public LearningQuestionManageVO getManageQuestionVO(long questionId, User user) {
        LearningQuestion question = this.getById(questionId);
        ThrowUtils.throwIf(question == null || question.getIsDelete() == 1,
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
        if (Boolean.TRUE.equals(request.getMyOnly())) {
            wrapper.eq(LearningQuestion::getUserId, user.getId());
        }
        if (StringUtils.isNotBlank(request.getReviewStatus())) {
            wrapper.eq(LearningQuestion::getReviewStatus, request.getReviewStatus());
        }
        if (StringUtils.isNotBlank(request.getTitle())) {
            wrapper.like(LearningQuestion::getTitle, request.getTitle());
        }
        if (StringUtils.isNotBlank(request.getType())) {
            wrapper.eq(LearningQuestion::getType, request.getType());
        }
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
        String chapterFilter = StringUtils.trimToNull(chapter);
        // 1) 题目：仅 PUBLISHED 且未逻辑删除（与 list/page 学生口径一致），按 questionId 去重
        List<LearningQuestion> questions = this.list(new LambdaQueryWrapper<LearningQuestion>()
                .eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name())
                .eq(LearningQuestion::getIsDelete, 0));
        Map<Long, LearningQuestion> visibleQuestions = new LinkedHashMap<>();
        for (LearningQuestion question : questions) {
            if (question.getId() == null || visibleQuestions.containsKey(question.getId())) {
                continue;
            }
            if (matchesChapter(question.getChapter(), chapterFilter)) {
                visibleQuestions.put(question.getId(), question);
            }
        }
        LearningCatalogVO vo = new LearningCatalogVO();
        vo.setTotalQuestions(visibleQuestions.size());
        vo.setChapters(new ArrayList<>());
        if (visibleQuestions.isEmpty()) {
            return vo;
        }

        // 2) 节点映射：一次批量查询，按 questionId 归组（同题同节点的重复映射行只计一次）
        List<Long> visibleIds = new ArrayList<>(visibleQuestions.keySet());
        Map<Long, Set<String>> nodeNamesByQuestion = new HashMap<>();
        for (LearningQuestionNode mapping : learningQuestionNodeMapper.selectList(
                new LambdaQueryWrapper<LearningQuestionNode>()
                        .in(LearningQuestionNode::getQuestionId, visibleIds))) {
            if (mapping.getQuestionId() == null || StringUtils.isBlank(mapping.getNodeName())) {
                continue;
            }
            nodeNamesByQuestion.computeIfAbsent(mapping.getQuestionId(), ignored -> new LinkedHashSet<>())
                    .add(mapping.getNodeName());
        }

        // 3) 本人作答：一次批量查询；排序与 studyState 过滤一致（createTime asc, id asc），末位即"最新有效作答"
        Map<Long, LearningAttempt> latestAttemptByQuestion = new HashMap<>();
        for (LearningAttempt attempt : learningAttemptMapper.selectList(
                new LambdaQueryWrapper<LearningAttempt>()
                        .eq(LearningAttempt::getUserId, user.getId())
                        .in(LearningAttempt::getQuestionId, visibleIds)
                        .orderByAsc(LearningAttempt::getCreateTime)
                        .orderByAsc(LearningAttempt::getId))) {
            latestAttemptByQuestion.put(attempt.getQuestionId(), attempt);
        }

        // 4) 章节分组：按共享谓词归一化分组（null/空白/字面值"未归类"同桶），
        // 随后排序（"未归类"恒排最后，其余中文序）
        Map<String, List<LearningQuestion>> questionsByChapter = new LinkedHashMap<>();
        for (LearningQuestion question : visibleQuestions.values()) {
            String key = chapterKey(question.getChapter());
            questionsByChapter.computeIfAbsent(key, ignored -> new ArrayList<>()).add(question);
        }
        List<LearningCatalogChapterVO> chapters = new ArrayList<>(questionsByChapter.size());
        for (Map.Entry<String, List<LearningQuestion>> entry : questionsByChapter.entrySet()) {
            LearningCatalogChapterVO chapterVo = new LearningCatalogChapterVO();
            chapterVo.setChapter(entry.getKey());
            chapterVo.setQuestionCount(entry.getValue().size());
            // 节点局部口径计数：一题映射多节点时每个节点各计一次；未作答数由前端用 questionCount - attempted 计算
            Map<String, LearningCatalogNodeVO> nodeVos = new LinkedHashMap<>();
            for (LearningQuestion question : entry.getValue()) {
                LearningAttempt latest = latestAttemptByQuestion.get(question.getId());
                boolean attempted = latest != null;
                boolean latestCorrect = attempted && latest.getIsCorrect() != null && latest.getIsCorrect() == 1;
                for (String nodeName : nodeNamesByQuestion.getOrDefault(question.getId(), Collections.emptySet())) {
                    LearningCatalogNodeVO nodeVo = nodeVos.computeIfAbsent(nodeName, ignored -> {
                        LearningCatalogNodeVO created = new LearningCatalogNodeVO();
                        created.setNodeName(nodeName);
                        created.setQuestionCount(0);
                        created.setAttempted(0);
                        created.setNeedsReview(0);
                        created.setCorrect(0);
                        return created;
                    });
                    nodeVo.setQuestionCount(nodeVo.getQuestionCount() + 1);
                    if (attempted) {
                        nodeVo.setAttempted(nodeVo.getAttempted() + 1);
                        if (latestCorrect) {
                            nodeVo.setCorrect(nodeVo.getCorrect() + 1);
                        } else {
                            nodeVo.setNeedsReview(nodeVo.getNeedsReview() + 1);
                        }
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
        vo.setChapters(chapters);
        return vo;
    }

    // endregion

    // region 作答

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningAnswerResultVO answerQuestion(LearningAnswerRequest request, User user) {
        ThrowUtils.throwIf(request == null || request.getQuestionId() == null, ErrorCode.PARAMS_ERROR);
        LearningQuestion question = this.getById(request.getQuestionId());
        ThrowUtils.throwIf(question == null || question.getIsDelete() == 1, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        ThrowUtils.throwIf(!LearningReviewStatusEnum.isStudentVisible(question.getReviewStatus()),
                ErrorCode.OPERATION_ERROR, "题目未发布，不可作答");
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
                                || !Objects.equals(existing.getAssessmentRunId(), request.getAssessmentRunId()),
                        ErrorCode.PARAMS_ERROR, "提交标识与本次作答不匹配");
                return rebuildResultFromAttempt(existing, question);
            }
        }
        boolean correct = LearningQuestionGrader.grade(question.getType(), question.getAnswer(), request.getAnswer());

        boolean isFirst = learningAttemptMapper.selectCount(new LambdaQueryWrapper<LearningAttempt>()
                .eq(LearningAttempt::getUserId, user.getId())
                .eq(LearningAttempt::getQuestionId, question.getId())) == 0;

        LearningAttempt attempt = new LearningAttempt();
        attempt.setQuestionId(question.getId());
        attempt.setUserId(user.getId());
        attempt.setSubmittedAnswer(submittedToJson(request.getAnswer()));
        attempt.setIsCorrect(correct ? 1 : 0);
        attempt.setIsFirst(isFirst ? 1 : 0);
        attempt.setHasViewedAnalysis(1);
        attempt.setAssessmentRunId(request.getAssessmentRunId());
        attempt.setSubmissionKey(request.getSubmissionKey());
        learningAttemptMapper.insert(attempt);

        List<MasteryChangeVO> changes = learningEventService.recordAttemptEvents(
                user.getId(), question, attempt.getId(), correct, isFirst);

        LearningAnswerResultVO result = new LearningAnswerResultVO();
        result.setAttemptId(attempt.getId());
        result.setCorrect(correct);
        result.setAnalysis(question.getAnalysis());
        result.setMisconception(question.getMisconception());
        result.setIsFirst(isFirst);
        result.setMasteryChanges(changes);
        return result;
    }

    /** 幂等重发：由既有 attempt 重建作答反馈（不产生新事件、不更新模型）。 */
    private LearningAnswerResultVO rebuildResultFromAttempt(LearningAttempt attempt,
                                                            LearningQuestion question) {
        LearningAnswerResultVO result = new LearningAnswerResultVO();
        result.setAttemptId(attempt.getId());
        result.setCorrect(attempt.getIsCorrect() == 1);
        result.setAnalysis(question.getAnalysis());
        result.setMisconception(question.getMisconception());
        result.setIsFirst(attempt.getIsFirst() == 1);
        result.setMasteryChanges(Collections.emptyList());
        return result;
    }

    // endregion

    // region 审核状态机

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewQuestion(LearningQuestionReviewRequest request, User operator) {
        ThrowUtils.throwIf(request == null || request.getQuestionId() == null, ErrorCode.PARAMS_ERROR);
        ReviewActionEnum action = parseAction(request.getAction());
        LearningQuestion question = this.getById(request.getQuestionId());
        ThrowUtils.throwIf(question == null || question.getIsDelete() == 1, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        boolean admin = userService.isAdmin(operator);
        boolean owner = question.getUserId().equals(operator.getId());

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
            mapping.setNodeName(nodeName);
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
