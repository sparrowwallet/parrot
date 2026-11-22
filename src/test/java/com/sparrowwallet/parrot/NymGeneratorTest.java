package com.sparrowwallet.parrot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NymGeneratorTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    public void generateNym() {
        Assertions.assertEquals("#GlumPitch", NymGenerator.getNym(SECRET, "123456789"));
    }

    @Test
    public void generateNym2() {
        Assertions.assertEquals("#PinkChair", NymGenerator.getNym(SECRET, "5678901234"));
    }

    @Test
    public void generateNym3() {
        Assertions.assertEquals("#BurlyPiano", NymGenerator.getNym(SECRET, "7000000001"));
    }

    @Test
    public void nymDependsOnSecret() {
        Assertions.assertNotEquals(NymGenerator.getNym(SECRET, "123456789"), NymGenerator.getNym("fedcba9876543210fedcba9876543210", "123456789"));
    }

    @Test
    public void missingSecretFails() {
        Assertions.assertThrows(IllegalStateException.class, () -> NymGenerator.getNym(null, "123456789"));
    }
}
