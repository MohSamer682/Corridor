package com.corridor.office.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    // Hashes keep their {id} prefix so a later algorithm change can verify old hashes and upgrade them.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(
                "argon2", Map.of("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
}
