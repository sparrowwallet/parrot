package com.sparrowwallet.parrot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@SpringBootApplication
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
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

        SpringApplication.run(Main.class, args);
    }
}