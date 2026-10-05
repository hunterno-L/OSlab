package com.yupi.springbootinit.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 id 精度修复（Phase 4）：JS Number 只能安全表示 2^53，
 * Long 型雪花 id（如 2105896752235872257）经 JSON 数字会丢失末位精度。
 * 统一把 Long 序列化为字符串；id 对前端是不透明值，字符串透传即可。
 */
@Configuration
public class JacksonSnowflakeConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
