package com.yupi.springbootinit.ai.plan;

import com.yupi.springbootinit.learning.PageReplacer;
import com.yupi.springbootinit.model.vo.learning.SimulationPageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预测诊断案例目录的回归（计划 §5.4 / 任务书 §4）。
 *
 * <p>两条硬性要求：
 * <ol>
 *   <li>目录不保存答案，真值一律由服务端 {@link PageReplacer} 现算，
 *       因此"目录与计算器不一致"在结构上不可能发生；</li>
 *   <li>提交前只下发允许看到的前缀——待预测步的换出页、以及聚合预测的逐步轨迹，
 *       都不能出现在 {@code Truth} 的可见字段里。</li>
 * </ol>
 */
class PredictCaseCatalogTest {

    private PredictCaseCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new PredictCaseCatalog();
        catalog.init();
    }

    @Test
    void planExampleIsReproducedExactlyByTheServerSideCalculator() {
        // 计划正文的例子：三页框访问 1,2,3,1,4，FIFO 换出 1、LRU 换出 2，均为 4 次缺页、1 次换出
        PredictCaseCatalog.PredictCase lruCase = catalog.find("page.lru-vs-fifo.victim");
        assertNotNull(lruCase);
        PredictCaseCatalog.Truth lru = catalog.truth(lruCase);
        assertEquals(2, lru.getValue(), "LRU 在访问 4 时应换出 2（命中后更新了顺序）");
        assertEquals(4, lru.getFaults());
        assertEquals(1, lru.getEvictions());

        SimulationPageVO fifo = PageReplacer.replace("FIFO", 3, Arrays.asList(1, 2, 3, 1, 4));
        assertEquals(4, fifo.getPageFaults(), "FIFO 缺页数与 LRU 相同");
        assertEquals(1, fifo.getSteps().get(4).getEvicted(), "FIFO 换出的是 1，与 LRU 的 2 形成区分点");
    }

    @Test
    void truthIsAlwaysRecomputedAndNeverStoredInTheCatalog() {
        for (PredictCaseCatalog.PredictCase predictCase : catalog.all()) {
            PredictCaseCatalog.Truth truth = catalog.truth(predictCase);
            SimulationPageVO computed = PageReplacer.replace(predictCase.getAlgorithm(),
                    predictCase.getFrames(), predictCase.getSequence());
            assertEquals(computed.getPageFaults(), truth.getFaults(), predictCase.getCode());
            int evictions = 0;
            for (SimulationPageVO.PageStep step : computed.getSteps()) {
                if (step.getEvicted() != null) {
                    evictions++;
                }
            }
            assertEquals(evictions, truth.getEvictions(), predictCase.getCode());
            if (predictCase.getPredictStepIndex() >= 0) {
                assertEquals(computed.getSteps().get(predictCase.getPredictStepIndex()).getEvicted(),
                        truth.getValue(), predictCase.getCode() + " 的换出页必须来自计算器");
            } else {
                assertEquals(truth.getFaults(), truth.getValue(),
                        predictCase.getCode() + " 是缺页计数预测");
            }
        }
    }

    @Test
    void visiblePrefixNeverContainsThePredictedVictim() {
        for (PredictCaseCatalog.PredictCase predictCase : catalog.all()) {
            PredictCaseCatalog.Truth truth = catalog.truth(predictCase);
            int predictedIndex = predictCase.getPredictStepIndex();
            if (predictedIndex < 0) {
                // 聚合预测：逐步轨迹会直接给出缺页次数，因此一个步骤都不能下发
                assertTrue(truth.getVisibleSteps().isEmpty(),
                        predictCase.getCode() + " 不得下发任何已解出步骤");
                continue;
            }
            assertTrue(truth.getVisibleSteps().size() < predictedIndex + 1,
                    predictCase.getCode() + " 不得下发待预测步本身");
            for (PredictCaseCatalog.StepView step : truth.getVisibleSteps()) {
                assertTrue(step.getStep() <= predictedIndex,
                        predictCase.getCode() + " 可见步骤越过了待预测步");
            }
            // 可见访问序列最多到待预测的那一次访问为止
            assertEquals(predictedIndex + 1, truth.getVisibleSequence().size(),
                    predictCase.getCode() + " 可见序列长度应止于待预测步");
            assertEquals(predictCase.getSequence().subList(0, predictedIndex + 1),
                    truth.getVisibleSequence());
        }
    }

    @Test
    void earlierStepsDoNotGiveAwayTheVictimOfThePredictedStep() {
        // 两个 LRU 案例里，待预测步之前都没有发生换出，因此可见轨迹不含任何"换出几"的提示
        for (String code : Arrays.asList("page.lru-vs-fifo.victim", "page.lru.hit-update-order")) {
            PredictCaseCatalog.Truth truth = catalog.truth(catalog.find(code));
            for (PredictCaseCatalog.StepView step : truth.getVisibleSteps()) {
                assertNull(step.getEvicted(), code + " 的可见步骤不应出现换出页");
            }
        }
    }

    @Test
    void consecutiveHitCaseStillDiscriminatesTheHitUpdateMisconception() {
        PredictCaseCatalog.PredictCase predictCase = catalog.find("page.lru.hit-update-order");
        assertEquals("LRU", predictCase.getAlgorithm());
        assertEquals(Arrays.asList(1, 2, 3, 2, 1, 4), predictCase.getSequence());
        // 正确做法（命中即更新顺序）换出 3；"命中后不更新"的误区做法会换出 1——两者不同才构成区分点
        PredictCaseCatalog.Truth truth = catalog.truth(predictCase);
        assertEquals(3, truth.getValue(), "连续命中后 LRU 应换出最久未用的 3");
        assertNotEquals(1, truth.getValue(), "若换出 1 说明命中顺序没有被更新，正是本案例要识别的误区");
        SimulationPageVO naive = PageReplacer.replace("FIFO", 3, predictCase.getSequence());
        assertNotEquals(truth.getValue(), naive.getSteps().get(5).getEvicted(),
                "LRU 与 FIFO 在该序列上的换出页必须不同，否则案例无法区分两种算法");
        assertTrue(predictCase.getDiscriminates().contains("lru.hit-no-update"));
    }

    @Test
    void aggregateCaseSeparatesFaultsFromEvictions() {
        PredictCaseCatalog.PredictCase predictCase = catalog.find("page.count.faults-vs-evictions");
        assertEquals(PredictCaseCatalog.Metric.FAULTS, predictCase.getMetric());
        PredictCaseCatalog.Truth truth = catalog.truth(predictCase);
        assertEquals(7, truth.getFaults());
        assertEquals(4, truth.getEvictions());
        assertNotEquals(truth.getEvictions(), truth.getValue(),
                "缺页次数与换出次数必须不同，否则该案例区分不了这一误区");
        assertTrue(predictCase.getDiscriminates().contains("fault.vs-eviction"));
    }

    @Test
    void discriminatingCasesAreFoundByMisconceptionCode() {
        List<PredictCaseCatalog.PredictCase> cases =
                catalog.discriminating(Arrays.asList("lru.hit-no-update"));
        assertEquals(2, cases.size(), "两个 LRU 案例都应能区分'命中后不更新顺序'");
        assertTrue(catalog.discriminating(Arrays.asList("fault.vs-eviction")).stream()
                .anyMatch(item -> "page.count.faults-vs-evictions".equals(item.getCode())));
        assertTrue(catalog.discriminating(null).isEmpty());
        assertTrue(catalog.discriminating(Arrays.asList("no.such.misconception")).isEmpty());
    }

    @Test
    void everyCaseCarriesPromptSkillNodeAndEstimate() {
        for (PredictCaseCatalog.PredictCase predictCase : catalog.all()) {
            assertNotNull(predictCase.getPrompt(), predictCase.getCode() + " 缺少题面");
            assertNotNull(predictCase.getSkillCode(), predictCase.getCode() + " 缺少技能编码");
            assertNotNull(predictCase.getNodeCode(), predictCase.getCode() + " 缺少知识点编码");
            assertFalse(predictCase.getDiscriminates().isEmpty(),
                    predictCase.getCode() + " 必须声明能区分哪些误区");
            assertTrue(predictCase.getEstimatedMinutes() > 0);
            assertTrue(predictCase.getFrames() > 0);
            assertFalse(predictCase.getSequence().isEmpty());
        }
    }

    @Test
    void unknownCodeIsNullRatherThanAnInventedCase() {
        assertNull(catalog.find("page.no.such.case"));
        assertNull(catalog.find(null));
        assertNull(catalog.find("  "));
    }

    @Test
    void explanationIsOnlyProducedAfterSubmissionAndMentionsRealNumbers() {
        PredictCaseCatalog.Truth truth = catalog.truth(catalog.find("page.lru-vs-fifo.victim"));
        assertTrue(truth.getExplanation().contains("LRU"));
        assertTrue(truth.getExplanation().contains("2"), "解释要指出真正被换出的页");
        assertTrue(truth.getExplanation().contains("4"), "解释要给出缺页次数");
    }
}
