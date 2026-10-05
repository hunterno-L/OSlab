package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.LearningNodeContentMapper;
import com.yupi.springbootinit.model.dto.learning.LearningNodeContentSaveRequest;
import com.yupi.springbootinit.model.entity.LearningNodeContent;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningNodeContentVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningNodeContentService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 节点共享学习素材服务实现（Phase 8 §12.2）
 *
 * <p>按 nodeName 唯一 upsert；状态机 DRAFT/PUBLISHED/OFFLINE，
 * 学生侧（NodeWorkbench）仅消费 PUBLISHED 行。
 */
@Service
public class LearningNodeContentServiceImpl extends ServiceImpl<LearningNodeContentMapper, LearningNodeContent>
        implements LearningNodeContentService {

    private static final List<String> STATUSES = Arrays.asList("DRAFT", "PUBLISHED", "OFFLINE");

    /** 素材正文字符上限 */
    private static final int MAX_CONTENT_LENGTH = 50000;

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Override
    public LearningNodeContentVO getByNode(String nodeName) {
        ThrowUtils.throwIf(StringUtils.isBlank(nodeName), ErrorCode.PARAMS_ERROR, "节点名称不能为空");
        LearningNodeContent content = this.getOne(new LambdaQueryWrapper<LearningNodeContent>()
                .eq(LearningNodeContent::getNodeName, nodeName)
                .eq(LearningNodeContent::getIsDelete, 0));
        return content == null ? null : toVO(content);
    }

    @Override
    public List<LearningNodeContentVO> listAll() {
        List<LearningNodeContentVO> result = new ArrayList<>();
        for (LearningNodeContent content : this.list(new LambdaQueryWrapper<LearningNodeContent>()
                .eq(LearningNodeContent::getIsDelete, 0)
                .orderByAsc(LearningNodeContent::getNodeName))) {
            result.add(toVO(content));
        }
        return result;
    }

    @Override
    public boolean saveContent(LearningNodeContentSaveRequest request, User user) {
        ThrowUtils.throwIf(request == null || StringUtils.isBlank(request.getNodeName()),
                ErrorCode.PARAMS_ERROR, "节点名称不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getStatus())
                        || !STATUSES.contains(request.getStatus()),
                ErrorCode.PARAMS_ERROR, "非法状态，仅支持 DRAFT/PUBLISHED/OFFLINE");
        ThrowUtils.throwIf(CollectionUtils.isEmpty(knowledgeGraphService.findByName(request.getNodeName())),
                ErrorCode.NOT_FOUND_ERROR, "知识节点不存在：" + request.getNodeName());
        if (request.getContentMd() != null) {
            ThrowUtils.throwIf(request.getContentMd().length() > MAX_CONTENT_LENGTH,
                    ErrorCode.PARAMS_ERROR, "素材正文超过 " + MAX_CONTENT_LENGTH + " 字符上限");
        }
        if (StringUtils.isNotBlank(request.getResourceLinks())) {
            try {
                JSONUtil.parseArray(request.getResourceLinks());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "关联资料必须是 JSON 数组（如 [{\"title\":\"...\",\"url\":\"...\"}]）");
            }
        }
        LearningNodeContent existing = this.baseMapper.selectAnyByNodeName(request.getNodeName());
        if (existing == null) {
            LearningNodeContent content = new LearningNodeContent();
            content.setNodeName(request.getNodeName());
            content.setContentMd(request.getContentMd());
            content.setResourceLinks(request.getResourceLinks());
            content.setStatus(request.getStatus());
            content.setUpdateUserId(user.getId());
            content.setIsDelete(0);
            return this.save(content);
        }
        // 已有行（含历史逻辑删除行，唯一键被占用）直接覆盖并恢复
        return this.baseMapper.updateByNodeNameIncludeDeleted(request.getNodeName(),
                request.getContentMd(), request.getResourceLinks(), request.getStatus(),
                user.getId()) > 0;
    }

    private LearningNodeContentVO toVO(LearningNodeContent content) {
        LearningNodeContentVO vo = new LearningNodeContentVO();
        vo.setId(content.getId());
        vo.setNodeName(content.getNodeName());
        vo.setContentMd(content.getContentMd());
        vo.setResourceLinks(content.getResourceLinks());
        vo.setStatus(content.getStatus());
        vo.setUpdateTime(content.getUpdateTime());
        return vo;
    }
}
