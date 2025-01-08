package com.sparrowwallet.parrot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class RateLimiter {
    private final int maxTokens;
    private final long refillIntervalMillis;
    private final Map<String, AtomicInteger> tokensMap;
    private final Map<String, Long> lastRefillTimestampMap;
    private final ReentrantLock lock = new ReentrantLock();

    public RateLimiter(int maxTokens, long refillIntervalMillis) {
        this.maxTokens = maxTokens;
        this.refillIntervalMillis = refillIntervalMillis;
        this.tokensMap = new HashMap<>();
        this.lastRefillTimestampMap = new HashMap<>();
    }

    public boolean tryAcquire(String username) {
        AtomicInteger tokens = tokensMap.computeIfAbsent(username, _ -> new AtomicInteger(maxTokens));
        this.refill(username, tokens);
        if(tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        } else {
            return false;
        }
    }

    private void refill(String username, AtomicInteger tokens) {
        this.lock.lock();

        long lastRefillTimestamp = lastRefillTimestampMap.computeIfAbsent(username, _ -> System.currentTimeMillis());
        try {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;
            if(elapsed > this.refillIntervalMillis) {
                int tokensToAdd = (int)(elapsed / this.refillIntervalMillis);
                tokens.set(Math.min(this.maxTokens, tokens.get() + tokensToAdd));
                lastRefillTimestampMap.put(username, now);
            }
        } finally {
            this.lock.unlock();
        }
    }
}
