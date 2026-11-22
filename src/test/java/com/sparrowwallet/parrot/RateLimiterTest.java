package com.sparrowwallet.parrot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.sparrowwallet.parrot.RateLimiter.Result.*;

public class RateLimiterTest {
    private static final long MINUTE = 60 * 1000;

    @Test
    public void allowsBurstThenRejects() {
        RateLimiter rateLimiter = new RateLimiter(5, MINUTE);
        for(int i = 0; i < 5; i++) {
            Assertions.assertEquals(ACQUIRED, rateLimiter.tryAcquire(1L, 0));
        }
        Assertions.assertEquals(REJECTED, rateLimiter.tryAcquire(1L, 0));
        Assertions.assertEquals(REJECTED_AGAIN, rateLimiter.tryAcquire(1L, 1000));
    }

    @Test
    public void allowsFivePerMinuteSustained() {
        RateLimiter rateLimiter = new RateLimiter(5, MINUTE);
        for(int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(1L, 0);
        }

        int acquired = 0;
        for(long t = 1000; t <= MINUTE; t += 1000) {
            if(rateLimiter.tryAcquire(1L, t) == ACQUIRED) {
                acquired++;
            }
        }
        Assertions.assertEquals(5, acquired);
    }

    @Test
    public void refillsGraduallyAndResetsNotification() {
        RateLimiter rateLimiter = new RateLimiter(5, MINUTE);
        for(int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(1L, 0);
        }
        Assertions.assertEquals(REJECTED, rateLimiter.tryAcquire(1L, 0));
        Assertions.assertEquals(REJECTED_AGAIN, rateLimiter.tryAcquire(1L, MINUTE / 5 - 1));
        Assertions.assertEquals(ACQUIRED, rateLimiter.tryAcquire(1L, MINUTE / 5));
        Assertions.assertEquals(REJECTED, rateLimiter.tryAcquire(1L, MINUTE / 5));
    }

    @Test
    public void usersAreIndependent() {
        RateLimiter rateLimiter = new RateLimiter(1, MINUTE);
        Assertions.assertEquals(ACQUIRED, rateLimiter.tryAcquire(1L, 0));
        Assertions.assertEquals(REJECTED, rateLimiter.tryAcquire(1L, 0));
        Assertions.assertEquals(ACQUIRED, rateLimiter.tryAcquire(2L, 0));
    }
}
