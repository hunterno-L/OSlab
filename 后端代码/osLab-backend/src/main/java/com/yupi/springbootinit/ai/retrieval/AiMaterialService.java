package com.yupi.springbootinit.ai.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.AiProperties;
import com.yupi.springbootinit.ai.plan.AiContentScopeService;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiMaterialChunkMapper;
import com.yupi.springbootinit.mapper.AiMaterialMapper;
import com.yupi.springbootinit.model.dto.ai.AiRequests;
import com.yupi.springbootinit.model.entity.AiMaterial;
import com.yupi.springbootinit.model.entity.AiMaterialChunk;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiMaterialVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 个人资料接入（计划 §6.2 资料接口 / P0-2）。
 *
 * <p>七天内支持粘贴文本与 UTF-8 Markdown / TXT，限量并按课程隔离；
 * 读取与删除都校验所有者，绝不返回或修改他人资料。
 *
 * <p>资料写入后立即建索引：embedding 可用则同时向量化，
 * 不可用时仍建立关键词与图谱索引，并把索引状态诚实标为 KEYWORD_ONLY。
 */
@Slf4j
@Service
public class AiMaterialService {

    @Resource
    private AiMaterialMapper materialMapper;

    @Resource
    private AiMaterialChunkMapper chunkMapper;

    @Resource
    private MaterialIndexService indexService;

    @Resource
    private AiContentScopeService contentScope;

    @Resource private com.yupi.springbootinit.course.EnabledCourses enabledCourses;

    @Resource
    private AiProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public AiMaterialVO create(User user, AiRequests.CreateMaterial request) {
        if (request == null || StringUtils.isBlank(request.getCourseCode())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "courseCode 不能为空");
        }
        String courseCode = request.getCourseCode().trim();
        enabledCourses.require(courseCode);
        if (contentScope.courseTitle(courseCode) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "课程不存在：" + courseCode);
        }
        String content = StringUtils.isNotBlank(request.getContentMd())
                ? request.getContentMd() : request.getText();
        if (StringUtils.isBlank(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请提供 text 或 contentMd");
        }
        int maxChars = properties.getRetrieval().getMaterialMaxChars();
        if (content.length() > maxChars) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "资料过长（" + content.length() + " 字），单条上限 " + maxChars + " 字");
        }
        String title = StringUtils.trimToEmpty(request.getTitle());
        if (title.isEmpty()) {
            title = StringUtils.abbreviate(content.replaceAll("\\s+", " ").trim(), 40);
        }
        if (title.length() > 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        Long existing = materialMapper.selectCount(new LambdaQueryWrapper<AiMaterial>()
                .eq(AiMaterial::getUserId, user.getId())
                .eq(AiMaterial::getCourseCode, courseCode));
        if (existing != null && existing >= properties.getRetrieval().getMaterialMaxCount()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该课程下个人资料已达上限 " + properties.getRetrieval().getMaterialMaxCount()
                            + " 条，请先删除不再需要的资料");
        }
        String textHash = MaterialIndexService.sha256(content);
        AiMaterial duplicate = findByHash(user.getId(), courseCode, textHash);
        if (duplicate != null) {
            // 相同内容重复提交：返回既有资料，不产生第二份语料
            log.info("[ai-material] duplicate content ignored user={} material={}", user.getId(), duplicate.getId());
            return toVo(duplicate);
        }
        AiMaterial material = new AiMaterial();
        material.setUserId(user.getId());
        material.setCourseCode(courseCode);
        material.setOwnerScope(AiMaterial.SCOPE_PERSONAL);
        material.setTitle(title);
        material.setContentMd(content);
        material.setTextHash(textHash);
        material.setRevision(1);
        material.setChunkCount(0);
        material.setNodeCodesJson(AiJson.write(splitNodeCodes(request.getNodeCodes())));
        material.setIndexStatus(AiMaterial.INDEX_PENDING);
        material.setIsDelete(0);
        materialMapper.insert(material);
        MaterialIndexService.IndexReport report = indexService.indexPersonalMaterial(material);
        log.info("[ai-material] indexed material={} chunks={} degraded={}",
                material.getId(), report.getBlocks(), report.isDegraded());
        return toVo(material);
    }

    /** 本人资料列表（按课程过滤）。 */
    public List<AiMaterialVO> list(User user, String courseCode) {
        LambdaQueryWrapper<AiMaterial> query = new LambdaQueryWrapper<AiMaterial>()
                .eq(AiMaterial::getUserId, user.getId())
                .eq(AiMaterial::getIsDelete, 0)
                .orderByDesc(AiMaterial::getCreateTime)
                .orderByDesc(AiMaterial::getId);
        if (StringUtils.isNotBlank(courseCode)) {
            query.eq(AiMaterial::getCourseCode, courseCode.trim());
        }
        List<AiMaterialVO> out = new ArrayList<>();
        for (AiMaterial material : materialMapper.selectList(query)) {
            out.add(toVo(material));
        }
        return out;
    }

    /** 读取单条资料正文（校验所有者）。 */
    public AiMaterial requireOwn(User user, String materialId) {
        Long id;
        try {
            id = AiJson.parseId(materialId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "materialId 非法");
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "materialId 不能为空");
        }
        AiMaterial material = materialMapper.selectById(id);
        if (material == null || (material.getIsDelete() != null && material.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资料不存在");
        }
        if (!material.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该资料");
        }
        return material;
    }

    /** 删除本人资料及其索引块（校验所有者）。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(User user, String materialId) {
        AiMaterial material = requireOwn(user, materialId);
        List<AiMaterialChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiMaterialChunk>()
                .eq(AiMaterialChunk::getMaterialId, material.getId())
                .eq(AiMaterialChunk::getUserId, user.getId()));
        List<Long> ids = new ArrayList<>();
        for (AiMaterialChunk chunk : chunks) {
            ids.add(chunk.getId());
        }
        for (int from = 0; from < ids.size(); from += 200) {
            chunkMapper.deletePhysicallyByIds(new ArrayList<>(
                    ids.subList(from, Math.min(from + 200, ids.size()))));
        }
        // 资料是用户数据，逻辑删除保留可追溯性。
        // 全局配置了 logic-delete-field=isDelete，手动 setIsDelete(1)+updateById 会被
        // 逻辑删除插件过滤成空更新，必须走 deleteById 生成 SET isDelete=1 的 SQL。
        return materialMapper.deleteById(material.getId()) > 0;
    }

    /** 重建某课程的个人资料索引（演示脚本与运维用）。 */
    public int reindexPersonal(User user, String courseCode) {
        LambdaQueryWrapper<AiMaterial> query = new LambdaQueryWrapper<AiMaterial>()
                .eq(AiMaterial::getUserId, user.getId())
                .eq(AiMaterial::getIsDelete, 0);
        if (StringUtils.isNotBlank(courseCode)) {
            query.eq(AiMaterial::getCourseCode, courseCode.trim());
        }
        int count = 0;
        for (AiMaterial material : materialMapper.selectList(query)) {
            indexService.indexPersonalMaterial(material);
            count++;
        }
        return count;
    }

    private AiMaterial findByHash(Long userId, String courseCode, String textHash) {
        List<AiMaterial> found = materialMapper.selectList(new LambdaQueryWrapper<AiMaterial>()
                .eq(AiMaterial::getUserId, userId)
                .eq(AiMaterial::getCourseCode, courseCode)
                .eq(AiMaterial::getTextHash, textHash)
                .eq(AiMaterial::getIsDelete, 0)
                .last("limit 1"));
        return found.isEmpty() ? null : found.get(0);
    }

    public AiMaterialVO toVo(AiMaterial material) {
        AiMaterialVO vo = new AiMaterialVO();
        vo.setMaterialId(AiJson.id(material.getId()));
        vo.setChunkCount(material.getChunkCount());
        vo.setRevision(material.getRevision());
        vo.setCourseCode(material.getCourseCode());
        vo.setTitle(material.getTitle());
        vo.setIndexStatus(material.getIndexStatus());
        vo.setCreatedAt(AiJson.iso(material.getCreateTime()));
        if (AiMaterial.INDEX_EMBEDDED.equals(material.getIndexStatus())) {
            List<AiMaterialChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiMaterialChunk>()
                    .eq(AiMaterialChunk::getMaterialId, material.getId())
                    .isNotNull(AiMaterialChunk::getEmbeddingModel)
                    .last("limit 1"));
            if (!chunks.isEmpty()) {
                vo.setEmbeddingModel(chunks.get(0).getEmbeddingModel());
            }
        }
        return vo;
    }

    private static List<String> splitNodeCodes(String nodeCodes) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isBlank(nodeCodes)) {
            return out;
        }
        for (String part : nodeCodes.split("[,，;；\\s]+")) {
            if (StringUtils.isNotBlank(part)) {
                out.add(part.trim());
            }
        }
        return out;
    }
}
