package com.sparrowwallet.parrot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class NymGenerator {
    private static final Logger log = LoggerFactory.getLogger(NymGenerator.class);

    private static final String ENGLISH_ADJECTIVES_NAME = "/english-adjectives.txt";
    private static final String ENGLISH_NOUNS_NAME = "/english-nouns.txt";

    private static final List<String> ENGLISH_ADJECTIVES;
    private static final List<String> ENGLISH_NOUNS;

    static {
        ENGLISH_ADJECTIVES = new ArrayList<>();
        ENGLISH_NOUNS = new ArrayList<>();

        loadWordList(ENGLISH_ADJECTIVES_NAME, ENGLISH_ADJECTIVES);
        loadWordList(ENGLISH_NOUNS_NAME, ENGLISH_NOUNS);
    }

    private static void loadWordList(String resourceName, List<String> words) {
        try(BufferedReader br = new BufferedReader(new InputStreamReader(NymGenerator.class.getResourceAsStream(resourceName), StandardCharsets.UTF_8))) {
            String line;
            while((line = br.readLine()) != null) {
                words.add(line);
            }
        } catch(IOException e) {
            log.error("Failed to load word list from " + resourceName, e);
        }
    }

    public static String getNym(String userName) {
        byte[] digestBytes = sha256Hash(userName);

        HexFormat format = HexFormat.of();
        String first = format.formatHex(digestBytes, 0, digestBytes.length / 2);
        String second = format.formatHex(digestBytes, digestBytes.length / 2, digestBytes.length);

        int adjectiveHash = first.hashCode();
        String adjective = ENGLISH_ADJECTIVES.get(Math.abs(adjectiveHash) % ENGLISH_ADJECTIVES.size());

        int nounHash = second.hashCode();
        String noun = ENGLISH_NOUNS.get(Math.abs(nounHash) % ENGLISH_NOUNS.size());

        return capitalize(adjective) + capitalize(noun);
    }

    private static byte[] sha256Hash(String userName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(userName.getBytes(StandardCharsets.UTF_8), 0, userName.length());
            return digest.digest();
        } catch(NoSuchAlgorithmException e) {
            log.error("Failed to generate SHA-256 hash", e);
        }

        return new byte[0];
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("-", "");
    }
}
