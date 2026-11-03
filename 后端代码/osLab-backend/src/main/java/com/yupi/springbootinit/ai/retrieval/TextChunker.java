package com.yupi.springbootinit.ai.retrieval;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 段落分块（计划 §5.2）。
 *
 * <p>以空行分段为基本单位：过短的相邻段落合并到目标长度，过长的段落按句子边界再切，
 * 保证一个块是"可引用、可打开、语义完整"的片段，而不是任意定长切片。
 * 块序号即定位锚点，来源修订由调用方携带，分块本身不改变原文。
 */
public final class TextChunker {

    private static final int MIN_CHUNK_CHARS = 120;
    private static final int MAX_CHUNK_CHARS = 2400;

    private TextChunker() {
    }

    /**
     * @param markdown    原文（UTF-8 Markdown 或纯文本）
     * @param targetChars 目标块长度
     * @return 按原文顺序排列的段落块，永不为 null
     */
    public static List<String> chunk(String markdown, int targetChars) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isBlank(markdown)) {
            return out;
        }
        int target = Math.max(MIN_CHUNK_CHARS, Math.min(targetChars, MAX_CHUNK_CHARS));
        String[] paragraphs = markdown.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        for (String raw : paragraphs) {
            String paragraph = StringUtils.stripEnd(raw, "\r\n ");
            if (StringUtils.isBlank(paragraph)) {
                continue;
            }
            if (paragraph.length() > target * 2) {
                // 超长段落先落地当前缓冲，再按句子切
                flush(out, current);
                for (String sentence : splitLong(paragraph, target)) {
                    out.add(sentence);
                }
                continue;
            }
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > target) {
                flush(out, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        flush(out, current);
        return out;
    }

    private static void flush(List<String> out, StringBuilder current) {
        if (current.length() > 0) {
            out.add(current.toString());
            current.setLength(0);
        }
    }

    /** 按中英文句末标点切分，再合并到目标长度，避免产出半句话的碎片块。 */
    private static List<String> splitLong(String paragraph, int target) {
        List<String> sentences = new ArrayList<>();
        StringBuilder sentence = new StringBuilder();
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            sentence.append(c);
            if (isSentenceEnd(c) && sentence.length() >= MIN_CHUNK_CHARS) {
                sentences.add(sentence.toString());
                sentence.setLength(0);
            }
        }
        if (sentence.length() > 0) {
            if (sentences.isEmpty()) {
                sentences.add(sentence.toString());
            } else {
                sentences.set(sentences.size() - 1, sentences.get(sentences.size() - 1) + sentence);
            }
        }
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String item : sentences) {
            if (buffer.length() > 0 && buffer.length() + item.length() > target) {
                merged.add(buffer.toString());
                buffer.setLength(0);
            }
            buffer.append(item);
        }
        if (buffer.length() > 0) {
            merged.add(buffer.toString());
        }
        return merged;
    }

    private static boolean isSentenceEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；' || c == '\n'
                || c == '.' || c == '!' || c == '?' || c == ';';
    }

    /**
     * 检索 token 切分：中英文混排时同时产出词与二字组，
     * 让没有分词器的关键词召回对中文教材仍然有效。
     */
    public static List<String> tokens(String query, int maxTokens) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isBlank(query)) {
            return out;
        }
        for (String part : query.split("[\\s，。？！、；：,.;:!?\"'（）()\\[\\]【】<>《》]+")) {
            String token = part.trim();
            if (token.length() > 1 && !out.contains(token)) {
                out.add(token);
            }
        }
        // 中文连续片段再切二字组，提升同义表达命中率
        String compact = query.replaceAll("[^\\u4e00-\\u9fa5]", "");
        for (int i = 0; i + 2 <= compact.length(); i++) {
            String bigram = compact.substring(i, i + 2);
            if (!out.contains(bigram)) {
                out.add(bigram);
            }
        }
        return out.size() > maxTokens ? new ArrayList<>(out.subList(0, maxTokens)) : out;
    }
}
