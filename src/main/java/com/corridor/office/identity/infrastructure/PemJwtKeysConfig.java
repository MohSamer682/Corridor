package com.corridor.office.identity.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class PemJwtKeysConfig {

    @Bean
    public RSAKey corridorSigningKey(JwtProperties properties) throws IOException, JOSEException {
        var publicKey = (RSAPublicKey) read(properties.publicKey(), true);
        var privateKey = (RSAPrivateKey) read(properties.privateKey(), false);
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyIDFromThumbprint()
                .build();
    }

    private static Object read(Resource pem, boolean isPublic) throws IOException {
        try (InputStream in = pem.getInputStream()) {
            return isPublic
                    ? RsaKeyConverters.x509().convert(in)
                    : RsaKeyConverters.pkcs8().convert(in);
        }
    }
}
