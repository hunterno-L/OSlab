package com.yupi.springbootinit.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 升级统一配置（任务书 §4）。
 *
 * <p>平台默认模型复用既有 {@code ai.ark.*} 配置，避免"新入口能配置、旧入口另一套模型"。
 * 主密钥只来自环境变量，绝不写入代码、配置文件默认值、日志或接口响应。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Ark ark = new Ark();

    /** AES-GCM 主密钥（环境变量 OSLAB_AI_MASTER_KEY）。缺失时个人配置无法保存密钥。 */
    private String masterKey = "";

    /** 部署方显式放行后才允许个人 baseUrl 指向本机/内网；默认 false（任务书 §4）。 */
    private boolean allowPrivateBaseUrl = false;

    /** 单次模型/ embedding HTTP 调用超时（毫秒）。 */
    private int requestTimeoutMs = 30000;

    /** 连接建立超时（毫秒）。 */
    private int connectTimeoutMs = 5000;

    private Turn turn = new Turn();

    private Retrieval retrieval = new Retrieval();

    @Data
    public static class Ark {
        private String apiKey = "";
        private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        private String model = "deepseek-v3-250324";
        /**
         * embedding 模型标识。留空表示当前凭据没有可用的 embedding 服务，
         * 检索必须诚实降级为关键词+图谱并标注 mode=FALLBACK，不得包装成语义检索。
         */
        private String embeddingModel = "";
        /**
         * 独立 embedding 服务的 OpenAI 兼容地址（例如对话用 DeepSeek、向量用阿里云）。
         * 留空时复用 {@link #baseUrl}，保持既有单端点部署不变。
         */
        private String embeddingBaseUrl = "";
        /** 独立 embedding 服务的密钥；留空时复用 {@link #apiKey}。 */
        private String embeddingApiKey = "";
    }

    /** 导师轮次预算：每轮最多 3 次工具执行、2 次模型调用（计划 §5.3）。 */
    @Data
    public static class Turn {
        private int maxToolCalls = 3;
        private int maxModelCalls = 2;
        /** 有界执行线程池：2 核 2GB 部署目标下不得新增常驻重型依赖。 */
        private int poolSize = 2;
        private int queueCapacity = 32;
        /** 生成回答的 token 上限（推理型模型需要留足空间，否则 content 为空）。 */
        private int answerMaxTokens = 4096;
        /** 工具决策的 token 上限（推理型模型的思考 token 也计入该上限）。 */
        private int planMaxTokens = 2048;
        /** 多轮上下文：只读最近 N 个成功轮次（01 契约 §3.2）。 */
        private int historyMaxTurns = 6;
        /** 多轮上下文总字符预算；超出时优先保留较新的轮次。 */
        private int historyMaxChars = 6000;
        /** 单轮"问题+回答"字符上限；超出部分截断并标注。 */
        private int historyTurnChars = 1200;
    }

    @Data
    public static class Retrieval {
        /** 单次联合检索返回的来源上限。 */
        private int maxSources = 8;
        /** 语义召回候选块上限（应用内计算相似度，语料控制在数百块）。 */
        private int semanticCandidates = 200;
        /** 关键词召回候选块上限。 */
        private int keywordCandidates = 60;
        /** 个人资料单条文本上限（字符）。 */
        private int materialMaxChars = 20000;
        /** 单个用户单课程资料条数上限。 */
        private int materialMaxCount = 20;
        /** 段落块目标长度（字符）。 */
        private int chunkTargetChars = 700;
    }
}
