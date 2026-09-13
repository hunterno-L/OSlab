package com.yupi.springbootinit.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定窗口内存限流器测试。
 */
class RateLimiterTest {

    @Test
    void allowsUpToLimitThenRejectsWithinWindow() {
        RateLimiter limiter = new RateLimiter(3, 60_000L);
        assertTrue(limiter.tryAcquire("key"));
        assertTrue(limiter.tryAcquire("key"));
        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"), "第 4 次尝试应被拒绝");
    }

    @Test
    void windowExpiryResetsCount() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, 50L);
        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));
        Thread.sleep(80L);
        assertTrue(limiter.tryAcquire("key"), "窗口过期后应允许再次尝试");
    }

    @Test
    void differentKeysAreIsolated() {
        RateLimiter limiter = new RateLimiter(1, 60_000L);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
        assertFalse(limiter.tryAcquire("a"), "a 的第二次尝试应被拒绝");
        assertFalse(limiter.tryAcquire("b"), "b 的第二次尝试也应被拒绝，与 a 互不影响");
    }

    @Test
    void resetClearsCount() {
        RateLimiter limiter = new RateLimiter(1, 60_000L);
        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));
        limiter.reset("key");
        assertTrue(limiter.tryAcquire("key"), "reset 后应重新允许");
    }
}
