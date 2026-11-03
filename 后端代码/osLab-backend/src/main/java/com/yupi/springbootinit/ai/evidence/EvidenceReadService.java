package com.yupi.springbootinit.ai.evidence;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本人学习证据读取（工具 {@code read_learning_evidence}）。
 *
 * <p>只读、且只读当前用户：查询恒定带 userId 条件，他人事件不可能进入证据包。
 * 返回内容包含作答结果、帮助情况、失败类型与是否独立观测，
 * 供导师区分"看过答案的表现"与"能独立完成新题"。
 *
 * <p>系统错误与编译错误按诊断事件返回，明确标记 {@code diagnostic=true}，
 * 不得被归为知识薄弱，也不参与薄弱知识点归纳。
 */
@Slf4j
@Service
public class EvidenceReadService {

    private static final int MAX_RECORDS = 30;

    @Resource
    private LearningEventMapper learningEventMapper;

    @Data
    public static class EvidenceRecord implements Serializable {
        private String eventId;
        private String nodeCode;
        private String nodeName;
        private String eventType;
        private String result;
        /** 是否被计为独立掌握观测（带提示/看答案/重复作答均为 false）。 */
        private boolean firstObservation;
        /** NONE / HINT / ANSWER_REVEALED / UNKNOWN */
        private String helpLevel;
        private String selfReportedHelp;
        private boolean diagnostic;
        private String occurredAt;
        private String sourceUnitCode;
        private String sourceActivityCode;
        private String note;
    }

    @Data
    public static class EvidenceSummary implements Serializable {
        private List<EvidenceRecord> records = new ArrayList<>();
        private int failures;
        private int diagnostics;
        private int passes;
        private int helpedAttempts;
        /** 有真实失败观测的知识点；诊断事件与带提示作答不进入该列表。 */
        private List<String> weakNodeCodes = new ArrayList<>();
        private String narrative;
    }

    /**
     * @param nodeCodes 为空表示不限定知识点（仍严格限定本人 + 条数上限）
     */
    public EvidenceSummary read(Long userId, List<String> nodeCodes, int limit) {
        EvidenceSummary summary = new EvidenceSummary();
        if (userId == null) {
            summary.setNarrative("未登录，无法读取学习证据");
            return summary;
        }
        int cap = limit <= 0 ? MAX_RECORDS : Math.min(limit, MAX_RECORDS);
        Set<String> scope = new LinkedHashSet<>();
        if (nodeCodes != null) {
            for (String code : nodeCodes) {
                if (StringUtils.isNotBlank(code)) {
                    scope.add(code);
                }
            }
        }
        List<LearningEvent> events;
        try {
            events = learningEventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                    .eq(LearningEvent::getUserId, userId)
                    // nodeName 是历史存储键、nodeCode 是稳定编码，两者都要匹配
                    .and(!scope.isEmpty(), wrapper -> wrapper
                            .in(LearningEvent::getNodeCode, scope).or()
                            .in(LearningEvent::getNodeName, scope))
                    .orderByDesc(LearningEvent::getCreateTime)
                    .orderByDesc(LearningEvent::getId)
                    .last("limit " + cap * 3L));
        } catch (RuntimeException e) {
            log.warn("[ai-evidence] read failed user={}: {}", userId, e.getClass().getSimpleName());
            summary.setNarrative("学习证据暂不可读（" + e.getClass().getSimpleName() + "）");
            return summary;
        }
        Set<String> weak = new LinkedHashSet<>();
        for (LearningEvent event : events) {
            if (summary.getRecords().size() >= cap) {
                break;
            }
            EvidenceRecord record = toRecord(event);
            summary.getRecords().add(record);
            LearningResultEnum result = LearningResultEnum.parse(event.getResult());
            if (record.isDiagnostic()) {
                summary.setDiagnostics(summary.getDiagnostics() + 1);
            } else if (result == LearningResultEnum.FAIL) {
                summary.setFailures(summary.getFailures() + 1);
                // 只有"独立观测的失败"才算薄弱证据；带提示或看答案后的失败不进薄弱清单
                if (record.isFirstObservation()
                        && EvidenceQualification.HELP_NONE.equals(record.getHelpLevel())
                        && StringUtils.isNotBlank(record.getNodeCode())) {
                    weak.add(record.getNodeCode());
                }
            } else if (result == LearningResultEnum.PASS) {
                summary.setPasses(summary.getPasses() + 1);
            }
            if (!EvidenceQualification.HELP_NONE.equals(record.getHelpLevel())
                    && !EvidenceQualification.HELP_UNKNOWN.equals(record.getHelpLevel())) {
                summary.setHelpedAttempts(summary.getHelpedAttempts() + 1);
            }
        }
        summary.setWeakNodeCodes(new ArrayList<>(weak));
        summary.setNarrative(narrative(summary));
        return summary;
    }

    /** 供模型消费的紧凑文本（不含他人信息，不含答案）。 */
    public String describe(EvidenceSummary summary) {
        if (summary == null || summary.getRecords().isEmpty()) {
            return "尚无本人学习事件记录。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本人近期学习事件 ").append(summary.getRecords().size()).append(" 条：")
                .append("失败 ").append(summary.getFailures())
                .append("，通过 ").append(summary.getPasses())
                .append("，诊断（系统/编译错误，不计知识失误）").append(summary.getDiagnostics())
                .append("，其中获得过平台帮助 ").append(summary.getHelpedAttempts()).append(" 条。\n");
        for (EvidenceRecord record : summary.getRecords()) {
            sb.append("- [").append(record.getEventId()).append("] ")
                    .append(StringUtils.defaultIfBlank(record.getNodeCode(), record.getNodeName()))
                    .append(' ').append(record.getEventType())
                    .append(' ').append(record.getResult())
                    .append(record.isFirstObservation() ? "（独立观测）" : "（不计独立观测）")
                    .append(" 帮助=").append(record.getHelpLevel())
                    .append(record.isDiagnostic() ? " 诊断事件" : "")
                    .append(StringUtils.isBlank(record.getNote()) ? "" : "：" + record.getNote())
                    .append('\n');
        }
        if (!summary.getWeakNodeCodes().isEmpty()) {
            sb.append("有真实失败观测的知识点：").append(String.join("、", summary.getWeakNodeCodes()));
        }
        return sb.toString();
    }

    private EvidenceRecord toRecord(LearningEvent event) {
        EvidenceRecord record = new EvidenceRecord();
        record.setEventId(AiJson.id(event.getId()));
        record.setNodeCode(event.getNodeCode());
        record.setNodeName(event.getNodeName());
        record.setEventType(event.getEventType());
        record.setResult(event.getResult());
        record.setFirstObservation(event.getIsFirst() != null && event.getIsFirst() == 1);
        record.setDiagnostic(isDiagnostic(event));
        record.setOccurredAt(AiJson.iso(event.getCreateTime()));
        record.setSourceUnitCode(event.getSourceUnitCode());
        record.setSourceActivityCode(event.getSourceActivityCode());
        JSONObject evidence = parseEvidence(event.getEvidence());
        record.setHelpLevel(evidence == null ? EvidenceQualification.HELP_UNKNOWN
                : StringUtils.defaultIfBlank(evidence.getStr("helpLevel"), EvidenceQualification.HELP_UNKNOWN));
        record.setSelfReportedHelp(evidence == null ? EvidenceQualification.SELF_HELP_NONE
                : StringUtils.defaultIfBlank(evidence.getStr("selfReportedHelp"),
                EvidenceQualification.SELF_HELP_NONE));
        record.setNote(evidence == null ? null : evidence.getStr("note"));
        return record;
    }

    /** 系统错误 / 编译错误是诊断事件，不得归为知识薄弱。 */
    private static boolean isDiagnostic(LearningEvent event) {
        LearningResultEnum result = LearningResultEnum.parse(event.getResult());
        return result != null && result.isDiagnostic();
    }

    private static JSONObject parseEvidence(String evidence) {
        if (StringUtils.isBlank(evidence)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(evidence);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String narrative(EvidenceSummary summary) {
        if (summary.getRecords().isEmpty()) {
            return "该范围内暂无本人学习事件；不自动判为薄弱，建议先完成 1-3 个短任务了解起点。";
        }
        if (summary.getFailures() == 0 && summary.getDiagnostics() > 0) {
            return "近期只有系统或编译类诊断事件，不计入知识失误，不能据此判定薄弱。";
        }
        if (summary.getWeakNodeCodes().isEmpty() && summary.getHelpedAttempts() > 0) {
            return "存在获得帮助后的作答，但没有无帮助条件下的失败观测，证据不足以判定薄弱。";
        }
        if (summary.getWeakNodeCodes().isEmpty()) {
            return "近期没有独立观测到的失败，暂无薄弱结论。";
        }
        return "存在独立观测到的失败，可据此提出候选原因并安排区分任务。";
    }

    /** 事件 id 列表（字符串），供诊断的 supporting/conflicting 引用。 */
    public static List<String> eventIds(EvidenceSummary summary) {
        if (summary == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(summary.getRecords().size());
        for (EvidenceRecord record : summary.getRecords()) {
            if (StringUtils.isNotBlank(record.getEventId())) {
                ids.add(record.getEventId());
            }
        }
        return ids;
    }
}
