package com.github.phantomthief.util;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * @author w.vela
 * Created on 2019-11-28.
 */
class SimpleRateLimiterTest {

    @Test
    void test() {
        SimpleRateLimiter limiter = SimpleRateLimiter.create(1.0D);
        int j = 0;
        for (int i = 0; i < 100; i++) {
            if (i % 10 == 0) {
                sleepUninterruptibly(1, SECONDS);
            }
            if (limiter.tryAcquire()) {
                j++;
            }
        }
        assertEquals(10, j);
    }

    @Test
    void test2() {
        SimpleRateLimiter limiter = SimpleRateLimiter.create(2.0D);
        int j = 0;
        for (int i = 0; i < 50; i++) {
            if (i % 5 == 0) {
                sleepUninterruptibly(500, MILLISECONDS);
            }
            if (limiter.tryAcquire()) {
                j++;
            }
        }
        assertEquals(10, j);
    }

    @Test
    void test3() {
        SimpleRateLimiter limiter = SimpleRateLimiter.create(0.5);
        int j = 0;
        for (int i = 0; i < 80; i++) {
            if (i % 10 == 0) {
                sleepUninterruptibly(1, SECONDS);
            }
            if (limiter.tryAcquire()) {
                j++;
            }
        }
        assertEquals(4, j);
    }
}