package com.yupi.springbootinit.ai.retrieval;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 个人资料的权限与幂等回归（计划 §5.2 / 任务书 §4）。
 *
 * <p>要点：资料严格限定本人；相同内容重复提交不产生第二份语料；
 * 删除时索引块物理清理、资料本体逻辑删除以保留可追溯性。
 */
class AiMaterialServiceTest {

    private final AiMaterialMapper materialMapper = mock(AiMaterialMapper.class);
    private final AiMaterialChunkMapper chunkMapper = mock(AiMaterialChunkMapper.class);
    private final MaterialIndexService indexService = mock(MaterialIndexService.class);
    private final AiContentScopeService contentScope = mock(AiContentScopeService.class);
    private final AiMaterialService service = new AiMaterialService();

    private static final String COURSE = "course.operating-systems";
    private final User owner = new User();
    private final User intruder = new User();

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "ai-material-test");
        TableInfoHelper.initTableInfo(assistant, AiMaterial.class);
        TableInfoHelper.initTableInfo(assistant, AiMaterialChunk.class);

        owner.setId(7L);
        intruder.setId(8L);
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        ReflectionTestUtils.setField(service, "chunkMapper", chunkMapper);
        ReflectionTestUtils.setField(service, "indexService", indexService);
        ReflectionTestUtils.setField(service, "contentScope", contentScope);
        ReflectionTestUtils.setField(service, "properties", new AiProperties());

        when(contentScope.courseTitle(COURSE)).thenReturn("操作系统");
        when(materialMapper.selectCount(any())).thenReturn(0L);
        when(materialMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(materialMapper.insert(any())).thenAnswer(invocation -> {
            AiMaterial material = invocation.getArgument(0);
            material.setId(700L);
            return 1;
        });
        MaterialIndexService.IndexReport report = new MaterialIndexService.IndexReport();
        report.setBlocks(2);
        // 真实索引服务会回写 chunkCount 与 indexStatus 并持久化；替身按同一契约回写，
        // 否则 create() 返回的 VO 会停在未索引状态，测不出"创建后立即可检索"
        when(indexService.indexPersonalMaterial(any())).thenAnswer(invocation -> {
            AiMaterial material = invocation.getArgument(0);
            material.setChunkCount(2);
            material.setIndexStatus(AiMaterial.INDEX_EMBEDDED);
            return report;
        });
    }

    // region 权限

    @Test
    void anotherUsersMaterialIsNeverReadable() {
        when(materialMapper.selectById(700L)).thenReturn(material(7L));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.requireOwn(intruder, "700")).getCode());
    }

    @Test
    void anotherUsersMaterialIsNeverDeletable() {
        when(materialMapper.selectById(700L)).thenReturn(material(7L));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.delete(intruder, "700")).getCode());
        verify(chunkMapper, never()).deletePhysicallyByIds(anyList());
        verify(materialMapper, never()).updateById(any());
    }

    @Test
    void missingOrDeletedMaterialIsNotFoundRatherThanForbidden() {
        when(materialMapper.selectById(700L)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.requireOwn(owner, "700")).getCode());

        AiMaterial deleted = material(7L);
        deleted.setIsDelete(1);
        when(materialMapper.selectById(701L)).thenReturn(deleted);
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.requireOwn(owner, "701")).getCode());
    }

    @Test
    void malformedMaterialIdIsRejected() {
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.requireOwn(owner, "not-a-number")).getCode());
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.requireOwn(owner, null)).getCode());
    }

    @Test
    void ownMaterialIsReadable() {
        when(materialMapper.selectById(700L)).thenReturn(material(7L));
        assertEquals(700L, service.requireOwn(owner, "700").getId());
    }

    // endregion

    // region 创建校验与幂等

    @Test
    void createRejectsUnknownCourse() {
        AiRequests.CreateMaterial request = request("course.not-published", "我的笔记");
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.create(owner, request)).getCode());
        verify(materialMapper, never()).insert(any());
    }

    @Test
    void createRejectsEmptyContentAndMissingCourse() {
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.create(owner, new AiRequests.CreateMaterial())).getCode());
        AiRequests.CreateMaterial withoutContent = request(COURSE, null);
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.create(owner, withoutContent)).getCode());
        verify(materialMapper, never()).insert(any());
    }

    @Test
    void createRejectsOversizedMaterial() {
        AiRequests.CreateMaterial request = request(COURSE, "长".repeat(30000));
        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(owner, request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), failure.getCode());
        assertTrue(failure.getMessage().contains("上限"), "要说明上限是多少");
        verify(materialMapper, never()).insert(any());
    }

    @Test
    void identicalContentIsNotIndexedTwice() {
        AiMaterial duplicate = material(7L);
        when(materialMapper.selectList(any())).thenReturn(List.of(duplicate));

        AiMaterialVO vo = service.create(owner, request(COURSE, "LRU 命中后更新顺序"));

        assertEquals("700", vo.getMaterialId());
        verify(materialMapper, never()).insert(any());
        verifyNoInteractions(indexService);
    }

    @Test
    void duplicateLookupIgnoresLogicallyDeletedMaterials() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AiMaterial>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);

        service.create(owner, request(COURSE, "LRU 命中后更新顺序"));

        verify(materialMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("isDelete"),
                "已逻辑删除的资料不能被当作重复内容复用（否则会返回 0 块的死记录）");
    }

    @Test
    void createdMaterialIsPersonalScopedAndIndexed() {
        AiMaterialVO vo = service.create(owner, request(COURSE, "LRU 命中后更新顺序"));

        assertEquals("700", vo.getMaterialId());
        assertEquals(COURSE, vo.getCourseCode());
        ArgumentCaptor<AiMaterial> captor = ArgumentCaptor.forClass(AiMaterial.class);
        verify(materialMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(AiMaterial.SCOPE_PERSONAL, captor.getValue().getOwnerScope());
        assertEquals(1, captor.getValue().getRevision());
        assertNotNull(captor.getValue().getTextHash(), "内容哈希用于增量索引与去重");
        verify(indexService).indexPersonalMaterial(any());
        assertEquals(2, vo.getChunkCount());
    }

    @Test
    void materialCountIsCappedPerCourse() {
        when(materialMapper.selectCount(any())).thenReturn(20L);
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.create(owner, request(COURSE, "又一份笔记"))).getCode());
        verify(materialMapper, never()).insert(any());
    }

    // endregion

    // region 删除

    @Test
    void deleteRemovesChunksPhysicallyButKeepsTheMaterialRowForTraceability() {
        AiMaterial mine = material(7L);
        when(materialMapper.selectById(700L)).thenReturn(mine);
        AiMaterialChunk chunk = new AiMaterialChunk();
        chunk.setId(1L);
        chunk.setMaterialId(700L);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        when(materialMapper.deleteById(700L)).thenReturn(1);

        assertTrue(service.delete(owner, "700"));

        verify(chunkMapper).deletePhysicallyByIds(List.of(1L));
        // 全局 logic-delete-field=isDelete：必须走 deleteById，updateById 设置 isDelete 不会生效
        verify(materialMapper).deleteById(700L);
        verify(materialMapper, never()).updateById(any());
    }

    @Test
    void deleteWithoutChunksStillMarksTheMaterialDeleted() {
        when(materialMapper.selectById(700L)).thenReturn(material(7L));
        when(chunkMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(materialMapper.deleteById(700L)).thenReturn(1);

        assertTrue(service.delete(owner, "700"));
        verify(chunkMapper, never()).deletePhysicallyByIds(anyList());
        verify(materialMapper).deleteById(700L);
    }

    // endregion

    // region fixtures

    private AiRequests.CreateMaterial request(String courseCode, String content) {
        AiRequests.CreateMaterial request = new AiRequests.CreateMaterial();
        request.setCourseCode(courseCode);
        request.setTitle("演示笔记");
        request.setText(content);
        request.setNodeCodes("os.memory.lru,os.memory.paging");
        return request;
    }

    private AiMaterial material(Long userId) {
        AiMaterial material = new AiMaterial();
        material.setId(700L);
        material.setUserId(userId);
        material.setCourseCode(COURSE);
        material.setOwnerScope(AiMaterial.SCOPE_PERSONAL);
        material.setTitle("演示笔记");
        material.setContentMd("LRU 命中后更新顺序");
        material.setTextHash("hash");
        material.setRevision(1);
        material.setChunkCount(2);
        material.setIndexStatus(AiMaterial.INDEX_EMBEDDED);
        material.setIsDelete(0);
        return material;
    }

    // endregion
}
