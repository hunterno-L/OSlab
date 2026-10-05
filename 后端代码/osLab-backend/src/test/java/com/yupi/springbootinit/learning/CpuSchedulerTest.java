package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.dto.learning.SimulationCpuRequest;
import com.yupi.springbootinit.model.vo.learning.SimulationCpuVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CPU 调度固定样例交叉验证（WP6 验收：同一输入不同算法产生不同且正确的时间线）
 */
class CpuSchedulerTest {

    private List<SimulationCpuRequest.CpuProcessInput> sampleProcesses() {
        // 经典样例：P1(0,7) P2(2,4) P3(4,1) P4(5,4)
        List<SimulationCpuRequest.CpuProcessInput> processes = new ArrayList<>();
        processes.add(process("P1", 0, 7));
        processes.add(process("P2", 2, 4));
        processes.add(process("P3", 4, 1));
        processes.add(process("P4", 5, 4));
        return processes;
    }

    private SimulationCpuRequest.CpuProcessInput process(String name, int arrival, int burst) {
        SimulationCpuRequest.CpuProcessInput p = new SimulationCpuRequest.CpuProcessInput();
        p.setName(name);
        p.setArrivalTime(arrival);
        p.setBurstTime(burst);
        return p;
    }

    @Test
    void fcfs_runsInArrivalOrder() {
        SimulationCpuVO vo = CpuScheduler.schedule("FCFS", null, sampleProcesses());
        // FCFS 顺序：P1[0,7] P2[7,11] P3[11,12] P4[12,16]
        Assertions.assertEquals("P1", CpuScheduler.processAt(vo.getGantt(), 0));
        Assertions.assertEquals("P2", CpuScheduler.processAt(vo.getGantt(), 8));
        Assertions.assertEquals("P3", CpuScheduler.processAt(vo.getGantt(), 11));
        Assertions.assertEquals("P4", CpuScheduler.processAt(vo.getGantt(), 13));
        Assertions.assertNull(CpuScheduler.processAt(vo.getGantt(), 16));

        SimulationCpuVO.CpuProcessStat p1 = statOf(vo, "P1");
        Assertions.assertEquals(0, p1.getWaitingTime());
        Assertions.assertEquals(7, p1.getTurnaroundTime());
        SimulationCpuVO.CpuProcessStat p2 = statOf(vo, "P2");
        // P2: 完成 11，周转 = 11-2 = 9，等待 = 9-4 = 5
        Assertions.assertEquals(9, p2.getTurnaroundTime());
        Assertions.assertEquals(5, p2.getWaitingTime());
    }

    @Test
    void sjf_prefersShortestArrivedJob() {
        SimulationCpuVO vo = CpuScheduler.schedule("SJF", null, sampleProcesses());
        // SJF 非抢占：P1[0,7]；t=7 已到达 P2(4)/P3(1)/P4(4) → P3[7,8]；P2 与 P4 burst 相同取先到达 P2[8,12]；P4[12,16]
        Assertions.assertEquals("P3", CpuScheduler.processAt(vo.getGantt(), 7));
        Assertions.assertEquals("P2", CpuScheduler.processAt(vo.getGantt(), 9));
        Assertions.assertEquals("P4", CpuScheduler.processAt(vo.getGantt(), 13));
        // P3 等待 = 7-4 = 3
        Assertions.assertEquals(3, statOf(vo, "P3").getWaitingTime());
    }

    @Test
    void rr_interleavesWithTimeSlice() {
        SimulationCpuVO vo = CpuScheduler.schedule("RR", 2, sampleProcesses());
        // RR(2)：P1[0,2] P2[2,4] P1[4,6] P3[6,7] P2 到期……同输入下应与 FCFS 时间线不同
        SimulationCpuVO fcfs = CpuScheduler.schedule("FCFS", null, sampleProcesses());
        Assertions.assertNotEquals(
                segmentSignature(fcfs.getGantt()), segmentSignature(vo.getGantt()),
                "RR 与 FCFS 在相同时刻应运行不同进程");
        // t=0..1 P1，t=2..3 P2
        Assertions.assertEquals("P1", CpuScheduler.processAt(vo.getGantt(), 0));
        Assertions.assertEquals("P2", CpuScheduler.processAt(vo.getGantt(), 2));
        // P3 burst=1，只占 1 个时间单位
        SimulationCpuVO.CpuProcessStat p3 = statOf(vo, "P3");
        Assertions.assertEquals(1, p3.getBurstTime());
        Assertions.assertEquals(p3.getBurstTime(), p3.getCompletionTime() - p3.getStartTime());
        // 守恒：总运行时间 = 总 burst
        int totalBurst = processesSum(sampleProcesses());
        Assertions.assertEquals(totalBurst, vo.getGantt().get(vo.getGantt().size() - 1).getEnd());
    }

    @Test
    void rr_timeSliceChangeChangesTimeline() {
        SimulationCpuVO slice2 = CpuScheduler.schedule("RR", 2, sampleProcesses());
        SimulationCpuVO slice4 = CpuScheduler.schedule("RR", 4, sampleProcesses());
        Assertions.assertNotEquals(segmentSignature(slice2.getGantt()), segmentSignature(slice4.getGantt()),
                "时间片变化后甘特图应同步变化");
    }

    @Test
    void idleGapBeforeFirstArrival() {
        List<SimulationCpuRequest.CpuProcessInput> late = Arrays.asList(process("A", 3, 2));
        SimulationCpuVO vo = CpuScheduler.schedule("FCFS", null, late);
        // 首片段从到达时刻 3 开始；等待 = 周转(2) - 运行(2) = 0（CPU 空闲不算等待）
        Assertions.assertEquals(3, vo.getGantt().get(0).getStart());
        Assertions.assertEquals(2, statOf(vo, "A").getTurnaroundTime());
        Assertions.assertEquals(0, statOf(vo, "A").getWaitingTime());
    }

    private SimulationCpuVO.CpuProcessStat statOf(SimulationCpuVO vo, String name) {
        for (SimulationCpuVO.CpuProcessStat stat : vo.getProcessStats()) {
            if (name.equals(stat.getName())) {
                return stat;
            }
        }
        throw new AssertionError("未找到进程统计：" + name);
    }

    private int processesSum(List<SimulationCpuRequest.CpuProcessInput> processes) {
        int sum = 0;
        for (SimulationCpuRequest.CpuProcessInput p : processes) {
            sum += p.getBurstTime();
        }
        return sum;
    }

    private String segmentSignature(List<SimulationCpuVO.CpuSegment> gantt) {
        StringBuilder sb = new StringBuilder();
        for (SimulationCpuVO.CpuSegment segment : gantt) {
            sb.append(segment.getProcess()).append('[').append(segment.getStart())
                    .append(',').append(segment.getEnd()).append(']');
        }
        return sb.toString();
    }
}
