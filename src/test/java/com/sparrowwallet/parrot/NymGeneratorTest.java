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
    public void canonicalNym() {
        Assertions.assertEquals("#GlumPitch", NymGenerator.toCanonicalNym("#GlumPitch"));
        Assertions.assertEquals("#GlumPitch", NymGenerator.toCanonicalNym("GlumPitch"));
        Assertions.assertEquals("#GlumPitch", NymGenerator.toCanonicalNym(" #glumPITCH "));
        Assertions.assertNull(NymGenerator.toCanonicalNym("#GlumPitc"));
        Assertions.assertNull(NymGenerator.toCanonicalNym("#Glum"));
        Assertions.assertNull(NymGenerator.toCanonicalNym("#GlumPitch extra"));
        Assertions.assertNull(NymGenerator.toCanonicalNym("#"));
        Assertions.assertNull(NymGenerator.toCanonicalNym(""));
        Assertions.assertEquals("#CloseDad", NymGenerator.toCanonicalNym("#CloseDad"));
        Assertions.assertEquals("#ClosedAd", NymGenerator.toCanonicalNym("#ClosedAd"));
        Assertions.assertNull(NymGenerator.toCanonicalNym("#closedad"));
    }

    @Test
    public void generatedNymsAreCanonical() {
        for(long userId = 1000000; userId < 1002000; userId++) {
            String nym = NymGenerator.getNym(SECRET, Long.toString(userId));
            Assertions.assertEquals(nym, NymGenerator.toCanonicalNym(nym));
        }
    }

    @Test
    public void missingSecretFails() {
        Assertions.assertThrows(IllegalStateException.class, () -> NymGenerator.getNym(null, "123456789"));
    }
}
