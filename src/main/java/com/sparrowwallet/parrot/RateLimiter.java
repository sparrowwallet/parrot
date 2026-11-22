package com.sparrowwallet.parrot;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-user token bucket allowing a burst of maxTokens, refilled continuously at maxTokens per refill interval.
 */
public class RateLimiter {
    private static final int PRUNE_THRESHOLD = 10000;

    private final int maxTokens;
    private final long refillIntervalMillis;
    private final Map<Long, Bucket> buckets = new HashMap<>();

    public RateLimiter(int maxTokens, long refillIntervalMillis) {
        this.maxTokens = maxTokens;
        this.refillIntervalMillis = refillIntervalMillis;
    }

    public Result tryAcquire(Long userId) {
        return tryAcquire(userId, System.currentTimeMillis());
    }

    synchronized Result tryAcquire(Long userId, long now) {
        if(buckets.size() > PRUNE_THRESHOLD) {
            buckets.values().removeIf(bucket -> bucket.isFull(now));
        }

        Bucket bucket = buckets.computeIfAbsent(userId, _ -> new Bucket(maxTokens, now));
        bucket.refill(now);
        if(bucket.tokens >= 1) {
            bucket.tokens--;
            bucket.rejected = false;
            return Result.ACQUIRED;
        }

        if(bucket.rejected) {
            return Result.REJECTED_AGAIN;
        }

        bucket.rejected = true;
        return Result.REJECTED;
    }

    public enum Result {
        ACQUIRED,
        REJECTED,
        REJECTED_AGAIN
    }

    private class Bucket {
        private double tokens;
        private long lastRefillMillis;
        private boolean rejected;

        private Bucket(double tokens, long lastRefillMillis) {
            this.tokens = tokens;
            this.lastRefillMillis = lastRefillMillis;
        }

        private void refill(long now) {
            long elapsed = Math.max(0, now - lastRefillMillis);
            tokens = Math.min(maxTokens, tokens + (double)elapsed * maxTokens / refillIntervalMillis);
            lastRefillMillis = now;
        }

        private boolean isFull(long now) {
            return tokens + (double)Math.max(0, now - lastRefillMillis) * maxTokens / refillIntervalMillis >= maxTokens;
        }
    }
}
