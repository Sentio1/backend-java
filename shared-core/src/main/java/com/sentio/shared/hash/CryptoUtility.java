package com.sentio.shared.hash;

import com.google.common.hash.Hashing;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class CryptoUtility {

    private CryptoUtility() {
        throw new UnsupportedOperationException();
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTE_LENGTH = 32;

    public static String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(@NonNull String rawToken) {
        return Hashing.sha256()
                .hashString(rawToken, StandardCharsets.UTF_8)
                .toString();
    }
}
