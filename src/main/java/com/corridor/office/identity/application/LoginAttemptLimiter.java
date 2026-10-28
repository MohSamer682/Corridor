package com.corridor.office.identity.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

// shortcut: counts live in this JVM only, so each instance has its own limit; move to Redis with F-15.
@Component
public class LoginAttemptLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Cache<String, AtomicInteger> attempts;

    public LoginAttemptLimiter(Clock clock) {
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(WINDOW)
                .ticker(() -> clock.millis() * 1_000_000L)
                .maximumSize(100_000)
                .build();
    }

    public void check(String clientIp, String normalizedEmail) {
        count("ip:" + clientIp);
        count("email:" + normalizedEmail);
    }

    private void count(String key) {
        var total = attempts.get(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (total > MAX_ATTEMPTS) {
            throw new TooManyAttemptsException(retryAfter(key));
        }
    }

    private Duration retryAfter(String key) {
        var age = attempts.policy().expireAfterWrite().orElseThrow().ageOf(key).orElse(Duration.ZERO);
        return WINDOW.minus(age);
    }
}
