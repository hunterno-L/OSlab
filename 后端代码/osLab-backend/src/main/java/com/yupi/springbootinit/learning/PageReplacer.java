package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.vo.learning.SimulationPageVO;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 页面置换算法（WP6）—— 确定性实现，固定样例交叉验证。
 * 支持：FIFO（先进先出）、LRU（最近最久未使用）、OPT（最佳置换，Phase 5 新增）。
 */
public final class PageReplacer {

    private PageReplacer() {
    }

    /**
     * 计算置换过程（不判分、不写库，纯计算）
     *
     * @param algorithm FIFO/LRU/OPT
     * @param frames    物理块数
     * @param sequence  页面访问序列
     */
    public static SimulationPageVO replace(String algorithm, int frames, List<Integer> sequence) {
        if (!"FIFO".equals(algorithm) && !"LRU".equals(algorithm) && !"OPT".equals(algorithm)) {
            throw new IllegalArgumentException("不支持的置换算法：" + algorithm);
        }
        List<SimulationPageVO.PageStep> steps = new ArrayList<>();
        // frames 有序：FIFO 队头在 index 0；LRU 最近使用在末尾
        LinkedList<Integer> memory = new LinkedList<>();
        int faults = 0;
        for (int i = 0; i < sequence.size(); i++) {
            Integer page = sequence.get(i);
            SimulationPageVO.PageStep step = new SimulationPageVO.PageStep();
            step.setStep(i);
            step.setPage(page);
            boolean hit = memory.contains(page);
            step.setFault(!hit);
            Integer evicted = null;
            if (hit) {
                if ("LRU".equals(algorithm)) {
                    // 命中：移到最近使用端
                    memory.remove(page);
                    memory.addLast(page);
                }
            } else {
                faults++;
                if (memory.size() >= frames) {
                    evicted = evict(algorithm, memory, sequence, i);
                }
                memory.addLast(page);
            }
            step.setEvicted(evicted);
            step.setFrames(new ArrayList<>(memory));
            steps.add(step);
        }
        SimulationPageVO vo = new SimulationPageVO();
        vo.setSteps(steps);
        vo.setPageFaults(faults);
        vo.setHitRate(sequence.isEmpty() ? 0.0
                : Math.round((1.0 - (double) faults / sequence.size()) * 10000.0) / 10000.0);
        return vo;
    }

    /**
     * 淘汰决策：FIFO/LRU 淘汰队头（进入序/最久未使用序）；
     * OPT 淘汰「未来最久不再访问」的页，多页并列时取内存序最前者保证确定性。
     */
    private static Integer evict(String algorithm, LinkedList<Integer> memory,
                                 List<Integer> sequence, int current) {
        if (!"OPT".equals(algorithm)) {
            return memory.removeFirst();
        }
        int victimIndex = 0;
        int farthest = -1;
        for (int m = 0; m < memory.size(); m++) {
            int nextUse = nextUseIndex(sequence, current, memory.get(m));
            if (nextUse > farthest) {
                farthest = nextUse;
                victimIndex = m;
            }
        }
        return memory.remove(victimIndex);
    }

    /**
     * 页面 page 在 current 之后下一次被访问的下标；不再访问返回 Integer.MAX_VALUE
     */
    private static int nextUseIndex(List<Integer> sequence, int current, int page) {
        for (int j = current + 1; j < sequence.size(); j++) {
            if (sequence.get(j).equals(page)) {
                return j;
            }
        }
        return Integer.MAX_VALUE;
    }
}
