package com.sparrowwallet.parrot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.*;

@Component
public class ParrotBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(ParrotBot.class);
    public static final String PARROT_BOT_TOKEN = "PARROT_BOT_TOKEN";
    public static final String PARROT_GROUP_ID = "PARROT_GROUP_ID";
    public static final String PARROT_BOT_USERNAME = "PARROT_BOT_USERNAME";

    private final TelegramClient telegramClient;
    private final RateLimiter rateLimiter;
    private final Map<String, List<Integer>> sentMessages;
    private final Set<String> bannedNyms;
    private String groupName;

    public ParrotBot() {
        this.telegramClient = new OkHttpTelegramClient(getBotToken());
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

        SendMessage sendMessage = new SendMessage(getGroupId(), nym + ": " + update.getMessage().getText());

        try {
            Message sentMessage = telegramClient.execute(sendMessage);
            sentMessages.computeIfAbsent(nym, _ -> new ArrayList<>()).add(sentMessage.getMessageId());
            sendForwardConfirmation(update.getMessage().getChatId());
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
        SendPhoto sendPhoto = new SendPhoto(getGroupId(), inputFile);
        sendPhoto.setCaption(nym + ": " + (update.getMessage().getCaption() == null ? "" : update.getMessage().getCaption()));

        try {
            Message sentMessage = telegramClient.execute(sendPhoto);
            sentMessages.computeIfAbsent(nym, _ -> new ArrayList<>()).add(sentMessage.getMessageId());
            sendForwardConfirmation(update.getMessage().getChatId());
        } catch(TelegramApiException e) {
            log.error("Error forwarding photo", e);
        }
    }

    private void sendForwardConfirmation(Long chatId) {
        if(groupName == null) {
            try {
                GetChat getChat = new GetChat(getGroupId());
                Chat chat = telegramClient.execute(getChat);
                groupName = chat.getTitle();
            } catch(TelegramApiException e) {
                log.error("Error getting group name", e);
            }
        }

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Your message has been forwarded to " + groupName).build();
        try {
            telegramClient.execute(sendMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending forwarded confirmation message", e);
        }
    }

    private void sendWelcomeMessage() {
        String welcomeText = """
                ⚠️WARNING: There are scammers impersonating admins in this chat ⚠️
                
                If you are a new user, you are STRONGLY RECOMMENDED to send messages anonymously via [BOT_NAME].
                Any messages sent to the bot will be forwarded here under a pseudonym.
                
                If you ignore this advice, expect to be contacted by several scammers impersonating admins. YOU HAVE BEEN WARNED.
                """;

        SendMessage welcomeMessage = new SendMessage(getGroupId(), welcomeText.replace("[BOT_NAME]", getBotUserName()));
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
            GetChatAdministrators getChatAdministrators = new GetChatAdministrators(getGroupId());
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
                DeleteMessages deleteMessages = new DeleteMessages(getGroupId(), sentMessageIds);
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

    @Override
    public String getBotToken() {
        return System.getenv(PARROT_BOT_TOKEN);
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    public String getGroupId() {
        return System.getenv(PARROT_GROUP_ID);
    }

    public String getBotUserName() {
        return System.getenv(PARROT_BOT_USERNAME);
    }
}
