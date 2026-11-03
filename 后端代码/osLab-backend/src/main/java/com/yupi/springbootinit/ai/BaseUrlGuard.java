package com.yupi.springbootinit.ai;

import org.apache.commons.lang3.StringUtils;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 个人 baseUrl 安全校验（任务书 §4 / 计划 §5.7）：
 * 校验协议与解析目标，默认阻止访问部署机器的本机与内网服务；
 * 确有本地模型需求时由部署配置 {@code ai.allow-private-base-url=true} 显式放行。
 *
 * <p>解析后的每个地址都要检查，避免域名指向 127.0.0.1 / 内网网段绕过。
 * 同时拒绝 URL 内嵌凭据（userinfo），防止把密钥写进地址栏与日志。
 */
public final class BaseUrlGuard {

    /** DNS 解析 seam：单元测试注入固定地址，不触网。 */
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static final HostResolver SYSTEM_RESOLVER = InetAddress::getAllByName;

    private final boolean allowPrivate;
    private final HostResolver resolver;

    public BaseUrlGuard(boolean allowPrivate) {
        this(allowPrivate, SYSTEM_RESOLVER);
    }

    public BaseUrlGuard(boolean allowPrivate, HostResolver resolver) {
        this.allowPrivate = allowPrivate;
        this.resolver = resolver == null ? SYSTEM_RESOLVER : resolver;
    }

    /**
     * @return null 表示通过；否则返回可直接展示给用户的中文拒绝原因
     */
    public String validate(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            return "服务地址不能为空";
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException e) {
            return "服务地址格式无效";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return "服务地址只支持 http 或 https";
        }
        if (StringUtils.isNotBlank(uri.getUserInfo())) {
            return "服务地址不得内嵌账号或密钥";
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            return "服务地址缺少主机名";
        }
        // URI.getHost() 对 IPv6 保留方括号，解析前必须去掉
        String normalized = StringUtils.strip(host.toLowerCase(Locale.ROOT), "[]");
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")
                || normalized.endsWith(".local") || normalized.endsWith(".internal")) {
            return allowPrivate ? null : "默认禁止访问本机或内部服务地址：" + host;
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(isIpLiteral(normalized) ? normalized : IDN.toASCII(normalized));
        } catch (UnknownHostException | IllegalArgumentException e) {
            return "服务地址无法解析：" + host;
        }
        if (addresses == null || addresses.length == 0) {
            return "服务地址无法解析：" + host;
        }
        if (allowPrivate) {
            return null;
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                return "默认禁止访问本机或内网地址：" + host;
            }
        }
        return null;
    }

    /** 抛业务无关的运行时校验：通过返回，失败返回原因字符串。 */
    public void requireValid(String baseUrl) {
        String reason = validate(baseUrl);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
    }

    /** IP 字面量不需要 IDN 转换（转换会对冒号与方括号报错）。 */
    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '.');
    }

    private static boolean isBlocked(InetAddress address) {        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isUniqueLocalIpv6(address);
    }

    /** 100.64.0.0/10（运营商级 NAT）不在 isSiteLocalAddress 覆盖范围内。 */
    private static boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    /** fc00::/7 唯一本地地址。 */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
