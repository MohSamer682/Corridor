package com.corridor.office.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {}
