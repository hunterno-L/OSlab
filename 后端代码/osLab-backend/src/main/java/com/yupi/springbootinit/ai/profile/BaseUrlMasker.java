package com.yupi.springbootinit.ai.profile;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * baseUrl 脱敏（任务书 §4：响应与日志只出现脱敏值）。
 *
 * <p>规则：去掉 userinfo、query 与 fragment（这三处最容易夹带凭据与内部参数）；
 * IP 字面量主机按网段遮蔽；命名主机保留，因为列表严格限定为本人配置，
 * 用户需要能认出"这是我配的哪一个地址"。
 */
public final class BaseUrlMasker {

    private BaseUrlMasker() {
    }

    public static String mask(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            return "";
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException e) {
            return "***";
        }
        String scheme = StringUtils.lowerCase(StringUtils.defaultString(uri.getScheme()), Locale.ROOT);
        String host = uri.getHost();
        if (StringUtils.isBlank(scheme) || StringUtils.isBlank(host)) {
            return "***";
        }
        StringBuilder sb = new StringBuilder(scheme).append("://").append(maskHost(host));
        if (uri.getPort() > 0) {
            sb.append(':').append(uri.getPort());
        }
        String path = StringUtils.defaultString(uri.getPath());
        if (StringUtils.isNotBlank(path)) {
            sb.append(path.startsWith("/") ? path : "/" + path);
        }
        // query 与 fragment 一律丢弃
        return sb.toString();
    }

    private static String maskHost(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        if (isIpv4(value)) {
            String[] parts = value.split("\\.");
            return parts[0] + "." + parts[1] + ".***.***";
        }
        if (value.startsWith("[") || value.contains(":")) {
            // IPv6：只保留前两组
            String raw = StringUtils.strip(value, "[]");
            String[] groups = raw.split(":");
            return "[" + (groups.length > 0 ? groups[0] : "") + ":"
                    + (groups.length > 1 ? groups[1] : "") + ":***]";
        }
        return value;
    }

    private static boolean isIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (!StringUtils.isNumeric(part) || part.length() > 3) {
                return false;
            }
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }
}
