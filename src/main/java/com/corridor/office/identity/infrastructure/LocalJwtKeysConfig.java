package com.corridor.office.identity.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "test"})
public class LocalJwtKeysConfig {

    // A fresh key per start is fine locally: restarting only signs everyone out.
    @Bean
    public RSAKey corridorSigningKey() {
        try {
            return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not generate the local signing key", exception);
        }
    }
}
