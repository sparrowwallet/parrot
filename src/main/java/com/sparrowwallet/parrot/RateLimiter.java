package com.sparrowwallet.parrot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class RateLimiter {
    private final int maxTokens;
    private final long refillIntervalMillis;
    private final Map<Long, AtomicInteger> tokensMap;
    private final Map<Long, Long> lastRefillTimestampMap;
    private final ReentrantLock lock = new ReentrantLock();

    public RateLimiter(int maxTokens, long refillIntervalMillis) {
        this.maxTokens = maxTokens;
        this.refillIntervalMillis = refillIntervalMillis;
        this.tokensMap = new HashMap<>();
        this.lastRefillTimestampMap = new HashMap<>();
    }

    public boolean tryAcquire(Long userId) {
        AtomicInteger tokens = tokensMap.computeIfAbsent(userId, _ -> new AtomicInteger(maxTokens));
        this.refill(userId, tokens);
        if(tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        } else {
            return false;
        }
    }

    private void refill(Long userId, AtomicInteger tokens) {
        this.lock.lock();

        long lastRefillTimestamp = lastRefillTimestampMap.computeIfAbsent(userId, _ -> System.currentTimeMillis());
        try {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;
            if(elapsed > this.refillIntervalMillis) {
                int tokensToAdd = (int)(elapsed / this.refillIntervalMillis);
                tokens.set(Math.min(this.maxTokens, tokens.get() + tokensToAdd));
                lastRefillTimestampMap.put(userId, now);
            }
        } finally {
            this.lock.unlock();
        }
    }
}
