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
import java.util.Locale;
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

    /**
     * Returns the nym in its canonical form if it is a valid nym ignoring an optional leading #, otherwise null.
     * Case is ignored unless needed to distinguish nyms that differ only by case, such as #CloseDad and #ClosedAd.
     */
    public static String toCanonicalNym(String input) {
        String name = input.strip();
        if(name.startsWith("#")) {
            name = name.substring(1);
        }

        String caseInsensitiveMatch = null;
        boolean ambiguous = false;
        String lowerName = name.toLowerCase(Locale.ROOT);
        for(String adjective : ENGLISH_ADJECTIVES) {
            String capitalizedAdjective = capitalize(adjective);
            if(!lowerName.startsWith(capitalizedAdjective.toLowerCase(Locale.ROOT))) {
                continue;
            }

            String rest = name.substring(capitalizedAdjective.length());
            for(String noun : ENGLISH_NOUNS) {
                String capitalizedNoun = capitalize(noun);
                if(capitalizedNoun.equalsIgnoreCase(rest)) {
                    String nym = "#" + capitalizedAdjective + capitalizedNoun;
                    if(nym.equals("#" + name)) {
                        return nym;
                    }
                    if(caseInsensitiveMatch == null) {
                        caseInsensitiveMatch = nym;
                    } else if(!caseInsensitiveMatch.equals(nym)) {
                        ambiguous = true;
                    }
                }
            }
        }

        return ambiguous ? null : caseInsensitiveMatch;
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("-", "");
    }
}
