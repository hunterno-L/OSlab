package com.yupi.springbootinit.utils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 固定窗口内存限流器。
 * <p>
 * 单实例部署下有效；若将来横向扩容为多实例，应替换为基于 Redis 的分布式实现，
 * 以保证计数在实例间共享。
 */
public class RateLimiter {

    /** 窗口状态：窗口起始时间戳与已计数次数。 */
    private static final class Window {
        private long start;
        private int count;

        Window(long start, int count) {
            this.start = start;
            this.count = count;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxCount;
    private final long windowMillis;

    /**
     * @param maxCount     窗口内允许的最大次数
     * @param windowMillis 窗口时长（毫秒）
     */
    public RateLimiter(int maxCount, long windowMillis) {
        if (maxCount <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("maxCount and windowMillis must be positive");
        }
        this.maxCount = maxCount;
        this.windowMillis = windowMillis;
    }

    /**
     * 尝试获取一次配额。
     *
     * @param key 限流维度（如账号、IP、用户 id）
     * @return true 表示允许；false 表示当前窗口内已超过上限
     */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        windows.compute(key, (k, old) -> {
            if (old == null || now - old.start >= windowMillis) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (old.count < maxCount) {
                old.count++;
                allowed[0] = true;
            }
            return old;
        });
        return allowed[0];
    }

    /**
     * 清除指定 key 的计数（例如登录成功后重置失败计数）。
     */
    public void reset(String key) {
        windows.remove(key);
    }
}
