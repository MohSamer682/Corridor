package com.corridor.office.identity.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.security.refresh-cookie")
record RefreshCookieProperties(@DefaultValue("true") boolean secure) {}
