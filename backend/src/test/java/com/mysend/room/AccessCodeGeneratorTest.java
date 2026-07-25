package com.mysend.room;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class AccessCodeGeneratorTest {

    @Test
    void generatesFourDigitsAndOneReadableLetter() {
        AccessCodeGenerator generator = new AccessCodeGenerator(new SecureRandom());

        for (int index = 0; index < 100; index++) {
            assertThat(generator.nextAvailable(code -> false))
                    .matches("\\d{4}[A-HJ-NP-Z]");
        }
    }

    @Test
    void normalizesCodesCaseInsensitively() {
        assertThat(AccessCodeGenerator.normalize(" 4821k ")).isEqualTo("4821K");
        assertThat(AccessCodeGenerator.isValid("4821k")).isTrue();
        assertThat(AccessCodeGenerator.isValid("48O1K")).isFalse();
    }
}
