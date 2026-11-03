package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.springbootinit.model.entity.LearningEvidenceContext;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 学习证据上下文数据库操作（v12）。 */
public interface LearningEvidenceContextMapper extends BaseMapper<LearningEvidenceContext> {

    /**
     * 行级锁读取：帮助记录与答题提交并发时必须串行化。
     *
     * <p>答题事务与帮助记录事务都先 {@code FOR UPDATE} 锁住同一行，
     * 因此"提示刚写下、提交同时发生"不会让提交读到过期的 helpLevel，
     * 也不会出现先更新 BKT 再补帮助标签的顺序。
     */
    @Select("SELECT * FROM learning_evidence_context WHERE id=#{id} AND isDelete=0 FOR UPDATE")
    LearningEvidenceContext selectByIdForUpdate(@Param("id") Long id);

    /**
     * 带版本校验的帮助等级更新（乐观锁兜底）：
     * 版本不匹配说明有并发写入，返回 0 由调用方重读重试。
     */
    @Update("UPDATE learning_evidence_context SET helpLevel=#{helpLevel}, "
            + "selfReportedHelp=#{selfReportedHelp}, helpSourceJson=#{helpSourceJson}, "
            + "eligibleForObservation=#{eligible}, eligibilityReason=#{reason}, "
            + "rowVersion=rowVersion+1, updateTime=NOW() "
            + "WHERE id=#{id} AND rowVersion=#{expectedVersion} AND isDelete=0")
    int updateHelpWithVersion(@Param("id") Long id,
                              @Param("helpLevel") String helpLevel,
                              @Param("selfReportedHelp") String selfReportedHelp,
                              @Param("helpSourceJson") String helpSourceJson,
                              @Param("eligible") Integer eligible,
                              @Param("reason") String reason,
                              @Param("expectedVersion") Integer expectedVersion);
}
