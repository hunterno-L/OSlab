package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.springbootinit.model.entity.LearningNodeContent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 节点共享学习素材数据库操作
 *
 * <p>项目全局开启 MyBatis-Plus 逻辑删除（isDelete），但本表 nodeName 有唯一键：
 * 逻辑删除行会继续占用唯一键，导致重新保存时 INSERT 冲突。因此 upsert 路径使用
 * 绕过逻辑删除过滤的原生 SQL（恢复被删行），查询/列表仍走 BaseMapper。
 */
public interface LearningNodeContentMapper extends BaseMapper<LearningNodeContent> {

    /**
     * 按节点名查任意状态行（含逻辑已删除；唯一键占用检测）
     */
    @Select("SELECT * FROM learning_node_content WHERE nodeName = #{nodeName} LIMIT 1")
    LearningNodeContent selectAnyByNodeName(@Param("nodeName") String nodeName);

    /**
     * 按节点名覆盖更新（含恢复逻辑删除行：isDelete 置回 0）
     */
    @Update("UPDATE learning_node_content SET contentMd = #{contentMd}, resourceLinks = #{resourceLinks}, "
            + "status = #{status}, updateUserId = #{updateUserId}, isDelete = 0, "
            + "updateTime = CURRENT_TIMESTAMP WHERE nodeName = #{nodeName}")
    int updateByNodeNameIncludeDeleted(@Param("nodeName") String nodeName,
                                       @Param("contentMd") String contentMd,
                                       @Param("resourceLinks") String resourceLinks,
                                       @Param("status") String status,
                                       @Param("updateUserId") Long updateUserId);

    /**
     * 按节点名物理删除（测试清理用；正常业务走状态机不删行）
     */
    @Delete("DELETE FROM learning_node_content WHERE nodeName = #{nodeName}")
    int physicalDeleteByNodeName(@Param("nodeName") String nodeName);
}
