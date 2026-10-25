package com.yupi.springbootinit.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.LearningTaskItemMapper;
import com.yupi.springbootinit.model.entity.LearningTask;
import com.yupi.springbootinit.model.entity.LearningTaskItem;
import com.yupi.springbootinit.model.entity.LearningTaskRecipient;
import com.yupi.springbootinit.model.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;

/** 任务执行上下文：接收人、轮次、内容块与执行副本必须同时匹配。 */
@Service
public class TaskExecutionContextService {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private TaskAccessService access;
    @Resource
    private LearningTaskItemMapper itemMapper;

    public static class Context {
        public LearningTask task;
        public LearningTaskRecipient recipient;
        public LearningTaskItem item;
        public int roundNo;
        public Long sourceQuestionId;
        public String evaluationIdentity;
        public JsonNode snapshot;
    }

    public Context validate(User user, String taskCode, String itemCode, Long runtimeResourceId,
                            Integer roundNo, boolean experiment) {
        ThrowUtils.throwIf(StringUtils.isAnyBlank(taskCode, itemCode), ErrorCode.PARAMS_ERROR);
        LearningTask task = access.task(taskCode);
        access.requirePublished(task);
        LearningTaskRecipient recipient = access.requireAccepted(task, user);
        ThrowUtils.throwIf(roundNo == null, ErrorCode.PARAMS_ERROR, "缺少提交轮次");
        access.requireEditable(recipient, roundNo);
        LearningTaskItem item = itemMapper.selectOne(new LambdaQueryWrapper<LearningTaskItem>()
                .eq(LearningTaskItem::getTaskId, task.getId())
                .eq(LearningTaskItem::getItemCode, itemCode)
                .last("limit 1"));
        ThrowUtils.throwIf(item == null, ErrorCode.NOT_FOUND_ERROR, "任务内容块不存在");
        String expectedType = experiment ? TaskConstants.ITEM_EXPERIMENT : TaskConstants.ITEM_QUESTION;
        ThrowUtils.throwIf(!expectedType.equals(item.getItemType()), ErrorCode.PARAMS_ERROR, "内容块类型不匹配");
        ThrowUtils.throwIf(item.getRuntimeResourceId() == null
                        || !Objects.equals(item.getRuntimeResourceId(), runtimeResourceId),
                ErrorCode.PARAMS_ERROR, "执行副本与任务内容块不匹配");
        Context context = new Context();
        context.task = task;
        context.recipient = recipient;
        context.item = item;
        context.roundNo = roundNo;
        try {
            context.snapshot = JSON.readTree(StringUtils.defaultIfBlank(item.getPublishedSnapshotJson(), "{}"));
        } catch (Exception e) {
            throw new com.yupi.springbootinit.exception.BusinessException(ErrorCode.SYSTEM_ERROR, "任务快照损坏");
        }
        context.evaluationIdentity = context.snapshot.path("evaluationIdentity").asText(null);
        if (context.snapshot.hasNonNull("sourceQuestionId")) {
            context.sourceQuestionId = context.snapshot.path("sourceQuestionId").asLong();
        }
        return context;
    }
}
