package com.corridor.office.identity.infrastructure;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.security.login-limit")
@Validated
public record LoginLimitProperties(@DefaultValue("10") @Positive int maxAttempts) {}
