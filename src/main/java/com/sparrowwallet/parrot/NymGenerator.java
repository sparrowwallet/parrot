package com.sparrowwallet.parrot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class NymGenerator {
    private static final Logger log = LoggerFactory.getLogger(NymGenerator.class);

    private static final String ENGLISH_ADJECTIVES_NAME = "/english-adjectives.txt";
    private static final String ENGLISH_NOUNS_NAME = "/english-nouns.txt";
    private static final String HMAC_SHA256 = "HmacSHA256";

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

    public static String getNym(String userId) {
        return getNym(System.getenv(ParrotBot.PARROT_NYM_SECRET), userId);
    }

    static String getNym(String secret, String userId) {
        ByteBuffer digest = ByteBuffer.wrap(hmacSha256(secret, userId));
        String adjective = ENGLISH_ADJECTIVES.get((int)Long.remainderUnsigned(digest.getLong(), ENGLISH_ADJECTIVES.size()));
        String noun = ENGLISH_NOUNS.get((int)Long.remainderUnsigned(digest.getLong(), ENGLISH_NOUNS.size()));

        return "#" + capitalize(adjective) + capitalize(noun);
    }

    private static byte[] hmacSha256(String secret, String userId) {
        if(secret == null || secret.isEmpty()) {
            throw new IllegalStateException(ParrotBot.PARROT_NYM_SECRET + " is not set");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(userId.getBytes(StandardCharsets.UTF_8));
        } catch(NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to generate HMAC-SHA256", e);
        }
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("-", "");
    }
}
