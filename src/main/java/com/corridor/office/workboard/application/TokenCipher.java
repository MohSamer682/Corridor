package com.corridor.office.workboard.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM for the Jira API token. The key comes from {@code app.jira.key} (32 bytes, base64), never the database. */
@Component
class TokenCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final String encodedKey;

    TokenCipher(@Value("${app.jira.key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    /** The random IV followed by the ciphertext and its tag. */
    byte[] encrypt(String token) {
        var iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        var sealed = run(Cipher.ENCRYPT_MODE, iv, token.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(iv.length + sealed.length)
                .put(iv)
                .put(sealed)
                .array();
    }

    String decrypt(byte[] stored) {
        var iv = Arrays.copyOf(stored, IV_BYTES);
        var sealed = Arrays.copyOfRange(stored, IV_BYTES, stored.length);
        return new String(run(Cipher.DECRYPT_MODE, iv, sealed), StandardCharsets.UTF_8);
    }

    private byte[] run(int mode, byte[] iv, byte[] input) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The Jira token could not be encrypted or decrypted", e);
        }
    }

    private byte[] key() {
        var key = encodedKey.isBlank() ? new byte[0] : Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) {
            throw new IllegalStateException("Set app.jira.key to 32 random bytes, base64 encoded");
        }
        return key;
    }
}
