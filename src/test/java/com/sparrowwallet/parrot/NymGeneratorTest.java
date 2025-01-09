package com.sparrowwallet.parrot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NymGeneratorTest {
    @Test
    public void generateNym() {
        String name = "Joe1234";
        String nym = NymGenerator.getNym(name);
        Assertions.assertEquals("#WickedGolf", nym);
    }

    @Test
    public void generateNym2() {
        String name = "Alice5678";
        String nym = NymGenerator.getNym(name);
        Assertions.assertEquals("#WebbedEditor", nym);
    }

    @Test
    public void generateNym3() {
        String name = "Charlie9";
        String nym = NymGenerator.getNym(name);
        Assertions.assertEquals("#ElatedMood", nym);
    }
}
