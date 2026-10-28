package com.corridor.office.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Lets integration tests move time forward, for example past the login limiter's one-minute window. */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(Instant.now());
    }
}
