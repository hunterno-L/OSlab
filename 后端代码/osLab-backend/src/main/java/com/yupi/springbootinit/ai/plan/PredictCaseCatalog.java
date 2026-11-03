package com.yupi.springbootinit.ai.plan;

import com.yupi.springbootinit.learning.PageReplacer;
import com.yupi.springbootinit.model.vo.learning.SimulationPageVO;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工核验的页面置换预测诊断案例（计划 §5.4）。
 *
 * <p>这些是<b>新增</b>的诊断活动，不冒充已有题目；答案一律由服务端
 * {@link PageReplacer} 现场计算，目录里不写死结果，因此不存在"目录与计算器不一致"的风险。
 *
 * <p>提交前只下发允许看到的前缀：待预测步之后的访问序列与任何已解出的步骤都不出现，
 * 避免完整轨迹提前暴露答案（计划 §4：完整轨迹可能提前暴露答案）。
 *
 * <p>案例 1 即计划正文的例子：三页框访问 1,2,3,1,4，FIFO 换出 1、LRU 换出 2，
 * 均为 4 次缺页、1 次换出——用于区分"混淆 FIFO 与 LRU"和"漏掉命中后的更新"。
 */
@Component
public class PredictCaseCatalog {

    /** 预测目标：换出页 / 缺页次数 / 换出次数。 */
    public enum Metric {
        VICTIM, FAULTS, EVICTIONS
    }

    @Data
    public static class PredictCase {
        private String code;
        private String title;
        private String algorithm;
        private int frames;
        private List<Integer> sequence = new ArrayList<>();
        /** 待预测的步序号；负数表示对整条序列做聚合预测（缺页/换出次数）。 */
        private int predictStepIndex = -1;
        private Metric metric = Metric.VICTIM;
        private String prompt;
        private String skillCode;
        private String nodeCode;
        /** 该案例能区分的误区代码。 */
        private List<String> discriminates = new ArrayList<>();
        private int estimatedMinutes = 3;
    }

    @Data
    public static class Truth {
        /** 正确答案（换出页号或缺页/换出次数）。 */
        private int value;
        private int faults;
        private int evictions;
        /** 提交前允许看到的访问序列前缀。 */
        private List<Integer> visibleSequence = new ArrayList<>();
        /** 提交前允许看到的已解出步骤（不含待预测步及其之后）。 */
        private List<StepView> visibleSteps = new ArrayList<>();
        /** 提交后才下发的解释。 */
        private String explanation;
    }

    @Data
    public static class StepView {
        private Integer step;
        private Integer page;
        private Boolean fault;
        private Integer evicted;
        private List<Integer> frames = new ArrayList<>();
    }

    private final Map<String, PredictCase> cases = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        register(newCase("page.lru-vs-fifo.victim",
                "预测 LRU 的换出页（命中后是否更新顺序）",
                "LRU", 3, Arrays.asList(1, 2, 3, 1, 4), 4, Metric.VICTIM,
                "三个物理块，访问序列 1,2,3,1,4。按 LRU，访问 4 发生缺页时应换出哪一页？",
                "os.memory.lru", "os.memory.lru",
                Arrays.asList("lru.hit-no-update", "fifo.lru-confusion")));
        register(newCase("page.lru.hit-update-order",
                "连续命中后预测 LRU 换出页",
                "LRU", 3, Arrays.asList(1, 2, 3, 2, 1, 4), 5, Metric.VICTIM,
                "三个物理块，访问序列 1,2,3,2,1,4。按 LRU，访问 4 发生缺页时应换出哪一页？",
                "os.memory.lru", "os.memory.lru",
                Collections.singletonList("lru.hit-no-update")));
        register(newCase("page.count.faults-vs-evictions",
                "区分缺页次数与换出次数",
                "FIFO", 3, Arrays.asList(1, 2, 3, 4, 1, 2, 5), -1, Metric.FAULTS,
                "三个物理块，访问序列 1,2,3,4,1,2,5。按 FIFO，整个过程一共发生多少次缺页？",
                "os.memory.paging", "os.memory.replacement",
                Collections.singletonList("fault.vs-eviction")));
    }

    private void register(PredictCase predictCase) {
        cases.put(predictCase.getCode(), predictCase);
    }

    private static PredictCase newCase(String code, String title, String algorithm, int frames,
                                    List<Integer> sequence, int predictStepIndex, Metric metric,
                                    String prompt, String skillCode, String nodeCode,
                                    List<String> discriminates) {
        PredictCase predictCase = new PredictCase();
        predictCase.setCode(code);
        predictCase.setTitle(title);
        predictCase.setAlgorithm(algorithm);
        predictCase.setFrames(frames);
        predictCase.setSequence(new ArrayList<>(sequence));
        predictCase.setPredictStepIndex(predictStepIndex);
        predictCase.setMetric(metric);
        predictCase.setPrompt(prompt);
        predictCase.setSkillCode(skillCode);
        predictCase.setNodeCode(nodeCode);
        predictCase.setDiscriminates(new ArrayList<>(discriminates));
        return predictCase;
    }

    public PredictCase find(String code) {
        return StringUtils.isBlank(code) ? null : cases.get(code);
    }

    public List<PredictCase> all() {
        return new ArrayList<>(cases.values());
    }

    /** 能区分给定误区代码的案例。 */
    public List<PredictCase> discriminating(List<String> hypothesisCodes) {
        List<PredictCase> out = new ArrayList<>();
        if (hypothesisCodes == null || hypothesisCodes.isEmpty()) {
            return out;
        }
        for (PredictCase predictCase : cases.values()) {
            for (String code : hypothesisCodes) {
                if (predictCase.getDiscriminates().contains(code)) {
                    out.add(predictCase);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * 用服务端计算器求真值与"允许看到的前缀"。
     *
     * <p>目录不保存答案：每次都现算，保证与 {@code check_page_trace} 工具、
     * 判分与解释使用同一份确定性结果。
     */
    public Truth truth(PredictCase predictCase) {
        SimulationPageVO computed = PageReplacer.replace(predictCase.getAlgorithm(),
                predictCase.getFrames(), predictCase.getSequence());
        List<SimulationPageVO.PageStep> steps = computed.getSteps();
        int evictions = 0;
        for (SimulationPageVO.PageStep step : steps) {
            if (step.getEvicted() != null) {
                evictions++;
            }
        }
        Truth truth = new Truth();
        truth.setFaults(computed.getPageFaults() == null ? 0 : computed.getPageFaults());
        truth.setEvictions(evictions);
        if (predictCase.getPredictStepIndex() >= 0) {
            int index = Math.min(predictCase.getPredictStepIndex(), steps.size() - 1);
            SimulationPageVO.PageStep predicted = steps.get(index);
            truth.setValue(predicted.getEvicted() == null ? -1 : predicted.getEvicted());
            truth.setVisibleSequence(new ArrayList<>(predictCase.getSequence().subList(0, index + 1)));
            for (int i = 0; i < index; i++) {
                truth.getVisibleSteps().add(view(steps.get(i)));
            }
            truth.setExplanation("按 " + predictCase.getAlgorithm() + "，访问 " + predicted.getPage()
                    + " 时缺页并换出 " + predicted.getEvicted()
                    + "；整条序列缺页 " + truth.getFaults() + " 次、换出 " + evictions + " 次。");
        } else {
            truth.setValue(predictCase.getMetric() == Metric.EVICTIONS ? evictions : truth.getFaults());
            truth.setVisibleSequence(new ArrayList<>(predictCase.getSequence()));
            // 聚合预测不下发任何已解出步骤，否则逐步轨迹会直接暴露答案
            truth.setExplanation("按 " + predictCase.getAlgorithm() + "，整条序列缺页 " + truth.getFaults()
                    + " 次、换出 " + evictions + " 次；物理块未满时装入新页不算换出。");
        }
        return truth;
    }

    private static StepView view(SimulationPageVO.PageStep step) {
        StepView view = new StepView();
        view.setStep(step.getStep());
        view.setPage(step.getPage());
        view.setFault(step.getFault());
        view.setEvicted(step.getEvicted());
        view.setFrames(step.getFrames() == null ? new ArrayList<>() : new ArrayList<>(step.getFrames()));
        return view;
    }
}
