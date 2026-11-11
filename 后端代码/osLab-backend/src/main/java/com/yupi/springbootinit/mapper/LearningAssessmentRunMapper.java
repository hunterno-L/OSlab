package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 知识测评会话 Mapper */
@Mapper
public interface LearningAssessmentRunMapper extends BaseMapper<LearningAssessmentRun> {

    /** 交卷/草稿保存的串行化入口：同一 run 的并发写必须排队。 */
    @Select("SELECT * FROM learning_assessment_run WHERE id=#{id} FOR UPDATE")
    LearningAssessmentRun selectByIdForUpdate(@Param("id") Long id);

    /** 草稿保存的乐观锁更新；返回 0 表示有并发写入，由调用方提示重新载入。 */
    @Update("UPDATE learning_assessment_run SET rowVersion=rowVersion+1, "
            + "lastSaveId=#{saveId}, lastSaveAt=NOW() "
            + "WHERE id=#{id} AND status='ACTIVE' AND rowVersion=#{expectedVersion}")
    int bumpDraftVersion(@Param("id") Long id,
                         @Param("saveId") String saveId,
                         @Param("expectedVersion") Integer expectedVersion);
}
