package com.corridor.office.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.corridor.office.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T09:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    @Test
    void shouldBlockTheEleventhAttemptWithinAMinute() {
        attempt(10, "10.0.0.1", "a@b.c");

        assertThatThrownBy(() -> limiter.check("10.0.0.1", "a@b.c")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void shouldAllowAttemptsAgainAfterTheWindow() {
        attempt(10, "10.0.0.1", "a@b.c");

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.check("10.0.0.1", "a@b.c")).doesNotThrowAnyException();
    }

    @Test
    void shouldBlockOneEmailTriedFromManyAddresses() {
        for (int i = 0; i < 10; i++) {
            limiter.check("10.0.0." + i, "a@b.c");
        }

        assertThatThrownBy(() -> limiter.check("10.0.1.1", "a@b.c")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void shouldTellHowLongToWait() {
        attempt(10, "10.0.0.1", "a@b.c");
        clock.advance(Duration.ofSeconds(20));

        var exception = catchThrowableOfType(TooManyAttemptsException.class, () -> limiter.check("10.0.0.1", "a@b.c"));

        assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(40));
    }

    private void attempt(int times, String ip, String email) {
        for (int i = 0; i < times; i++) {
            limiter.check(ip, email);
        }
    }
}
