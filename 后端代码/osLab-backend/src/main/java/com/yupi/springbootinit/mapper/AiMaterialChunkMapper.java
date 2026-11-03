package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.springbootinit.model.entity.AiMaterialChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 资料段落块与 embedding 数据库操作（v11）。
 *
 * <p>检索路径全部使用投影查询而不是整实体：向量列与正文列分别按需读取，
 * 避免在 2 核 2GB 部署目标上一次把整块语料连正文带向量载入内存。
 *
 * <p>每条 SQL 都强制带 courseCode 与所有者条件——课程隔离与"不读他人资料"
 * 由查询本身保证，不依赖调用方自觉。
 */
public interface AiMaterialChunkMapper extends BaseMapper<AiMaterialChunk> {

    /**
     * 语义召回候选：只取 blockId 与向量，且严格限定 embedding 模型，
     * 不同模型的向量在 SQL 层就不可能被混用。
     */
    @Select("SELECT blockId, vectorJson FROM ai_material_chunk "
            + "WHERE courseCode=#{courseCode} AND isDelete=0 "
            + "AND embeddingModel=#{embeddingModel} AND vectorJson IS NOT NULL "
            + "AND (ownerScope='COURSE' OR (ownerScope='PERSONAL' AND userId=#{userId})) "
            + "LIMIT #{limit}")
    List<Map<String, Object>> selectVectorCandidates(@Param("courseCode") String courseCode,
                                                     @Param("userId") Long userId,
                                                     @Param("embeddingModel") String embeddingModel,
                                                     @Param("limit") int limit);

    /**
     * 关键词召回：SQL 侧先按 token 做 LIKE 预过滤，再在应用内做重叠度打分排序。
     * tokens 为空时退化为课程内全部块（仍受课程与所有者条件约束）。
     */
    @Select("<script>"
            + "SELECT id, blockId, sourceId, courseCode, ownerScope, revision, locator, title, nodeCodesJson, contentMd "
            + "FROM ai_material_chunk "
            + "WHERE courseCode=#{courseCode} AND isDelete=0 "
            + "AND (ownerScope='COURSE' OR (ownerScope='PERSONAL' AND userId=#{userId})) "
            + "<if test='tokens != null and tokens.size() > 0'> AND ("
            + "<foreach collection='tokens' item='token' separator=' OR '>"
            + "title LIKE CONCAT('%',#{token},'%') OR contentMd LIKE CONCAT('%',#{token},'%')"
            + "</foreach>)</if> "
            + "ORDER BY id LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> searchByKeywords(@Param("courseCode") String courseCode,
                                               @Param("userId") Long userId,
                                               @Param("tokens") List<String> tokens,
                                               @Param("limit") int limit);

    /** 按 blockId 批量取正文（仍限定课程与所有者，避免越权按 id 猜块）。 */
    @Select("<script>"
            + "SELECT id, blockId, sourceId, courseCode, ownerScope, revision, locator, title, nodeCodesJson, contentMd "
            + "FROM ai_material_chunk "
            + "WHERE courseCode=#{courseCode} AND isDelete=0 "
            + "AND (ownerScope='COURSE' OR (ownerScope='PERSONAL' AND userId=#{userId})) "
            + "AND blockId IN "
            + "<foreach collection='blockIds' item='blockId' open='(' separator=',' close=')'>#{blockId}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectScopedByBlockIds(@Param("courseCode") String courseCode,
                                                     @Param("userId") Long userId,
                                                     @Param("blockIds") List<String> blockIds);

    /** 增量重建用：读取现有块的哈希与向量模型，内容未变且模型一致则跳过。 */
    @Select("SELECT id, blockId, textHash, embeddingModel FROM ai_material_chunk "
            + "WHERE courseCode=#{courseCode} AND isDelete=0 "
            + "AND (ownerScope=#{ownerScope} AND (#{ownerScope}='COURSE' OR userId=#{userId}))")
    List<Map<String, Object>> selectIndexState(@Param("courseCode") String courseCode,
                                               @Param("userId") Long userId,
                                               @Param("ownerScope") String ownerScope);

    /** 某份个人资料的现存块（重建时清理已删除段落）。 */
    @Select("SELECT id, blockId, textHash, embeddingModel FROM ai_material_chunk "
            + "WHERE materialId=#{materialId} AND userId=#{userId} AND isDelete=0")
    List<Map<String, Object>> selectMaterialIndexState(@Param("materialId") Long materialId,
                                                       @Param("userId") Long userId);

    /** 统计某课程已建索引的块数（含未向量化），用于演示脚本与状态自检。 */
    @Select("SELECT COUNT(*) FROM ai_material_chunk WHERE courseCode=#{courseCode} AND isDelete=0 "
            + "AND (ownerScope='COURSE' OR (ownerScope='PERSONAL' AND userId=#{userId}))")
    int countIndexed(@Param("courseCode") String courseCode, @Param("userId") Long userId);

    /** 统计某课程已完成向量化的块数。 */
    @Select("SELECT COUNT(*) FROM ai_material_chunk WHERE courseCode=#{courseCode} AND isDelete=0 "
            + "AND embeddingModel=#{embeddingModel} AND vectorJson IS NOT NULL "
            + "AND (ownerScope='COURSE' OR (ownerScope='PERSONAL' AND userId=#{userId}))")
    int countEmbedded(@Param("courseCode") String courseCode, @Param("userId") Long userId,
                      @Param("embeddingModel") String embeddingModel);

    /**
     * 物理删除过期块。
     *
     * <p>索引块是可完全重建的派生数据，不是账本：这里必须物理删除，
     * 因为 blockId 上有唯一索引，逻辑删除的行会让同一块重新发布时插入失败。
     */
    @Delete("<script>"
            + "DELETE FROM ai_material_chunk WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deletePhysicallyByIds(@Param("ids") List<Long> ids);

    /** 兜底清理：blockId 唯一索引冲突时先物理删除再重写。 */
    @Delete("DELETE FROM ai_material_chunk WHERE blockId=#{blockId}")
    int deletePhysicallyByBlockId(@Param("blockId") String blockId);
}
