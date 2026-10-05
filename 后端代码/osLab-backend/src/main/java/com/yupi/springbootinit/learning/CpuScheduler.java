package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.dto.learning.SimulationCpuRequest;
import com.yupi.springbootinit.model.vo.learning.SimulationCpuVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * CPU 调度算法（WP6）—— 确定性实现，固定样例交叉验证。
 * 支持：FCFS、非抢占 SJF、RR（时间片轮转）。
 *
 * <p>约定：
 * <ul>
 *   <li>时间为非负整数，时间单位与输入一致；</li>
 *   <li>同时刻到达时按输入顺序；</li>
 *   <li>SJF 并列时取先到达者，仍并列取输入顺序；</li>
 *   <li>RR 时间片用尽时，先入队“当前时刻已到达”的新进程，再重新入队当前进程。</li>
 * </ul>
 */
public final class CpuScheduler {

    private CpuScheduler() {
    }

    /**
     * 计算调度结果（不判分、不写库，纯计算）
     */
    public static SimulationCpuVO schedule(String algorithm, Integer timeSlice,
                                           List<SimulationCpuRequest.CpuProcessInput> processes) {
        List<SimulationCpuVO.CpuSegment> gantt;
        switch (algorithm) {
            case "FCFS":
                gantt = fcfs(processes);
                break;
            case "SJF":
                gantt = sjf(processes);
                break;
            case "RR":
                gantt = roundRobin(processes, timeSlice);
                break;
            default:
                throw new IllegalArgumentException("不支持的调度算法：" + algorithm);
        }
        return buildResult(gantt, processes);
    }

    /**
     * 该时间单位上正在运行的进程名；无运行返回 null
     */
    public static String processAt(List<SimulationCpuVO.CpuSegment> gantt, int timeUnit) {
        for (SimulationCpuVO.CpuSegment segment : gantt) {
            if (timeUnit >= segment.getStart() && timeUnit < segment.getEnd()) {
                return segment.getProcess();
            }
        }
        return null;
    }

    // region 算法

    private static List<SimulationCpuVO.CpuSegment> fcfs(List<SimulationCpuRequest.CpuProcessInput> processes) {
        List<SimulationCpuRequest.CpuProcessInput> sorted = new ArrayList<>(processes);
        // 稳定排序：到达时间相同保持输入顺序
        sorted.sort(Comparator.comparingInt(SimulationCpuRequest.CpuProcessInput::getArrivalTime));
        List<SimulationCpuVO.CpuSegment> gantt = new ArrayList<>();
        int time = 0;
        for (SimulationCpuRequest.CpuProcessInput p : sorted) {
            int start = Math.max(time, p.getArrivalTime());
            addSegment(gantt, p.getName(), start, start + p.getBurstTime());
            time = start + p.getBurstTime();
        }
        return gantt;
    }

    private static List<SimulationCpuVO.CpuSegment> sjf(List<SimulationCpuRequest.CpuProcessInput> processes) {
        int n = processes.size();
        boolean[] done = new boolean[n];
        List<SimulationCpuVO.CpuSegment> gantt = new ArrayList<>();
        int time = 0;
        int finished = 0;
        while (finished < n) {
            int pick = -1;
            for (int i = 0; i < n; i++) {
                SimulationCpuRequest.CpuProcessInput p = processes.get(i);
                if (done[i] || p.getArrivalTime() > time) {
                    continue;
                }
                if (pick < 0) {
                    pick = i;
                    continue;
                }
                SimulationCpuRequest.CpuProcessInput best = processes.get(pick);
                if (p.getBurstTime() < best.getBurstTime()
                        || (p.getBurstTime() == best.getBurstTime()
                        && p.getArrivalTime() < best.getArrivalTime())) {
                    pick = i;
                }
            }
            if (pick < 0) {
                // 无已到达进程：跳到最早到达时刻
                int next = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!done[i]) {
                        next = Math.min(next, processes.get(i).getArrivalTime());
                    }
                }
                time = next;
                continue;
            }
            SimulationCpuRequest.CpuProcessInput p = processes.get(pick);
            addSegment(gantt, p.getName(), time, time + p.getBurstTime());
            time += p.getBurstTime();
            done[pick] = true;
            finished++;
        }
        return gantt;
    }

    private static List<SimulationCpuVO.CpuSegment> roundRobin(
            List<SimulationCpuRequest.CpuProcessInput> processes, int timeSlice) {
        if (timeSlice <= 0) {
            throw new IllegalArgumentException("RR 算法时间片必须大于 0");
        }
        int n = processes.size();
        int[] remaining = new int[n];
        for (int i = 0; i < n; i++) {
            remaining[i] = processes.get(i).getBurstTime();
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        // 初始按到达时间排序（稳定，保持输入顺序）
        order.sort(Comparator.comparingInt(i -> processes.get(i).getArrivalTime()));
        Queue<Integer> queue = new LinkedList<>();
        int time = 0;
        int nextToArrive = 0;
        List<SimulationCpuVO.CpuSegment> gantt = new ArrayList<>();
        while (nextToArrive < n || !queue.isEmpty()) {
            // 先入队当前时刻已到达的新进程
            while (nextToArrive < n && processes.get(order.get(nextToArrive)).getArrivalTime() <= time) {
                queue.offer(order.get(nextToArrive));
                nextToArrive++;
            }
            if (queue.isEmpty()) {
                // 快进到下一个到达时刻
                time = processes.get(order.get(nextToArrive)).getArrivalTime();
                continue;
            }
            int idx = queue.poll();
            int run = Math.min(timeSlice, remaining[idx]);
            addSegment(gantt, processes.get(idx).getName(), time, time + run);
            time += run;
            remaining[idx] -= run;
            // 时间片用尽：先入队已到达新进程，再重新入队当前进程
            while (nextToArrive < n && processes.get(order.get(nextToArrive)).getArrivalTime() <= time) {
                queue.offer(order.get(nextToArrive));
                nextToArrive++;
            }
            if (remaining[idx] > 0) {
                queue.offer(idx);
            }
        }
        return gantt;
    }

    private static void addSegment(List<SimulationCpuVO.CpuSegment> gantt, String process, int start, int end) {
        if (end <= start) {
            return;
        }
        if (!gantt.isEmpty()) {
            SimulationCpuVO.CpuSegment last = gantt.get(gantt.size() - 1);
            if (last.getProcess().equals(process) && last.getEnd() == start) {
                // 合并同进程相邻片段
                last.setEnd(end);
                return;
            }
        }
        SimulationCpuVO.CpuSegment segment = new SimulationCpuVO.CpuSegment();
        segment.setProcess(process);
        segment.setStart(start);
        segment.setEnd(end);
        gantt.add(segment);
    }

    // endregion

    private static SimulationCpuVO buildResult(List<SimulationCpuVO.CpuSegment> gantt,
                                               List<SimulationCpuRequest.CpuProcessInput> processes) {
        SimulationCpuVO vo = new SimulationCpuVO();
        vo.setGantt(gantt);
        List<SimulationCpuVO.CpuProcessStat> stats = new ArrayList<>();
        long totalWaiting = 0;
        long totalTurnaround = 0;
        for (SimulationCpuRequest.CpuProcessInput p : processes) {
            SimulationCpuVO.CpuProcessStat stat = new SimulationCpuVO.CpuProcessStat();
            stat.setName(p.getName());
            stat.setArrivalTime(p.getArrivalTime());
            stat.setBurstTime(p.getBurstTime());
            // 首个片段起点 = 首次获得 CPU；最后片段终点 = 完成
            Integer startTime = null;
            Integer completionTime = null;
            for (SimulationCpuVO.CpuSegment segment : gantt) {
                if (segment.getProcess().equals(p.getName())) {
                    if (startTime == null) {
                        startTime = segment.getStart();
                    }
                    completionTime = segment.getEnd();
                }
            }
            stat.setStartTime(startTime);
            stat.setCompletionTime(completionTime);
            int turnaround = completionTime - p.getArrivalTime();
            int waiting = turnaround - p.getBurstTime();
            stat.setTurnaroundTime(turnaround);
            stat.setWaitingTime(waiting);
            totalWaiting += waiting;
            totalTurnaround += turnaround;
            stats.add(stat);
        }
        vo.setProcessStats(stats);
        int n = processes.size();
        vo.setAvgWaitingTime(n == 0 ? 0.0 : round2((double) totalWaiting / n));
        vo.setAvgTurnaroundTime(n == 0 ? 0.0 : round2((double) totalTurnaround / n));
        return vo;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
