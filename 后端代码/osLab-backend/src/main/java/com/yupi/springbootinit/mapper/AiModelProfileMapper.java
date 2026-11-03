package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.springbootinit.model.entity.AiModelProfile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 个人模型配置数据库操作（v12）。 */
public interface AiModelProfileMapper extends BaseMapper<AiModelProfile> {

    /**
     * 取消该用户全部激活配置：同一用户只保留一个当前生效模型。
     * 与激活写入同事务，避免并发激活出现两个"当前配置"。
     */
    @Update("UPDATE ai_model_profile SET active=0, updateTime=NOW() "
            + "WHERE userId=#{userId} AND active=1 AND isDelete=0")
    int deactivateAllForUser(@Param("userId") Long userId);
}
