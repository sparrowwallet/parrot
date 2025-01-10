package com.sparrowwallet.parrot.store;

import com.sparrowwallet.parrot.ForwardedMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class StoreTest {
    @Test
    public void test() throws IOException {
        Store store = getStore();

        String nym = "#DeliciousMonster";

        for(int i = 1; i <= 5; i++) {
            store.addSentNymMessage(nym, i);
        }

        List<Integer> ids = store.getSentNymMessageIds(nym);
        Assertions.assertEquals(5, ids.size());
        Assertions.assertEquals(1, ids.get(0));
        Assertions.assertEquals(2, ids.get(1));
        Assertions.assertEquals(3, ids.get(2));
        Assertions.assertEquals(4, ids.get(3));
        Assertions.assertEquals(5, ids.get(4));
        Assertions.assertFalse(ids.contains(6));

        store.addSentMessage(1, new ForwardedMessage(500L, 2));
        store.addSentMessage(2, new ForwardedMessage(500L, 3));

        Assertions.assertTrue(store.hasSentMessage(1));
        Assertions.assertTrue(store.hasSentMessage(2));
        Assertions.assertFalse(store.hasSentMessage(3));
        Assertions.assertEquals(500L, store.getSentMessage(1).chatId());
        Assertions.assertEquals(2, store.getSentMessage(1).messageId());
        Assertions.assertEquals(500L, store.getSentMessage(2).chatId());
        Assertions.assertEquals(3, store.getSentMessage(2).messageId());
        Assertions.assertNull(store.getSentMessage(3));

        store.addSentReply(5, 10);
        store.addSentReply(6, 20);

        Assertions.assertTrue(store.hasSentReply(5));
        Assertions.assertTrue(store.hasSentReply(6));
        Assertions.assertFalse(store.hasSentReply(7));
        Assertions.assertEquals(10, store.getSentReply(5));
        Assertions.assertEquals(20, store.getSentReply(6));
        Assertions.assertNull(store.getSentReply(7));

        store.addBannedNym(nym);

        Assertions.assertTrue(store.hasBannedNym(nym));
        Assertions.assertFalse(store.hasBannedNym("#xyz"));

        store.removeBannedNym(nym);

        Assertions.assertFalse(store.hasBannedNym(nym));

        long timestamp = System.currentTimeMillis();
        store.newUserAdded(1L, timestamp);

        Assertions.assertEquals(timestamp, store.getNewUserTimestamp(1L));
        Assertions.assertNull(store.getNewUserTimestamp(2L));

        store.close();
    }

    private Store getStore() {
        return new RedisStore();
    }
}
