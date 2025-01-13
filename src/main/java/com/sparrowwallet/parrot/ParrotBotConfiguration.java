package com.sparrowwallet.parrot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.util.DefaultGetUpdatesGenerator;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ParrotBotConfiguration {
    class ParrotBotLongPollingApplication extends TelegramBotsLongPollingApplication {
        @Override
        public BotSession registerBot(String botToken, LongPollingUpdateConsumer updatesConsumer) throws TelegramApiException {
            return registerBot(botToken, () -> TelegramUrl.DEFAULT_URL, new DefaultGetUpdatesGenerator(getAllowedUpdates()), updatesConsumer);
        }
    }

    @Bean(value = "telegramBotsLongPollingApplication")
    public TelegramBotsLongPollingApplication telegramBotsLongPollingApplication() {
        return new ParrotBotLongPollingApplication();

    }

    private List<String> getAllowedUpdates() {
        return Arrays.asList("message", "edited_message", "chat_member", "message_reaction");
    }
}
