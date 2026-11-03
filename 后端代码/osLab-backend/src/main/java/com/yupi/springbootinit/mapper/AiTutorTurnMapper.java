package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.springbootinit.model.entity.AiTutorTurn;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** AI 导师轮次数据库操作（v11）。 */
public interface AiTutorTurnMapper extends BaseMapper<AiTutorTurn> {

    /**
     * 进程重启恢复：把所有仍为 RUNNING 的轮次标成可重试。
     *
     * <p>重启前的执行线程已经消失，这些轮次永远不会自己走到终态；
     * 标记为 FAILED_RETRYABLE 后前端可以重新发起，且幂等键仍返回同一轮次，
     * 不会重复提交活动。
     *
     * @return 受影响行数
     */
    @Update("UPDATE ai_tutor_turn SET status='FAILED_RETRYABLE', "
            + "errorMessage='服务重启导致轮次中断，可重试' "
            + "WHERE status='RUNNING' AND isDelete=0")
    int markRunningTurnsRetryable();

    /** 供测试与运维核对：统计某状态的轮次数。 */
    @Update("UPDATE ai_tutor_turn SET status=#{status}, errorMessage=#{message} "
            + "WHERE id=#{id} AND status='RUNNING' AND isDelete=0")
    int finishIfRunning(@Param("id") Long id, @Param("status") String status,
                        @Param("message") String message);
}
