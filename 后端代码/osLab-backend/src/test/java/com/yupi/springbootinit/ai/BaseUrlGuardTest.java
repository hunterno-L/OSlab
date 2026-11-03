package com.yupi.springbootinit.ai;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * baseUrl 安全校验回归（任务书 §4 / 计划 §5.7）：
 * 校验协议与解析目标，默认禁止访问部署机器的本机与内网服务；
 * 确有本地模型需求时由部署配置显式放行。
 */
class BaseUrlGuardTest {

    /** 固定 DNS 结果，避免测试触网；IP 字面量直接解析。 */
    private static BaseUrlGuard guardWith(Map<String, String> dns, boolean allowPrivate) {
        return new BaseUrlGuard(allowPrivate, host -> {
            String ip = dns.get(host);
            if (ip == null) {
                if (host.indexOf(':') >= 0 || host.matches("[0-9.]+")) {
                    return new InetAddress[]{InetAddress.getByName(host)};
                }
                throw new UnknownHostException(host);
            }
            return new InetAddress[]{InetAddress.getByName(ip)};
        });
    }

    private static Map<String, String> dns(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void acceptsPublicHttpsEndpoint() {
        BaseUrlGuard guard = guardWith(
                dns("ark.cn-beijing.volces.com", "23.44.55.66", "api.example.com", "93.184.216.34"),
                false);
        assertNull(guard.validate("https://ark.cn-beijing.volces.com/api/v3"));
        assertNull(guard.validate("https://api.example.com/v1/"));
    }

    @Test
    void rejectsNonHttpSchemes() {
        BaseUrlGuard guard = guardWith(dns(), false);
        assertNotNull(guard.validate("file:///etc/passwd"));
        assertNotNull(guard.validate("ftp://example.com/v1"));
        assertNotNull(guard.validate("jar:https://example.com!/a"));
        assertTrue(guard.validate("example.com/v1").contains("http"), "缺协议必须被拒");
        assertNotNull(guard.validate(""));
        assertNotNull(guard.validate(null));
    }

    @Test
    void rejectsEmbeddedCredentials() {
        BaseUrlGuard guard = guardWith(dns("example.com", "23.44.55.66"), false);
        String reason = guard.validate("https://user:sk-secret@example.com/v1");
        assertNotNull(reason);
        assertTrue(reason.contains("密钥") || reason.contains("账号"), "必须说明不得内嵌凭据");
    }

    @Test
    void rejectsLoopbackAndInternalTargetsByDefault() {
        Map<String, String> dns = dns(
                "localhost", "127.0.0.1",
                "::1", "::1",
                "internal.model.local", "127.0.0.1",
                "ollama.home", "192.168.1.20",
                "corp.model", "10.8.0.5",
                "link.local.host", "169.254.169.254",
                "cgnat.host", "100.64.0.1",
                "ipv6.local", "fc00::1");
        BaseUrlGuard guard = guardWith(dns, false);
        assertNotNull(guard.validate("http://localhost:11434/v1"), "localhost 必须被拒");
        assertNotNull(guard.validate("http://127.0.0.1:8000/v1"), "回环 IP 必须被拒");
        assertNotNull(guard.validate("http://[::1]:8000/v1"), "IPv6 回环必须被拒");
        assertNotNull(guard.validate("https://internal.model.local/v1"), ".local 必须被拒");
        assertNotNull(guard.validate("http://192.168.1.20:11434/v1"), "私网 IP 必须被拒");
        assertNotNull(guard.validate("http://ollama.home/v1"), "域名解析到私网必须被拒");
        assertNotNull(guard.validate("http://corp.model/v1"), "10/8 必须被拒");
        assertNotNull(guard.validate("http://link.local.host/v1"), "云元数据地址必须被拒");
        assertNotNull(guard.validate("http://cgnat.host/v1"), "运营商级 NAT 段必须被拒");
        assertNotNull(guard.validate("http://ipv6.local/v1"), "IPv6 唯一本地地址必须被拒");
        assertNotNull(guard.validate("http://0.0.0.0:8000/v1"), "任意本地地址必须被拒");
    }

    @Test
    void deploymentCanExplicitlyAllowPrivateTargets() {
        Map<String, String> dns = dns("ollama.home", "192.168.1.20");
        BaseUrlGuard guard = guardWith(dns, true);
        assertNull(guard.validate("http://localhost:11434/v1"));
        assertNull(guard.validate("http://127.0.0.1:8000/v1"));
        assertNull(guard.validate("http://ollama.home/v1"));
    }

    @Test
    void unresolvableHostIsRejected() {
        BaseUrlGuard guard = guardWith(dns(), false);
        String reason = guard.validate("https://no-such-host.invalid/v1");
        assertNotNull(reason);
        assertTrue(reason.contains("无法解析"));
    }

    @Test
    void malformedUrlIsRejectedWithoutLeaking() {
        BaseUrlGuard guard = guardWith(dns(), false);
        assertNotNull(guard.validate("https://exa mple.com/v1"));
        assertNotNull(guard.validate("https:///v1"));
    }

    @Test
    void requireValidThrowsWithReadableReason() {
        BaseUrlGuard guard = guardWith(dns("ark.cn-beijing.volces.com", "23.44.55.66"), false);
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> guard.requireValid("http://127.0.0.1/v1"));
        assertTrue(e.getMessage().contains("禁止"));
        guard.requireValid("https://ark.cn-beijing.volces.com/api/v3");
    }
}
