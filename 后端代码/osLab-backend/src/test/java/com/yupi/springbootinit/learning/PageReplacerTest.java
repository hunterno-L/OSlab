package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.vo.learning.SimulationPageVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 页面置换固定样例交叉验证（WP6 验收）
 * 序列 7,0,1,2,0,3,0,4,2,3,0,3,2，3 块（手工逐步验算）：
 * FIFO 缺页 10 次（命中 t4,t11,t12）；LRU 缺页 9 次（命中 t4,t11,t12 + t6）；
 * OPT 缺页 7 次（命中 t4,t6,t8,t9,t11,t12，Phase 5 新增）
 */
class PageReplacerTest {

    private static final List<Integer> SEQ = Arrays.asList(7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2);

    @Test
    void fifo_classicSequence_tenFaults() {
        SimulationPageVO vo = PageReplacer.replace("FIFO", 3, SEQ);
        Assertions.assertEquals(10, vo.getPageFaults());
        // t3 访问 2：内存 [7,0,1] 满，FIFO 淘汰队头 7
        SimulationPageVO.PageStep step3 = vo.getSteps().get(3);
        Assertions.assertTrue(step3.getFault());
        Assertions.assertEquals(7, step3.getEvicted());
        Assertions.assertEquals(Arrays.asList(0, 1, 2), step3.getFrames());
        // t4 访问 0：命中，帧不变
        SimulationPageVO.PageStep step4 = vo.getSteps().get(4);
        Assertions.assertFalse(step4.getFault());
        Assertions.assertNull(step4.getEvicted());
        Assertions.assertEquals(Arrays.asList(0, 1, 2), step4.getFrames());
        // t5 访问 3：缺页，淘汰 0（FIFO 队头）
        Assertions.assertEquals(0, vo.getSteps().get(5).getEvicted());
    }

    @Test
    void lru_classicSequence_nineFaults() {
        SimulationPageVO vo = PageReplacer.replace("LRU", 3, SEQ);
        Assertions.assertEquals(9, vo.getPageFaults());
        // t3 访问 2：淘汰最久未使用的 7
        SimulationPageVO.PageStep step3 = vo.getSteps().get(3);
        Assertions.assertTrue(step3.getFault());
        Assertions.assertEquals(7, step3.getEvicted());
        // t5 访问 3：内存 [0,1,2]（0 在 t4 刚用过）→ 淘汰 1
        Assertions.assertEquals(1, vo.getSteps().get(5).getEvicted());
        // t7 访问 4：内存 [2,3,0]（2 自 t3 后未用）→ 淘汰 2
        Assertions.assertEquals(2, vo.getSteps().get(7).getEvicted());
        // t11 访问 3：命中
        Assertions.assertFalse(vo.getSteps().get(11).getFault());
    }

    @Test
    void lru_hitMovesPageToRecencyEnd() {
        SimulationPageVO lru = PageReplacer.replace("LRU", 3, SEQ);
        // t6 访问 0 命中后，0 应位于最近使用端（队尾）
        SimulationPageVO.PageStep step6 = lru.getSteps().get(6);
        Assertions.assertFalse(step6.getFault());
        Assertions.assertEquals(Integer.valueOf(0),
                step6.getFrames().get(step6.getFrames().size() - 1));
    }

    @Test
    void fifoVsLru_differOnSameInput() {
        SimulationPageVO fifo = PageReplacer.replace("FIFO", 3, SEQ);
        SimulationPageVO lru = PageReplacer.replace("LRU", 3, SEQ);
        // 同一输入不同算法：缺页数不同（10 vs 9），逐步淘汰序列不同
        Assertions.assertEquals(10, fifo.getPageFaults());
        Assertions.assertEquals(9, lru.getPageFaults());
        Assertions.assertNotEquals(fifo.getSteps().get(5).getEvicted(), lru.getSteps().get(5).getEvicted(),
                "t5 处 FIFO 淘汰 0、LRU 淘汰 1，应不同");
    }

    @Test
    void hitRate_consistentWithFaults() {
        SimulationPageVO vo = PageReplacer.replace("FIFO", 3, SEQ);
        Assertions.assertEquals(3.0 / 13.0, vo.getHitRate(), 1e-4);
    }

    @Test
    void fewerFramesMoreFaults_monotonicSanity() {
        SimulationPageVO small = PageReplacer.replace("FIFO", 2, SEQ);
        SimulationPageVO large = PageReplacer.replace("FIFO", 4, SEQ);
        Assertions.assertTrue(small.getPageFaults() >= large.getPageFaults(),
                "更多物理块不应产生更多缺页");
    }

    // ---- OPT（Phase 5 新增）：经典序列手工验算，7 次缺页 ----

    @Test
    void opt_classicSequence_sevenFaults() {
        SimulationPageVO vo = PageReplacer.replace("OPT", 3, SEQ);
        Assertions.assertEquals(7, vo.getPageFaults());
        // 命中步：t4(0), t6(0), t8(2), t9(3), t11(3), t12(2)
        Assertions.assertFalse(vo.getSteps().get(4).getFault());
        Assertions.assertFalse(vo.getSteps().get(6).getFault());
        Assertions.assertFalse(vo.getSteps().get(8).getFault());
        Assertions.assertFalse(vo.getSteps().get(9).getFault());
        Assertions.assertFalse(vo.getSteps().get(11).getFault());
        Assertions.assertFalse(vo.getSteps().get(12).getFault());
        Assertions.assertEquals(6.0 / 13.0, vo.getHitRate(), 1e-4);
    }

    @Test
    void opt_evictsFarthestFutureUse() {
        SimulationPageVO vo = PageReplacer.replace("OPT", 3, SEQ);
        // t3 访问 2：7 与 1 均不再使用，并列取内存序最前 → 淘汰 7
        SimulationPageVO.PageStep step3 = vo.getSteps().get(3);
        Assertions.assertTrue(step3.getFault());
        Assertions.assertEquals(7, step3.getEvicted());
        Assertions.assertEquals(Arrays.asList(0, 1, 2), step3.getFrames());
        // t5 访问 3：1 不再使用（0→t6、2→t8）→ 淘汰 1
        Assertions.assertEquals(1, vo.getSteps().get(5).getEvicted());
        // t7 访问 4：0→t10 最远（2→t8、3→t9）→ 淘汰 0
        Assertions.assertEquals(0, vo.getSteps().get(7).getEvicted());
        Assertions.assertEquals(Arrays.asList(2, 3, 4), vo.getSteps().get(7).getFrames());
        // t10 访问 0：4 不再使用 → 淘汰 4
        Assertions.assertEquals(4, vo.getSteps().get(10).getEvicted());
    }

    @Test
    void opt_tieBreakDeterministic() {
        // 全部页都不再使用时，稳定淘汰内存序最前页
        SimulationPageVO vo = PageReplacer.replace("OPT", 3, Arrays.asList(1, 2, 3, 4));
        SimulationPageVO.PageStep step3 = vo.getSteps().get(3);
        Assertions.assertTrue(step3.getFault());
        Assertions.assertEquals(1, step3.getEvicted());
        Assertions.assertEquals(Arrays.asList(2, 3, 4), step3.getFrames());
        // 重复运行结果一致（纯函数确定性）
        SimulationPageVO again = PageReplacer.replace("OPT", 3, Arrays.asList(1, 2, 3, 4));
        Assertions.assertEquals(again.getSteps().get(3).getEvicted(), step3.getEvicted());
    }

    @Test
    void fifoLruOpt_faultOrderingOnSameInput() {
        SimulationPageVO fifo = PageReplacer.replace("FIFO", 3, SEQ);
        SimulationPageVO lru = PageReplacer.replace("LRU", 3, SEQ);
        SimulationPageVO opt = PageReplacer.replace("OPT", 3, SEQ);
        // 经典结论：OPT ≤ LRU ≤ FIFO（10 / 9 / 7）
        Assertions.assertTrue(opt.getPageFaults() <= lru.getPageFaults(),
                "OPT 缺页数不应多于 LRU");
        Assertions.assertTrue(lru.getPageFaults() <= fifo.getPageFaults(),
                "LRU 缺页数不应多于 FIFO");
        Assertions.assertEquals(10, fifo.getPageFaults());
        Assertions.assertEquals(9, lru.getPageFaults());
        Assertions.assertEquals(7, opt.getPageFaults());
    }

    @Test
    void opt_stackProperty_noBeladyAnomaly() {
        // OPT 属栈式算法：物理块增多缺页不应增加
        SimulationPageVO small = PageReplacer.replace("OPT", 2, SEQ);
        SimulationPageVO large = PageReplacer.replace("OPT", 4, SEQ);
        Assertions.assertTrue(small.getPageFaults() >= large.getPageFaults(),
                "OPT 更多物理块不应产生更多缺页");
    }

    @Test
    void unsupportedAlgorithm_rejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> PageReplacer.replace("LFU", 3, SEQ));
    }

    // region AI 诊断案例依赖的确定性契约（计划 §5.4）

    @Test
    void diagnosisExample_fifoEvictsOneAndLruEvictsTwoWithSameFaultCount() {
        // 计划正文的例子：三页框访问 1,2,3,1,4，两种算法缺页数相同但换出页不同，
        // 因此它能区分"混淆 FIFO 与 LRU"，也能区分"命中后没有更新顺序"
        List<Integer> sequence = Arrays.asList(1, 2, 3, 1, 4);
        SimulationPageVO fifo = PageReplacer.replace("FIFO", 3, sequence);
        SimulationPageVO lru = PageReplacer.replace("LRU", 3, sequence);

        Assertions.assertEquals(4, fifo.getPageFaults());
        Assertions.assertEquals(4, lru.getPageFaults());
        Assertions.assertEquals(1, countEvictions(fifo), "三页框装 1,2,3 后只有访问 4 会触发一次换出");
        Assertions.assertEquals(1, countEvictions(lru));
        Assertions.assertEquals(1, fifo.getSteps().get(4).getEvicted(), "FIFO 换出最早装入的 1");
        Assertions.assertEquals(2, lru.getSteps().get(4).getEvicted(), "LRU 换出最久未用的 2（1 刚被命中）");
    }

    @Test
    void visiblePrefixCannotLeakThePredictedVictim() {
        // AI 预测任务在学生作答前只允许下发前缀：截断序列后，
        // 步骤数必须等于前缀长度，且待预测步本身不在其中
        List<Integer> sequence = Arrays.asList(1, 2, 3, 1, 4);
        SimulationPageVO full = PageReplacer.replace("LRU", 3, sequence);
        SimulationPageVO prefix = PageReplacer.replace("LRU", 3, sequence.subList(0, 4));

        Assertions.assertEquals(5, full.getSteps().size());
        Assertions.assertEquals(4, prefix.getSteps().size(), "前缀计算的步骤数必须等于前缀长度");
        Assertions.assertNull(prefix.getSteps().get(3).getEvicted(),
                "待预测步之前的可见步骤不含换出页，因此轨迹本身不会泄露答案");
        Assertions.assertEquals(3, prefix.getPageFaults(), "前缀缺页数独立于后续访问");
    }

    @Test
    void repeatedRunsAreIdenticalSoToolAndGradingAgree() {
        // check_page_trace 工具与提交判分必须得到同一份结果，否则诊断会和判分互相矛盾
        SimulationPageVO first = PageReplacer.replace("LRU", 3, SEQ);
        SimulationPageVO second = PageReplacer.replace("LRU", 3, SEQ);
        Assertions.assertEquals(first.getPageFaults(), second.getPageFaults());
        Assertions.assertEquals(first.getSteps().size(), second.getSteps().size());
        for (int i = 0; i < first.getSteps().size(); i++) {
            Assertions.assertEquals(first.getSteps().get(i).getEvicted(), second.getSteps().get(i).getEvicted());
            Assertions.assertEquals(first.getSteps().get(i).getFrames(), second.getSteps().get(i).getFrames());
            Assertions.assertEquals(first.getSteps().get(i).getFault(), second.getSteps().get(i).getFault());
        }
    }

    private static int countEvictions(SimulationPageVO vo) {
        int count = 0;
        for (SimulationPageVO.PageStep step : vo.getSteps()) {
            if (step.getEvicted() != null) {
                count++;
            }
        }
        return count;
    }

    // endregion
}
