package com.yupi.springbootinit.ai.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.ai.AiJson;
import org.apache.commons.lang3.StringUtils;

/**
 * 向量工具：MySQL 存 JSON 文本，相似度在应用内计算（任务书 §4，不引入向量数据库）。
 *
 * <p>演示语料控制在数百块，逐块余弦相似度的开销可以接受；
 * 维度不一致一律返回 0，避免不同 embedding 模型的向量被误当作可比。
 */
public final class VectorMath {

    private VectorMath() {
    }

    public static float[] parse(String vectorJson) {
        if (StringUtils.isBlank(vectorJson)) {
            return null;
        }
        try {
            JsonNode node = AiJson.mapper().readTree(vectorJson);
            if (node == null || !node.isArray() || node.size() == 0) {
                return null;
            }
            float[] vector = new float[node.size()];
            for (int i = 0; i < node.size(); i++) {
                vector[i] = (float) node.get(i).asDouble();
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(vector.length * 12);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    /** 余弦相似度；任一向量为空或维度不同返回 0（禁止跨模型比较）。 */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
