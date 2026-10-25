package com.yupi.springbootinit.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.LearningTaskMapper;
import com.yupi.springbootinit.mapper.LearningTaskRecipientMapper;
import com.yupi.springbootinit.mapper.LearningTaskSubmissionMapper;
import com.yupi.springbootinit.model.entity.LearningTask;
import com.yupi.springbootinit.model.entity.LearningTaskRecipient;
import com.yupi.springbootinit.model.entity.LearningTaskSubmission;
import com.yupi.springbootinit.model.entity.User;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/** 任务创建者/接收人的统一服务端校验。 */
@Service
public class TaskAccessService {

    @Resource
    private LearningTaskMapper taskMapper;
    @Resource
    private LearningTaskRecipientMapper recipientMapper;
    @Resource
    private LearningTaskSubmissionMapper submissionMapper;

    public LearningTask task(String taskCode) {
        ThrowUtils.throwIf(taskCode == null || taskCode.trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        LearningTask task = taskMapper.selectOne(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getTaskCode, taskCode.trim())
                .eq(LearningTask::getIsDelete, 0)
                .last("limit 1"));
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        return task;
    }

    public LearningTask requireCreator(String taskCode, User user) {
        LearningTask task = task(taskCode);
        ThrowUtils.throwIf(!task.getCreatorId().equals(user.getId()),
                ErrorCode.NO_AUTH_ERROR, "仅任务创建者可操作该任务");
        return task;
    }

    public LearningTaskRecipient recipient(Long taskId, Long userId) {
        return recipientMapper.selectOne(new LambdaQueryWrapper<LearningTaskRecipient>()
                .eq(LearningTaskRecipient::getTaskId, taskId)
                .eq(LearningTaskRecipient::getUserId, userId)
                .last("limit 1"));
    }

    public LearningTaskRecipient requireRecipient(LearningTask task, User user) {
        LearningTaskRecipient recipient = recipient(task.getId(), user.getId());
        ThrowUtils.throwIf(recipient == null, ErrorCode.NO_AUTH_ERROR, "你不是该任务的接收人");
        return recipient;
    }

    public LearningTaskRecipient requireAccepted(LearningTask task, User user) {
        LearningTaskRecipient recipient = requireRecipient(task, user);
        ThrowUtils.throwIf(!TaskConstants.R_IN_PROGRESS.equals(recipient.getStatus())
                        && !TaskConstants.R_RETURNED.equals(recipient.getStatus()),
                ErrorCode.OPERATION_ERROR, "请先接收任务再作答");
        return recipient;
    }

    public void requirePublished(LearningTask task) {
        ThrowUtils.throwIf(!TaskConstants.TASK_PUBLISHED.equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "任务当前不可作答");
    }

    public LearningTaskSubmission latestSubmission(Long recipientId) {
        return submissionMapper.selectOne(new LambdaQueryWrapper<LearningTaskSubmission>()
                .eq(LearningTaskSubmission::getRecipientId, recipientId)
                .orderByDesc(LearningTaskSubmission::getRoundNo)
                .last("limit 1"));
    }

    /** 可修改轮次：无提交为 1；最近一轮被退回则进入新一轮；其他状态不允许继续修改。 */
    public int editableRound(Long recipientId) {
        LearningTaskSubmission latest = latestSubmission(recipientId);
        if (latest == null) return 1;
        if (TaskConstants.R_RETURNED.equals(latest.getStatus())) return latest.getRoundNo() + 1;
        return latest.getRoundNo();
    }

    public boolean editable(LearningTaskRecipient recipient) {
        return TaskConstants.R_IN_PROGRESS.equals(recipient.getStatus())
                || TaskConstants.R_RETURNED.equals(recipient.getStatus());
    }

    public void requireEditable(LearningTaskRecipient recipient, Integer roundNo) {
        ThrowUtils.throwIf(!editable(recipient), ErrorCode.OPERATION_ERROR,
                "当前提交状态不允许继续修改");
        int editable = editableRound(recipient.getId());
        ThrowUtils.throwIf(roundNo == null || roundNo != editable, ErrorCode.OPERATION_ERROR,
                "提交轮次已变化，请刷新页面");
    }

    public boolean late(LearningTask task, java.util.Date now) {
        return task.getDueAt() != null && now.after(task.getDueAt());
    }

    public void requireSubmittable(LearningTask task, LearningTaskRecipient recipient, java.util.Date now) {
        requirePublished(task);
        requireEditable(recipient, editableRound(recipient.getId()));
        if (late(task, now) && !Integer.valueOf(1).equals(task.getAllowLate())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已超过截止时间且不允许逾期提交");
        }
    }
}
