package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/** Reads the reviewed experiment mode without exposing authoring metadata or answers. */
@Service
public class ExperimentJudgeModeService {
    public static final String AUTO = "AUTO";
    public static final String LOCAL_BUILD = "LOCAL_BUILD";
    public static final String CONCEPT_HARNESS = "CONCEPT_HARNESS";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource private JdbcTemplate jdbc;

    public String mode(long questionId) {
        List<String> rows = jdbc.queryForList(
                "SELECT CAST(metadataJson AS CHAR) FROM course_resource " +
                        "WHERE questionId=? AND resourceType='EXPERIMENT' AND isDelete=0 ORDER BY id LIMIT 1",
                String.class, questionId);
        if (rows.isEmpty() || rows.get(0) == null) return AUTO;
        try {
            JsonNode metadata = JSON.readTree(rows.get(0));
            if (metadata == null || !metadata.isObject()) throw new IllegalArgumentException("metadata must be an object");
            JsonNode configured = metadata.path("judgeMode");
            if (configured.isMissingNode()) configured = metadata.path("metadataJson").path("judgeMode");
            if (configured.isMissingNode()) return AUTO;
            if (!configured.isTextual()) throw new IllegalArgumentException("mode must be text");
            String mode = configured.asText();
            if (LOCAL_BUILD.equals(mode) || CONCEPT_HARNESS.equals(mode)) return mode;
            if (AUTO.equals(mode) || "FUNCTIONAL_HARNESS".equals(mode)) return AUTO;
            throw new IllegalArgumentException("unknown judge mode");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "实验判题方式配置无效");
        }
    }

    public void requireAutomaticJudge(long questionId) {
        if (LOCAL_BUILD.equals(mode(questionId))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "本实验为本地构建，不自动判分。请按实验说明在本地 MIPS 环境验证。");
        }
    }
}
