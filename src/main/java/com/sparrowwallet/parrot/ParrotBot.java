package com.sparrowwallet.parrot;

import com.sparrowwallet.parrot.store.Store;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionUpdated;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionType;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.*;

@Component
public class ParrotBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(ParrotBot.class);
    public static final String PARROT_BOT_TOKEN = "PARROT_BOT_TOKEN";
    public static final String PARROT_GROUP_ID = "PARROT_GROUP_ID";
    public static final String PARROT_BOT_USERNAME = "PARROT_BOT_USERNAME";
    public static final String PARROT_COOLDOWN_PERIOD_MINUTES = "PARROT_COOLDOWN_PERIOD_MINUTES";
    public static final String MEMBER = "member";
    public static final String LEFT = "left";
    public static final String KICKED = "kicked";

    private final Store store;
    private final TelegramClient telegramClient;
    private final RateLimiter rateLimiter;
    private String groupName;

    public ParrotBot(Store store) {
        this.store = store;
        this.telegramClient = new OkHttpTelegramClient(getBotToken());
        this.rateLimiter = new RateLimiter(5, 60 * 1000);
    }

    @Override
    public void consume(Update update) {
        if(update.hasMessage() && update.getMessage().getChat().isUserChat()) {
            Long userId = update.getMessage().getFrom().getId();

            if(!rateLimiter.tryAcquire(userId)) {
                sendRateLimitedMessage(update);
            } else if(update.getMessage().hasText()) {
                if(update.getMessage().getText().startsWith("/ban ") && isGroupAdmin(update.getMessage().getFrom().getId())) {
                    banNym(update.getMessage().getChatId(), update.getMessage().getText().substring(5));
                } else if(update.getMessage().getText().startsWith("/unban ") && isGroupAdmin(update.getMessage().getFrom().getId())) {
                    unbanNym(update.getMessage().getChatId(), update.getMessage().getText().substring(7));
                } else if(update.getMessage().getText().startsWith("/start")) {
                    sendStartMessage(update);
                } else {
                    forwardText(update, userId.toString());
                }
            } else if(update.getMessage().hasPhoto()) {
                forwardPhoto(update, userId.toString());
            }
        } else if(update.hasEditedMessage() && update.getEditedMessage().hasText() && update.getEditedMessage().getChat().isUserChat()) {
            forwardEditedMessage(update.getEditedMessage());
        } else if(update.getMessageReaction() != null && update.getMessageReaction().getChat().isUserChat()) {
            forwardReaction(update.getMessageReaction());
        } else if(update.hasMessage() && update.getMessage().getChat().isSuperGroupChat()) {
            if(update.getMessage().getReplyToMessage() != null && store.hasSentMessage(update.getMessage().getReplyToMessage().getMessageId())) {
                forwardReply(update, update.getMessage().getFrom().getFirstName());
            }
        }
        if(update.hasChatMember() && update.getChatMember().getChat().isSuperGroupChat()) {
            if(LEFT.equals(update.getChatMember().getOldChatMember().getStatus()) && MEMBER.equals(update.getChatMember().getNewChatMember().getStatus())) {
                sendWelcomeMessage();
                restrictNewUser(update.getChatMember().getFrom().getId());
            } else if(!KICKED.equals(update.getChatMember().getOldChatMember().getStatus()) && KICKED.equals(update.getChatMember().getNewChatMember().getStatus())) {
                String nym = NymGenerator.getNym(update.getChatMember().getNewChatMember().getUser().getId().toString());
                banNym(null, nym);
            } else if(KICKED.equals(update.getChatMember().getOldChatMember().getStatus()) && !KICKED.equals(update.getChatMember().getNewChatMember().getStatus())) {
                String nym = NymGenerator.getNym(update.getChatMember().getNewChatMember().getUser().getId().toString());
                unbanNym(null, nym);
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

    private void forwardText(Update update, String userId) {
        String nym = NymGenerator.getNym(userId);
        if(store.hasBannedNym(nym)) {
            sendBannedMessage(update.getMessage().getChatId());
            return;
        }

        Integer replyToMessageId = null;
        if(update.getMessage().getReplyToMessage() != null && store.hasSentReply(update.getMessage().getReplyToMessage().getMessageId())) {
            replyToMessageId = store.getSentReply(update.getMessage().getReplyToMessage().getMessageId());
        }

        SendMessage sendMessage = SendMessage.builder()
                .text(nym + ": " + update.getMessage().getText())
                .replyToMessageId(replyToMessageId)
                .chatId(getGroupId()).build();

        try {
            Message sentMessage = telegramClient.execute(sendMessage);
            store.addSentNymMessage(nym, sentMessage.getMessageId());
            store.addForwardedMessage(update.getMessage().getMessageId(), sentMessage.getMessageId());
            store.addSentMessage(sentMessage.getMessageId(), new ForwardedMessage(update.getMessage().getChatId(), update.getMessage().getMessageId()));
            if(replyToMessageId == null) {
                sendForwardConfirmation(update.getMessage().getChatId(), false);
            }
        } catch(TelegramApiException e) {
            log.error("Error forwarding message", e);
        }
    }

    private void forwardPhoto(Update update, String userId) {
        String nym = NymGenerator.getNym(userId);
        if(store.hasBannedNym(nym)) {
            sendBannedMessage(update.getMessage().getChatId());
            return;
        }

        Integer replyToMessageId = null;
        if(update.getMessage().getReplyToMessage() != null && store.hasSentReply(update.getMessage().getReplyToMessage().getMessageId())) {
            replyToMessageId = store.getSentReply(update.getMessage().getReplyToMessage().getMessageId());
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
            store.addSentNymMessage(nym, sentMessage.getMessageId());
            store.addForwardedMessage(update.getMessage().getMessageId(), sentMessage.getMessageId());
            store.addSentMessage(sentMessage.getMessageId(), new ForwardedMessage(update.getMessage().getChatId(), update.getMessage().getMessageId()));
            if(replyToMessageId == null) {
                sendForwardConfirmation(update.getMessage().getChatId(), true);
            }
        } catch(TelegramApiException e) {
            log.error("Error forwarding photo", e);
        }
    }

    private void forwardEditedMessage(Message editedMessage) {
        Integer forwardedMessageId = store.getForwardedMessageId(editedMessage.getMessageId());
        if(forwardedMessageId != null) {
            EditMessageText editMessageText = EditMessageText.builder()
                    .messageId(forwardedMessageId)
                    .chatId(getGroupId())
                    .text(editedMessage.getText()).build();

            try {
                telegramClient.execute(editMessageText);
            } catch(TelegramApiException e) {
                log.error("Error forwarding message edit", e);
            }
        }
    }

    private void forwardReaction(MessageReactionUpdated messageReactionUpdated) {
        Integer forwardedMessageId = store.getForwardedMessageId(messageReactionUpdated.getMessageId());
        if(forwardedMessageId != null) {
            List<ReactionType> reactionTypes = new ArrayList<>(messageReactionUpdated.getNewReaction());
            reactionTypes.removeAll(messageReactionUpdated.getOldReaction());

            if(!reactionTypes.isEmpty()) {
                SetMessageReaction setMessageReaction = SetMessageReaction.builder()
                        .messageId(forwardedMessageId)
                        .chatId(getGroupId())
                        .reactionTypes(reactionTypes).build();
                try {
                    telegramClient.execute(setMessageReaction);
                } catch(TelegramApiException e) {
                    log.error("Error forwarding message reactions", e);
                }
            }
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

    private void forwardReply(Update update, String userName) {
        ForwardedMessage forwardedMessage = store.getSentMessage(update.getMessage().getReplyToMessage().getMessageId());

        if(update.getMessage().hasText()) {
            SendMessage replyMessage = SendMessage.builder()
                    .text(userName + ": " + update.getMessage().getText())
                    .replyToMessageId(forwardedMessage.messageId())
                    .chatId(forwardedMessage.chatId()).build();

            try {
                Message sentMessage = telegramClient.execute(replyMessage);
                store.addSentReply(sentMessage.getMessageId(), update.getMessage().getMessageId());
            } catch(TelegramApiException e) {
                log.error("Error sending reply to forwarded message", e);
            }
        } else if(update.getMessage().hasPhoto()) {
            PhotoSize photoSize = update.getMessage().getPhoto().getFirst();
            InputFile inputFile = new InputFile(photoSize.getFileId());
            SendPhoto replyPhoto = SendPhoto.builder()
                    .photo(inputFile)
                    .caption(update.getMessage().getCaption() == null ? "From " + userName : userName + ": " + update.getMessage().getCaption())
                    .replyToMessageId(forwardedMessage.messageId())
                    .chatId(forwardedMessage.chatId()).build();

            try {
                Message sentMessage = telegramClient.execute(replyPhoto);
                store.addSentReply(sentMessage.getMessageId(), update.getMessage().getMessageId());
            } catch(TelegramApiException e) {
                log.error("Error sending reply to forwarded message", e);
            }
        }
    }

    private void sendWelcomeMessage() {
        String welcomeText = """
                ⚠️WARNING: There are scammers impersonating admins in this chat ⚠️
                
                If you are a new user, you are STRONGLY RECOMMENDED to send messages anonymously via [BOT_NAME]. Any messages sent to the bot will be forwarded here under a pseudonym.
                
                """;

        Integer cooldownPeriodMinutes = getCooldownPeriodMinutes();
        if(cooldownPeriodMinutes != null && cooldownPeriodMinutes > 0) {
            welcomeText += """
                To ensure you have read and considered this advice, you will not be able to post directly to this chat for [COOLDOWN_PERIOD]. You can however ask questions using [BOT_NAME] during this period.
                
                """.replace("[COOLDOWN_PERIOD]", cooldownPeriodMinutes > 1 ? cooldownPeriodMinutes + " minutes" : cooldownPeriodMinutes + "minute");
        }

        welcomeText += """
                If you ignore this advice, you may be contacted by scammers impersonating admins. YOU HAVE BEEN WARNED.
                """;

        SendMessage welcomeMessage = new SendMessage(getGroupId(), welcomeText.replace("[BOT_NAME]", getBotUserName()));
        try {
            telegramClient.execute(welcomeMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending welcome message", e);
        }
    }

    private void restrictNewUser(long userId) {
        Integer cooldownPeriodMinutes = getCooldownPeriodMinutes();
        if(cooldownPeriodMinutes != null && cooldownPeriodMinutes > 0) {
            ChatPermissions noSendPermissions = ChatPermissions.builder()
                    .canSendMessages(false)
                    .canSendPhotos(false)
                    .canSendPolls(false)
                    .canSendAudios(false)
                    .canSendVideos(false)
                    .canSendDocuments(false)
                    .canSendVoiceNotes(false)
                    .canSendOtherMessages(false)
                    .canAddWebPagePreviews(false).build();

            int untilDate = (int) (System.currentTimeMillis() / 1000) + (cooldownPeriodMinutes * 60);

            RestrictChatMember restrictChatMember = RestrictChatMember.builder()
                    .chatId(getGroupId())
                    .userId(userId)
                    .permissions(noSendPermissions)
                    .untilDate(untilDate).build();

            try {
                Boolean result = telegramClient.execute(restrictChatMember); // Telegram will lift restrictions automatically
                if(result == null || !result) {
                    log.error("Bot does not have permission to restrict new users, add Ban Users permission");
                }
            } catch(Exception e) {
                log.error("Error restricting new user chat for cooldown period", e);
            }
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
        store.addBannedNym(nym);

        List<Integer> sentMessageIds = store.getSentNymMessageIds(nym);
        if(sentMessageIds != null && !sentMessageIds.isEmpty()) {
            if(sentMessageIds.size() > 100) {
                sentMessageIds = new ArrayList<>(sentMessageIds.subList(sentMessageIds.size() - 100, sentMessageIds.size()));
            }

            try {
                DeleteMessages deleteMessages = new DeleteMessages(getGroupId(), sentMessageIds);
                Boolean result = telegramClient.execute(deleteMessages);
                if(result == null || !result) {
                    log.error("Bot does not have permission to delete messages from banned bym, add Delete Messages permission");
                }
            } catch(TelegramApiException e) {
                log.error("Error deleting banned nym messages", e);
            }
        }

        if(chatId != null) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(nym + " has been banned, and all forwarded messages have been deleted").build();
            try {
                telegramClient.execute(sendMessage);
            } catch(TelegramApiException e) {
                log.error("Error sending ban confirmation message", e);
            }
        }
    }

    private void unbanNym(Long chatId, String nym) {
        store.removeBannedNym(nym);

        if(chatId != null) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(nym + " has been unbanned").build();
            try {
                telegramClient.execute(sendMessage);
            } catch(TelegramApiException e) {
                log.error("Error sending unban confirmation message", e);
            }
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

    public Integer getCooldownPeriodMinutes() {
        String strCooldown = System.getenv(PARROT_COOLDOWN_PERIOD_MINUTES);
        if(strCooldown != null) {
            try {
                return Integer.parseInt(strCooldown);
            } catch(NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    @PreDestroy
    public void shutdown() throws Exception {
        log.info("Shutting down");
        store.close();
    }
}
