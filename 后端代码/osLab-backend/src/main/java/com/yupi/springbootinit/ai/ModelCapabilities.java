package com.yupi.springbootinit.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 模型能力（计划 §5.7）：聊天、结构化输出、原生工具分别记录。
 * 不支持原生工具但结构化输出可用的模型走受约束协议。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelCapabilities implements Serializable {

    private boolean chat;
    private boolean structured;
    private boolean nativeTools;

    public static ModelCapabilities none() {
        return new ModelCapabilities(false, false, false);
    }

    public static ModelCapabilities chatOnly() {
        return new ModelCapabilities(true, false, false);
    }

    /** 未探测过时的保守假设：只保证聊天，结构化与工具由真实探测决定。 */
    public static ModelCapabilities unknown() {
        return new ModelCapabilities(true, false, false);
    }
}
