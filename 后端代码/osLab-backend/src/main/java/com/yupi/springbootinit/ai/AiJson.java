package com.yupi.springbootinit.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * AI 升级共用的 JSON / ID / 时间工具。
 *
 * <p>契约要求（任务书 §3）：长整型 ID 在 JSON 中为字符串；
 * 新增时间带明确时区，且不改动旧接口的本地时间格式。
 */
@Slf4j
public final class AiJson {

    /** 平台演示与国内部署统一使用东八区；带偏移量输出，前端无需猜时区。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AiJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[ai] json serialize failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    public static <T> T read(String json, Class<T> type) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.warn("[ai] json deserialize failed for {}: {}", type.getSimpleName(), e.getClass().getSimpleName());
            return null;
        }
    }

    public static <T> List<T> readList(String json, Class<T> type) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            // readValue 已经返回 List<T>，不能再套 Arrays.asList：
            // 那会得到"只含一个 List 元素"的列表，调用方迭代时抛 ClassCastException。
            List<T> parsed = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, type));
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception e) {
            log.warn("[ai] json list deserialize failed for {}: {}", type.getSimpleName(),
                    e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    /** 长整型 ID → 契约字符串；null 保持 null。 */
    public static String id(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    public static List<String> ids(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(values.size());
        for (Long value : values) {
            if (value != null) {
                out.add(String.valueOf(value));
            }
        }
        return out;
    }

    /** 契约字符串 → 长整型；非法输入抛 NumberFormatException 由调用方转成参数错误。 */
    public static Long parseId(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return Long.valueOf(value.trim());
    }

    public static String nowIso() {
        return ISO.format(ZonedDateTime.now(ZONE));
    }

    public static String iso(Date date) {
        if (date == null) {
            return null;
        }
        return ISO.format(ZonedDateTime.ofInstant(date.toInstant(), ZONE));
    }
}
