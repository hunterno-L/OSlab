package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** AI 学习会话数据库操作（v11）。 */
public interface AiLearningSessionMapper extends BaseMapper<AiLearningSession> {

    /**
     * 写入计划与当前任务位置。
     *
     * <p>必须用显式 SQL：MyBatis-Plus 的 updateById 默认忽略 null 字段，
     * 而"任务完成后清空当前任务位置"正是要写入 NULL。
     * 不修改 rowVersion——计划由服务端推进，不应让前端持有的版本失效。
     */
    @Update("UPDATE ai_learning_session SET planJson=#{planJson}, currentActionJson=#{currentActionJson}, "
            + "updateTime=NOW() WHERE id=#{id} AND userId=#{userId} AND isDelete=0")
    int updatePlan(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("planJson") String planJson,
                   @Param("currentActionJson") String currentActionJson);
}
