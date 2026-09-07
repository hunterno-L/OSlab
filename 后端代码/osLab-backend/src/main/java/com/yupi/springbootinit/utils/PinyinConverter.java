package com.yupi.springbootinit.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

public class PinyinConverter {
    public static String convertToPinyin(String chinese) {
        // 创建拼音输出格式
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        // 禁用音调，并使用小写字母
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);

        StringBuilder pinyin = new StringBuilder();

        // 遍历字符串中的每个字符
        for (int i = 0; i < chinese.length(); i++) {
            char c = chinese.charAt(i);

            // 判断是否为汉字
            if (Character.toString(c).matches("[\u4e00-\u9fa5]")) {
                try {
                    // 获取该汉字的拼音
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    // 拼接拼音到结果中（使用第一个拼音，通常可以处理多音字）
                    pinyin.append(pinyinArray[0]);
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    e.printStackTrace();
                }
            } else {
                // 非汉字字符直接拼接
                pinyin.append(c);
            }
        }

        return pinyin.toString();
    }
}
