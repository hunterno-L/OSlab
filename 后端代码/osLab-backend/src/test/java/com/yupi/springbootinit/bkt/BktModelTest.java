package com.yupi.springbootinit.bkt;

import com.yupi.springbootinit.model.enums.learning.MasteryStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * BKT 纯函数测试（契约 §3.3：同一已知输入可复算后验；参数/公式可检查；5 参签名）
 */
class BktModelTest {

    private static final double DELTA = 1e-9;
    private static final double P_LEARN = BktModel.DEFAULT_P_LEARN;
    private static final double P_GUESS = BktModel.DEFAULT_P_GUESS;
    private static final double P_SLIP = BktModel.DEFAULT_P_SLIP;

    @Test
    void updateCorrect_matchesHandComputation() {
        // pKnown=0.1, pGuess=0.2, pSlip=0.1
        // posterior = 0.1*0.9 / (0.1*0.9 + 0.9*0.2) = 0.09/0.27 = 1/3
        // pKnown' = 1/3 + (2/3)*0.1 = 0.4
        double result = BktModel.updateCorrect(0.1, P_LEARN, P_GUESS, P_SLIP);
        Assertions.assertEquals(1.0 / 3.0 + (1.0 - 1.0 / 3.0) * P_LEARN, result, DELTA);
        Assertions.assertEquals(0.4, result, DELTA);
    }

    @Test
    void updateWrong_matchesHandComputation() {
        // posterior = 0.4*0.1 / (0.4*0.1 + 0.6*0.8) = 0.04/0.52
        double posterior = 0.4 * P_SLIP / (0.4 * P_SLIP + 0.6 * (1 - P_GUESS));
        double result = BktModel.updateWrong(0.4, P_LEARN, P_GUESS, P_SLIP);
        Assertions.assertEquals(posterior + (1 - posterior) * P_LEARN, result, DELTA);
    }

    @Test
    void pLearn_parameterShiftsResult() {
        // 更大的 pLearn 让答错后的后验更高（学习转移补偿）
        double withSmallLearn = BktModel.update(0.3, false, 0.0, P_GUESS, P_SLIP);
        double withLargeLearn = BktModel.update(0.3, false, 0.5, P_GUESS, P_SLIP);
        Assertions.assertTrue(withLargeLearn > withSmallLearn,
                "pLearn 越大，更新后 pKnown 应越高");
    }

    @Test
    void repeatedCorrectConvergesTowardOne() {
        double p = 0.1;
        for (int i = 0; i < 20; i++) {
            p = BktModel.updateCorrect(p, P_LEARN, P_GUESS, P_SLIP);
        }
        Assertions.assertTrue(p > 0.95, "连续答对 20 次后验应趋近 1，实际 " + p);
    }

    @Test
    void repeatedWrongDecreasesPosterior() {
        double p = 0.8;
        p = BktModel.updateWrong(p, P_LEARN, P_GUESS, P_SLIP);
        Assertions.assertTrue(p < 0.8, "答错后后验应下降，实际 " + p);
    }

    @Test
    void update_isMonotonicAndBounded() {
        double p = BktModel.update(0.5, true, P_LEARN, P_GUESS, P_SLIP);
        Assertions.assertTrue(p > 0.5 && p <= 1.0);
        p = BktModel.update(p, false, P_LEARN, P_GUESS, P_SLIP);
        Assertions.assertTrue(p >= 0.0 && p < 1.0);
    }

    @Test
    void boundaryInputsAreClamped() {
        // pKnown 越界钳制到 [0,1]；答对后应用学习转移：1.0 保持 1.0
        Assertions.assertEquals(1.0, BktModel.updateCorrect(1.5, P_LEARN, P_GUESS, P_SLIP), DELTA);
        // pKnown=-1 → 0.0，答错后验 0，学习转移 → pLearn
        double wrongFromClamped = BktModel.updateWrong(-1.0, P_LEARN, P_GUESS, P_SLIP);
        Assertions.assertTrue(wrongFromClamped >= 0.0 && wrongFromClamped <= 1.0,
                "结果必须落在 [0,1]，实际 " + wrongFromClamped);
        Assertions.assertFalse(Double.isNaN(BktModel.update(0.5, true, P_LEARN, P_GUESS, P_SLIP)));
    }

    @Test
    void zeroDenominatorGuard_keepsValueStable() {
        // pGuess=0 且 pSlip=1 时答对观测分母为 0，应退化为不更新
        double stable = BktModel.update(0.42, true, P_LEARN, 0.0, 1.0);
        Assertions.assertEquals(0.42, stable, DELTA);
    }

    @Test
    void sameInputSameOutput_deterministic() {
        Assertions.assertEquals(BktModel.update(0.3, true, P_LEARN, P_GUESS, P_SLIP),
                BktModel.update(0.3, true, P_LEARN, P_GUESS, P_SLIP), 0.0);
    }

    @Test
    void confidence_scalesWithDistinctSources() {
        Assertions.assertEquals(0.0, BktModel.confidence(0), DELTA);
        Assertions.assertEquals(0.25, BktModel.confidence(1), DELTA);
        Assertions.assertEquals(1.0, BktModel.confidence(4), DELTA);
        Assertions.assertEquals(1.0, BktModel.confidence(10), DELTA);
    }

    @Test
    void masteryStatus_derivation_perContractThresholds() {
        // 契约：无证据 GRAY；pKnown<0.35 RED；<0.70 YELLOW；>=0.70 且两类证据齐全 GREEN
        Assertions.assertEquals(MasteryStatusEnum.GRAY, MasteryStatusEnum.derive(0.5, 0, 0));
        Assertions.assertEquals(MasteryStatusEnum.GRAY, MasteryStatusEnum.derive(null, 0, 0));
        Assertions.assertEquals(MasteryStatusEnum.RED, MasteryStatusEnum.derive(0.34, 2, 0));
        Assertions.assertEquals(MasteryStatusEnum.YELLOW, MasteryStatusEnum.derive(0.35, 2, 0));
        Assertions.assertEquals(MasteryStatusEnum.YELLOW, MasteryStatusEnum.derive(0.69, 1, 1));
        // 达到 GREEN 阈值但只有一类证据：仍是 YELLOW（GREEN 需两类证据）
        Assertions.assertEquals(MasteryStatusEnum.YELLOW, MasteryStatusEnum.derive(0.9, 3, 0));
        Assertions.assertEquals(MasteryStatusEnum.YELLOW, MasteryStatusEnum.derive(0.9, 0, 3));
        Assertions.assertEquals(MasteryStatusEnum.GREEN, MasteryStatusEnum.derive(0.85, 3, 3));
        Assertions.assertEquals(MasteryStatusEnum.GREEN, MasteryStatusEnum.derive(0.70, 1, 1));
    }
}
