package com.yupi.springbootinit.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.learning.CpuScheduler;
import com.yupi.springbootinit.learning.PageReplacer;
import com.yupi.springbootinit.model.dto.learning.SimulationCpuRequest;
import com.yupi.springbootinit.model.dto.learning.SimulationPageRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.SimulationCpuVO;
import com.yupi.springbootinit.model.vo.learning.SimulationPageVO;
import com.yupi.springbootinit.service.LearningEventService;
import com.yupi.springbootinit.service.LearningSimulationService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模拟器服务实现（WP6）
 * 前端上报的“正确”不被采信：预测一律由服务端重算真值后判分。
 */
@Service
public class LearningSimulationServiceImpl implements LearningSimulationService {

    private static final Set<String> CPU_ALGORITHMS = new HashSet<>(List.of("FCFS", "SJF", "RR"));
    private static final Set<String> PAGE_ALGORITHMS = new HashSet<>(List.of("FIFO", "LRU", "OPT"));
    private static final int MAX_PROCESSES = 10;
    private static final int MAX_SEQUENCE = 50;

    @Resource
    private LearningEventService learningEventService;

    @Override
    public SimulationCpuVO runCpu(SimulationCpuRequest request, User user) {
        validateCpu(request);
        List<SimulationCpuRequest.CpuProcessInput> processes = request.getProcesses();
        // 归一化后再计算摘要，保证同配置幂等
        String config = request.getAlgorithm() + "|" + StringUtils.defaultString(request.getTimeSlice() == null ? null : String.valueOf(request.getTimeSlice()))
                + "|" + JSONUtil.toJsonStr(processes);
        SimulationCpuVO vo = CpuScheduler.schedule(request.getAlgorithm(), request.getTimeSlice(), processes);
        Integer predictIndex = request.getPredictIndex();
        if (predictIndex != null && predictIndex >= 0) {
            SimulationCpuVO.PredictionResult prediction = new SimulationCpuVO.PredictionResult();
            String expected = CpuScheduler.processAt(vo.getGantt(), predictIndex);
            prediction.setExpectedProcess(expected);
            prediction.setPredictedProcess(request.getPredictedProcess());
            prediction.setCorrect(expected != null && expected.equals(StringUtils.trimToEmpty(request.getPredictedProcess())));
            if (StringUtils.isNotBlank(request.getNodeName())) {
                String simKey = "cpu:" + DigestUtil.md5Hex(config + "|" + predictIndex);
                String evidence = JSONUtil.toJsonStr(new EvidenceBean(
                        "CPU调度预测", request.getAlgorithm(),
                        "预测时刻 " + predictIndex + " → 应运行 " + expected,
                        prediction.getCorrect() ? "答对" : "答错"));
                prediction.setEventId(learningEventService.recordSimulationEvent(
                        user.getId(), request.getNodeName(), simKey, prediction.getCorrect(), evidence));
            }
            vo.setPrediction(prediction);
        }
        return vo;
    }

    @Override
    public SimulationPageVO runPage(SimulationPageRequest request, User user) {
        validatePage(request);
        String config = request.getAlgorithm() + "|" + request.getFrames() + "|" + JSONUtil.toJsonStr(request.getAccessSequence());
        SimulationPageVO vo = PageReplacer.replace(request.getAlgorithm(), request.getFrames(), request.getAccessSequence());
        Integer predictStep = request.getPredictStep();
        if (predictStep != null && predictStep >= 0) {
            ThrowUtils.throwIf(predictStep >= vo.getSteps().size(), ErrorCode.PARAMS_ERROR, "预测步超出访问序列范围");
            SimulationPageVO.PageStep actual = vo.getSteps().get(predictStep);
            SimulationPageVO.PredictionResult prediction = new SimulationPageVO.PredictionResult();
            prediction.setActualFault(actual.getFault());
            prediction.setActualEvicted(actual.getEvicted());
            // 缺页预测：前端上报仅供参考，以服务端真值判分
            prediction.setFaultCorrect(Boolean.TRUE.equals(actual.getFault()) == Boolean.TRUE.equals(request.getPredictedFault()));
            // 淘汰页预测：该步无淘汰时，学生未传淘汰页视为正确
            boolean evictedCorrect;
            if (actual.getEvicted() == null) {
                evictedCorrect = request.getPredictedEvicted() == null;
            } else {
                evictedCorrect = actual.getEvicted().equals(request.getPredictedEvicted());
            }
            prediction.setEvictedCorrect(evictedCorrect);
            // OPT 仅用于算法结果对比：预测可判分但不写学习事件（Phase 5 §9.2）
            if (StringUtils.isNotBlank(request.getNodeName()) && !"OPT".equals(request.getAlgorithm())) {
                String simKey = "page:" + DigestUtil.md5Hex(config + "|" + predictStep);
                String evidence = JSONUtil.toJsonStr(new EvidenceBean(
                        "页面置换预测", request.getAlgorithm(),
                        "第 " + predictStep + " 步访问 " + actual.getPage()
                                + " → " + (actual.getFault() ? "缺页" : "命中")
                                + (actual.getEvicted() == null ? "" : "，淘汰 " + actual.getEvicted()),
                        prediction.getFaultCorrect() && prediction.getEvictedCorrect() ? "答对" : "答错"));
                prediction.setEventId(learningEventService.recordSimulationEvent(
                        user.getId(), request.getNodeName(), simKey,
                        prediction.getFaultCorrect() && prediction.getEvictedCorrect(), evidence));
            }
            vo.setPrediction(prediction);
        }
        return vo;
    }

    // region 校验

    private void validateCpu(SimulationCpuRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!CPU_ALGORITHMS.contains(request.getAlgorithm()), ErrorCode.PARAMS_ERROR,
                "算法仅支持 FCFS/SJF/RR");
        List<SimulationCpuRequest.CpuProcessInput> processes = request.getProcesses();
        ThrowUtils.throwIf(CollectionUtils.isEmpty(processes), ErrorCode.PARAMS_ERROR, "进程列表不能为空");
        ThrowUtils.throwIf(processes.size() > MAX_PROCESSES, ErrorCode.PARAMS_ERROR, "进程数最多 " + MAX_PROCESSES);
        Set<String> names = new HashSet<>();
        for (SimulationCpuRequest.CpuProcessInput p : processes) {
            ThrowUtils.throwIf(p == null || StringUtils.isBlank(p.getName()), ErrorCode.PARAMS_ERROR, "进程名不能为空");
            ThrowUtils.throwIf(!names.add(p.getName()), ErrorCode.PARAMS_ERROR, "进程名重复：" + p.getName());
            ThrowUtils.throwIf(p.getArrivalTime() == null || p.getArrivalTime() < 0,
                    ErrorCode.PARAMS_ERROR, "到达时间必须为非负整数");
            ThrowUtils.throwIf(p.getBurstTime() == null || p.getBurstTime() <= 0,
                    ErrorCode.PARAMS_ERROR, "运行时间必须大于 0");
            ThrowUtils.throwIf(p.getBurstTime() > 100, ErrorCode.PARAMS_ERROR, "单进程运行时间不能超过 100");
        }
        if ("RR".equals(request.getAlgorithm())) {
            ThrowUtils.throwIf(request.getTimeSlice() == null || request.getTimeSlice() <= 0,
                    ErrorCode.PARAMS_ERROR, "RR 算法必须提供大于 0 的时间片");
        }
        if (request.getPredictIndex() != null && request.getPredictIndex() >= 0) {
            ThrowUtils.throwIf(StringUtils.isBlank(request.getPredictedProcess()),
                    ErrorCode.PARAMS_ERROR, "预测模式必须提交预测的进程名");
        }
    }

    private void validatePage(SimulationPageRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!PAGE_ALGORITHMS.contains(request.getAlgorithm()), ErrorCode.PARAMS_ERROR,
                "算法仅支持 FIFO/LRU/OPT");
        ThrowUtils.throwIf(request.getFrames() == null || request.getFrames() < 1 || request.getFrames() > 10,
                ErrorCode.PARAMS_ERROR, "物理块数取值 1-10");
        List<Integer> sequence = request.getAccessSequence();
        ThrowUtils.throwIf(CollectionUtils.isEmpty(sequence), ErrorCode.PARAMS_ERROR, "访问序列不能为空");
        ThrowUtils.throwIf(sequence.size() > MAX_SEQUENCE, ErrorCode.PARAMS_ERROR, "访问序列最多 " + MAX_SEQUENCE + " 步");
        for (Integer page : sequence) {
            ThrowUtils.throwIf(page == null || page < 0 || page > 9999, ErrorCode.PARAMS_ERROR, "页号必须为 0-9999");
        }
    }

    // endregion

    /**
     * 证据 JSON 结构
     */
    public static class EvidenceBean {
        public String source;
        public String algorithm;
        public String detail;
        public String resultText;

        public EvidenceBean(String source, String algorithm, String detail, String resultText) {
            this.source = source;
            this.algorithm = algorithm;
            this.detail = detail;
            this.resultText = resultText;
        }
    }
}
