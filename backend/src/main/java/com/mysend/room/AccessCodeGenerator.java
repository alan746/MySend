package com.mysend.room;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

@Component
public class AccessCodeGenerator {

    private static final char[] LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random;

    public AccessCodeGenerator(SecureRandom random) {
        this.random = random;
    }

    public String nextAvailable(Predicate<String> isUnavailable) {
        for (int attempt = 0; attempt < 64; attempt++) {
            String code = "%04d%c".formatted(
                    random.nextInt(10_000),
                    LETTERS[random.nextInt(LETTERS.length)]
            );
            if (!isUnavailable.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("The active access-code pool is temporarily busy");
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String code) {
        return normalize(code).matches("\\d{4}[A-Z]");
    }
}
