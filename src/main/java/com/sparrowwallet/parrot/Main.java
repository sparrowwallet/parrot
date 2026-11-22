package com.sparrowwallet.parrot;

import com.sparrowwallet.parrot.store.JavaStore;
import com.sparrowwallet.parrot.store.RedisStore;
import com.sparrowwallet.parrot.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.DefaultGetUpdatesGenerator;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final int MIN_NYM_SECRET_LENGTH = 32;
    private static final List<String> ALLOWED_UPDATES = List.of("message", "edited_message", "chat_member", "message_reaction");

    public static void main(String[] args) throws InterruptedException {
        String botToken = System.getenv(ParrotBot.PARROT_BOT_TOKEN);
        if(botToken == null) {
            System.err.println(ParrotBot.PARROT_BOT_TOKEN + " environment variable not set");
            System.exit(1);
        }

        String botUserName = System.getenv(ParrotBot.PARROT_BOT_USERNAME);
        if(botUserName == null) {
            System.err.println(ParrotBot.PARROT_BOT_USERNAME + " environment variable not set");
            System.exit(1);
        }

        String groupId = System.getenv(ParrotBot.PARROT_GROUP_ID);
        if(groupId == null) {
            System.err.println(ParrotBot.PARROT_GROUP_ID + " environment variable not set");
            System.exit(1);
        }

        String nymSecret = System.getenv(ParrotBot.PARROT_NYM_SECRET);
        if(nymSecret == null || nymSecret.length() < MIN_NYM_SECRET_LENGTH) {
            System.err.println(ParrotBot.PARROT_NYM_SECRET + " environment variable not set or shorter than " + MIN_NYM_SECRET_LENGTH + " characters");
            System.exit(1);
        }

        String redisUrl = System.getenv(ParrotBot.PARROT_REDIS_URL);
        Store store = "java".equalsIgnoreCase(System.getenv(ParrotBot.PARROT_STORE)) ? new JavaStore() : new RedisStore(redisUrl == null ? RedisStore.DEFAULT_REDIS_URL : redisUrl);
        ParrotBot parrotBot = new ParrotBot(store);
        TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");
            try {
                botsApplication.close();
            } catch(Exception e) {
                log.error("Error stopping bot", e);
            }
            parrotBot.stopHandlingUpdates();
            try {
                store.close();
            } catch(Exception e) {
                log.error("Error closing store", e);
            }
        }));

        try {
            botsApplication.registerBot(botToken, () -> TelegramUrl.DEFAULT_URL, new DefaultGetUpdatesGenerator(ALLOWED_UPDATES), parrotBot);
            log.info("Started " + botUserName);
        } catch(TelegramApiException e) {
            log.error("Error registering bot", e);
            System.exit(1);
        }

        Thread.currentThread().join();
    }
}
