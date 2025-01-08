package com.sparrowwallet.parrot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.*;

public class ParrotBot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(ParrotBot.class);

    private final TelegramClient telegramClient;
    private final String groupId;
    private final RateLimiter rateLimiter;
    private final Map<String, List<Integer>> sentMessages;
    private final Set<String> bannedNyms;

    public ParrotBot(String botToken, String groupId) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.groupId = groupId;
        this.rateLimiter = new RateLimiter(5, 60 * 1000);
        this.sentMessages = new HashMap<>();
        this.bannedNyms = new HashSet<>();
    }

    @Override
    public void consume(Update update) {
        if(update.hasMessage() && update.getMessage().getChat().isUserChat()) {
            String userName = update.getMessage().getFrom().getUserName();
            if(!rateLimiter.tryAcquire(userName)) {
                sendRateLimitedMessage(update);
            } else if(update.getMessage().hasText()) {
                if(update.getMessage().getText().startsWith("/ban ") && isGroupAdmin(update.getMessage().getFrom().getId())) {
                    banNym(update.getMessage().getChatId(), update.getMessage().getText().substring(5));
                } else if(update.getMessage().getText().startsWith("/unban ") && isGroupAdmin(update.getMessage().getFrom().getId())) {
                    unbanNym(update.getMessage().getChatId(), update.getMessage().getText().substring(7));
                } else {
                    forwardText(update, userName);
                }
            } else if(update.getMessage().hasPhoto()) {
                forwardPhoto(update, userName);
            }
        } else if(update.hasMessage() && update.getMessage().getChat().isGroupChat() &&
                update.getMessage().getNewChatMembers() != null && !update.getMessage().getNewChatMembers().isEmpty()) {
            sendWelcomeMessage();
        }
    }

    private void forwardText(Update update, String userName) {
        String nym = NymGenerator.getNym(userName);
        if(bannedNyms.contains(nym)) {
            sendBannedMessage(update.getMessage().getChatId());
            return;
        }

        SendMessage sendMessage = new SendMessage(groupId, nym + ": " + update.getMessage().getText());

        try {
            Message sentMessage = telegramClient.execute(sendMessage);
            sentMessages.computeIfAbsent(nym, _ -> new ArrayList<>()).add(sentMessage.getMessageId());
        } catch(TelegramApiException e) {
            log.error("Error forwarding message", e);
        }
    }

    private void forwardPhoto(Update update, String userName) {
        String nym = NymGenerator.getNym(userName);
        if(bannedNyms.contains(nym)) {
            sendBannedMessage(update.getMessage().getChatId());
            return;
        }

        PhotoSize photoSize = update.getMessage().getPhoto().getFirst();
        InputFile inputFile = new InputFile(photoSize.getFileId());
        SendPhoto sendPhoto = new SendPhoto(groupId, inputFile);
        sendPhoto.setCaption(nym + ": " + (update.getMessage().getCaption() == null ? "" : update.getMessage().getCaption()));

        try {
            Message sentMessage = telegramClient.execute(sendPhoto);
            sentMessages.computeIfAbsent(nym, _ -> new ArrayList<>()).add(sentMessage.getMessageId());
        } catch(TelegramApiException e) {
            log.error("Error forwarding photo", e);
        }
    }

    private void sendWelcomeMessage() {
        String welcomeText = """
                ⚠️WARNING: There are scammers impersonating admins in this chat ⚠️
                
                If you are a new user, you are STRONGLY RECOMMENDED to send messages anonymously via the @NewSparrowWalletUser bot.
                Your messages will be forwarded here under a pseudonym.
                
                If you ignore this advice, expect to be contacted by several scammers impersonating admins. YOU HAVE BEEN WARNED.
                """;

        SendMessage welcomeMessage = new SendMessage(groupId, welcomeText);
        try {
            telegramClient.execute(welcomeMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending welcome message", e);
        }
    }

    private void sendRateLimitedMessage(Update update) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(update.getMessage().getChatId())
                .text("Too many messages, please wait awhile...").build();

        try {
            telegramClient.execute(sendMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending rate limit message", e);
        }
    }

    private boolean isGroupAdmin(Long userId) {
        try {
            GetChatAdministrators getChatAdministrators = new GetChatAdministrators(groupId);
            List<ChatMember> administrators = telegramClient.execute(getChatAdministrators);

            for(ChatMember admin : administrators) {
                if(admin.getUser().getId().equals(userId)) {
                    return true;
                }
            }
        } catch(TelegramApiException e) {
            log.error("Error determining group administrators", e);
        }

        return false;
    }

    private void banNym(Long chatId, String nym) {
        bannedNyms.add(nym);

        List<Integer> sentMessageIds = sentMessages.get(nym);
        if(sentMessageIds != null) {
            try {
                DeleteMessages deleteMessages = new DeleteMessages(groupId, sentMessageIds);
                telegramClient.execute(deleteMessages);
            } catch(TelegramApiException e) {
                log.error("Error deleting banned nym messages", e);
            }
        }

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(nym + "has been banned, and all forwarded messages have been deleted").build();
        try {
            telegramClient.execute(sendMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending ban confirmation message", e);
        }
    }

    private void unbanNym(Long chatId, String nym) {
        bannedNyms.remove(nym);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(nym + "has been unbanned").build();
        try {
            telegramClient.execute(sendMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending unban confirmation message", e);
        }
    }

    private void sendBannedMessage(Long chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("You have been banned from using this bot.").build();
        try {
            telegramClient.execute(sendMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending ban confirmation message", e);
        }
    }
}
