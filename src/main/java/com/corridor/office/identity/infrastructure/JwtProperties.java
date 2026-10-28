package com.corridor.office.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.security.jwt")
@Validated
public record JwtProperties(
        @NotBlank String issuer,
        @DefaultValue("10m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        Resource privateKey,
        Resource publicKey) {}
