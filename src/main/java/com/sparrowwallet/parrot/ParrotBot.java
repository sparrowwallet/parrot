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
    private final Map<String, List<Integer>> sentNymMessages;
    private final Map<Integer, ForwardedMessage> sentMessages;
    private final Map<Integer, Integer> sentReplies;
    private final Set<String> bannedNyms;
    private String groupName;

    public ParrotBot() {
        this.telegramClient = new OkHttpTelegramClient(getBotToken());
        this.rateLimiter = new RateLimiter(5, 60 * 1000);
        this.sentNymMessages = new HashMap<>();
        this.sentMessages = new HashMap<>();
        this.sentReplies = new HashMap<>();
        this.bannedNyms = new HashSet<>();
    }

    @Override
    public void consume(Update update) {
        if(update.hasMessage() && update.getMessage().getChat().isUserChat()) {
            String userName = update.getMessage().getFrom().getUserName();
            if(userName == null) {
                userName = update.getMessage().getFrom().getId().toString();
            }
            if(!rateLimiter.tryAcquire(userName)) {
                sendRateLimitedMessage(update);
            } else if(update.getMessage().hasText()) {
                if(update.getMessage().getText().startsWith("/ban ") && isGroupAdmin(update.getMessage().getFrom().getId())) {
                    banNym(update.getMessage().getChatId(), update.getMessage().getText().substring(5));
                } else if(update.getMessage().getText().startsWith("/unban ") && isGroupAdmin(update.getMessage().getFrom().getId())) {
                    unbanNym(update.getMessage().getChatId(), update.getMessage().getText().substring(7));
                } else if(update.getMessage().getText().startsWith("/start")) {
                    sendStartMessage(update);
                } else {
                    forwardText(update, userName);
                }
            } else if(update.getMessage().hasPhoto()) {
                forwardPhoto(update, userName);
            }
        } else if(update.hasMessage() && update.getMessage().getChat().isSuperGroupChat()) {
            if(update.getMessage().getNewChatMembers() != null && !update.getMessage().getNewChatMembers().isEmpty()) {
                sendWelcomeMessage();
            }
            if(update.getMessage().getReplyToMessage() != null && sentMessages.containsKey(update.getMessage().getReplyToMessage().getMessageId())) {
                forwardReply(update);
            }
        }
    }

    private void sendStartMessage(Update update) {
        String groupName = getGroupName();

        SendMessage sendMessage = SendMessage.builder()
                .chatId(update.getMessage().getChatId())
                .text("Welcome to " + getBotUserName() + ". Use this bot to send messages pseudonymously to @" + groupName + ". " +
                        "Any message or image you send to the bot will be forwarded to the group. " +
                        "In turn, any replies to your message will be forwarded here, and you can also reply to them here to stay anonymous.").build();
        try {
            telegramClient.execute(sendMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending start message", e);
        }
    }

    private void forwardText(Update update, String userName) {
        String nym = NymGenerator.getNym(userName);
        if(bannedNyms.contains(nym)) {
            sendBannedMessage(update.getMessage().getChatId());
            return;
        }

        Integer replyToMessageId = null;
        if(update.getMessage().getReplyToMessage() != null && sentReplies.containsKey(update.getMessage().getReplyToMessage().getMessageId())) {
            replyToMessageId = sentReplies.get(update.getMessage().getReplyToMessage().getMessageId());
        }

        SendMessage sendMessage = SendMessage.builder()
                .text(nym + ": " + update.getMessage().getText())
                .replyToMessageId(replyToMessageId)
                .chatId(getGroupId()).build();

        try {
            Message sentMessage = telegramClient.execute(sendMessage);
            sentNymMessages.computeIfAbsent(nym, _ -> new ArrayList<>()).add(sentMessage.getMessageId());
            sentMessages.put(sentMessage.getMessageId(), new ForwardedMessage(update.getMessage().getChatId(), update.getMessage().getMessageId()));
            if(replyToMessageId == null) {
                sendForwardConfirmation(update.getMessage().getChatId(), false);
            }
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

        Integer replyToMessageId = null;
        if(update.getMessage().getReplyToMessage() != null && sentReplies.containsKey(update.getMessage().getReplyToMessage().getMessageId())) {
            replyToMessageId = sentReplies.get(update.getMessage().getReplyToMessage().getMessageId());
        }

        PhotoSize photoSize = update.getMessage().getPhoto().getFirst();
        InputFile inputFile = new InputFile(photoSize.getFileId());
        SendPhoto sendPhoto = SendPhoto.builder()
                .photo(inputFile)
                .caption(update.getMessage().getCaption() == null ? "From " + nym : nym + ": " + update.getMessage().getCaption())
                .replyToMessageId(replyToMessageId)
                .chatId(getGroupId()).build();

        try {
            Message sentMessage = telegramClient.execute(sendPhoto);
            sentNymMessages.computeIfAbsent(nym, _ -> new ArrayList<>()).add(sentMessage.getMessageId());
            sentMessages.put(sentMessage.getMessageId(), new ForwardedMessage(update.getMessage().getChatId(), update.getMessage().getMessageId()));
            if(replyToMessageId == null) {
                sendForwardConfirmation(update.getMessage().getChatId(), true);
            }
        } catch(TelegramApiException e) {
            log.error("Error forwarding photo", e);
        }
    }

    private void sendForwardConfirmation(Long chatId, boolean photo) {
        String groupName = getGroupName();

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Your " + (photo ? "image" : "message") + " has been forwarded to @" + groupName + ". " +
                        "Any responses will be forwarded here, and you can reply to them here to continue the conversation. " +
                        "Please be patient, this is a community group and not a paid service.").build();
        try {
            telegramClient.execute(sendMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending forwarded confirmation message", e);
        }
    }

    private void forwardReply(Update update) {
        ForwardedMessage forwardedMessage = sentMessages.get(update.getMessage().getReplyToMessage().getMessageId());

        if(update.getMessage().hasText()) {
            SendMessage replyMessage = SendMessage.builder()
                    .text(update.getMessage().getText())
                    .replyToMessageId(forwardedMessage.messageId())
                    .chatId(forwardedMessage.chatId()).build();

            try {
                Message sentMessage = telegramClient.execute(replyMessage);
                sentReplies.put(sentMessage.getMessageId(), update.getMessage().getMessageId());
            } catch(TelegramApiException e) {
                log.error("Error sending reply to forwarded message", e);
            }
        } else if(update.getMessage().hasPhoto()) {
            PhotoSize photoSize = update.getMessage().getPhoto().getFirst();
            InputFile inputFile = new InputFile(photoSize.getFileId());
            SendPhoto replyPhoto = SendPhoto.builder()
                    .photo(inputFile)
                    .caption(update.getMessage().getCaption())
                    .replyToMessageId(forwardedMessage.messageId())
                    .chatId(forwardedMessage.chatId()).build();

            try {
                Message sentMessage = telegramClient.execute(replyPhoto);
                sentReplies.put(sentMessage.getMessageId(), update.getMessage().getMessageId());
            } catch(TelegramApiException e) {
                log.error("Error sending reply to forwarded message", e);
            }
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

        List<Integer> sentMessageIds = sentNymMessages.get(nym);
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

    private String getGroupName() {
        if(groupName == null) {
            try {
                GetChat getChat = new GetChat(getGroupId());
                Chat chat = telegramClient.execute(getChat);
                groupName = chat.getUserName();
            } catch(TelegramApiException e) {
                log.error("Error getting group name", e);
            }
        }

        return groupName;
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

    private record ForwardedMessage(Long chatId, Integer messageId) {}
}
