package com.sparrowwallet.parrot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final Object lock = new Object();

    public static void main(String[] args) {
        String botToken = System.getenv("PARROT_BOT_TOKEN");
        if(botToken == null) {
            System.err.println("PARROT_BOT_TOKEN environment variable not set");
            System.exit(1);
        }

        String botUserName = System.getenv("PARROT_BOT_USERNAME");
        if(botUserName == null) {
            System.err.println("PARROT_BOT_USERNAME environment variable not set");
            System.exit(1);
        }

        String groupId = System.getenv("PARROT_GROUP_ID");
        if(groupId == null) {
            System.err.println("PARROT_GROUP_ID environment variable not set");
            System.exit(1);
        }

        try(TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new ParrotBot(botToken, botUserName, groupId));
        } catch(Exception e) {
            log.error("Failed to register bot", e);
        }

        try {
            synchronized(lock) {
                lock.wait();
            }
        } catch(InterruptedException e) {
            log.error("Interrupted while waiting for lock", e);
        }
    }
}