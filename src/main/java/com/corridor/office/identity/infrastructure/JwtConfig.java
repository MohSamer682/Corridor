package com.corridor.office.identity.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, LoginLimitProperties.class})
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(RSAKey corridorSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(corridorSigningKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey corridorSigningKey, JwtProperties properties, Clock clock) {
        var decoder =
                NimbusJwtDecoder.withPublicKey(publicKey(corridorSigningKey)).build();
        var timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(properties.issuer())));
        return decoder;
    }

    private static RSAPublicKey publicKey(RSAKey key) {
        try {
            return key.toRSAPublicKey();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Signing key has no usable public part", exception);
        }
    }
}
