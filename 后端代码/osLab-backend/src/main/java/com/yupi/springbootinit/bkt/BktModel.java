package com.yupi.springbootinit.bkt;

/**
 * BKT（Bayesian Knowledge Tracing）纯函数模型 —— 契约 §3.3。
 *
 * <p>所有计算均为无状态纯函数，给定相同输入必然得到相同输出。
 * 参数为课程通用默认值（pInit=0.10、pLearn=0.10、pGuess=0.20、pSlip=0.10），
 * 未做真实教学数据校准，仅用于“基于证据的后验更新”。
 *
 * <p>更新公式（一次独立观测后）：
 * <pre>
 * 答对：posterior = pKnown*(1-pSlip) / [pKnown*(1-pSlip) + (1-pKnown)*pGuess]
 * 答错：posterior = pKnown*pSlip   / [pKnown*pSlip + (1-pKnown)*(1-pGuess)]
 * 下一步：pKnown'  = posterior + (1-posterior)*pLearn
 * </pre>
 */
public final class BktModel {

    /** 默认初始掌握概率 */
    public static final double DEFAULT_P_INIT = 0.1;
    /** 默认学习转移概率 */
    public static final double DEFAULT_P_LEARN = 0.1;
    /** 默认猜对概率 */
    public static final double DEFAULT_P_GUESS = 0.2;
    /** 默认失误概率 */
    public static final double DEFAULT_P_SLIP = 0.1;

    private static final double EPS = 1e-12;

    private BktModel() {
    }

    /**
     * 答对观测后的后验更新（含学习转移）
     */
    public static double updateCorrect(double pKnown, double pLearn, double pGuess, double pSlip) {
        double numerator = pKnown * (1.0 - clamp01(pSlip));
        double denominator = numerator + (1.0 - clamp01(pKnown)) * clamp01(pGuess);
        if (denominator < EPS) {
            // 零分母保护：退化为不更新
            return clamp01(pKnown);
        }
        return applyLearn(numerator / denominator, pLearn);
    }

    /**
     * 答错观测后的后验更新（含学习转移）
     */
    public static double updateWrong(double pKnown, double pLearn, double pGuess, double pSlip) {
        double numerator = clamp01(pKnown) * clamp01(pSlip);
        double denominator = numerator + (1.0 - clamp01(pKnown)) * (1.0 - clamp01(pGuess));
        if (denominator < EPS) {
            return clamp01(pKnown);
        }
        return applyLearn(numerator / denominator, pLearn);
    }

    /**
     * 统一入口：按观测结果更新后验（契约 §3.3 签名）
     *
     * @param pKnown 观测前掌握概率
     * @param correct 观测是否通过
     * @param pLearn  学习转移概率
     * @param pGuess  猜对概率
     * @param pSlip   失误概率
     */
    public static double update(double pKnown, boolean correct,
                                double pLearn, double pGuess, double pSlip) {
        return correct ? updateCorrect(pKnown, pLearn, pGuess, pSlip)
                : updateWrong(pKnown, pLearn, pGuess, pSlip);
    }

    /**
     * 学习转移：pKnown' = posterior + (1-posterior)*pLearn
     */
    public static double applyLearn(double posterior, double pLearn) {
        double p = clamp01(posterior);
        return clamp01(p + (1.0 - p) * clamp01(pLearn));
    }

    /**
     * 可信度估算：证据量与来源种类越多越可信，上限 1.0。
     * 单一来源 0.25，两类来源 0.5，三类 0.75，四类及以上 1.0。
     */
    public static double confidence(int distinctSources) {
        if (distinctSources <= 0) {
            return 0.0;
        }
        return Math.min(1.0, distinctSources * 0.25);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
