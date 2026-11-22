package com.sparrowwallet.parrot;

import com.sparrowwallet.parrot.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
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
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberBanned;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberLeft;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberRestricted;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionUpdated;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionType;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.*;

public class ParrotBot extends DefaultLongPollingUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(ParrotBot.class);
    public static final String PARROT_BOT_TOKEN = "PARROT_BOT_TOKEN";
    public static final String PARROT_GROUP_ID = "PARROT_GROUP_ID";
    public static final String PARROT_BOT_USERNAME = "PARROT_BOT_USERNAME";
    public static final String PARROT_COOLDOWN_PERIOD_MINUTES = "PARROT_COOLDOWN_PERIOD_MINUTES";
    public static final String PARROT_STORE = "PARROT_STORE";
    public static final String PARROT_NYM_SECRET = "PARROT_NYM_SECRET";
    public static final String MEMBER = "member";
    public static final String KICKED = "kicked";
    public static final String BAN_COMMAND = "/ban";
    public static final String UNBAN_COMMAND = "/unban";
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_CAPTION_LENGTH = 1024;
    private static final String FORWARD_FAILED = " could not be forwarded to the group, please try again later.";

    private final Store store;
    private final TelegramClient telegramClient;
    private final RateLimiter rateLimiter;
    private String groupName;

    private static final long WELCOME_MESSAGE_MIN_INTERVAL = 60 * 1000;
    private long lastWelcomeMessageTime;

    private static final int MAX_TRACKED_ALBUMS = 1000;
    private final Map<Long, Album> lastAlbums = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Album> eldest) {
            return size() > MAX_TRACKED_ALBUMS;
        }
    };

    public ParrotBot(Store store) {
        this.store = store;
        this.telegramClient = new OkHttpTelegramClient(getBotToken());
        this.rateLimiter = new RateLimiter(5, 60 * 1000);
    }

    @Override
    public void consume(Update update) {
        try {
            handleUpdate(update);
        } catch(RuntimeException e) {
            log.error("Error handling update " + update.getUpdateId(), e);
        }
    }

    private void handleUpdate(Update update) {
        if(update.hasMessage() && update.getMessage().getChat().isUserChat()) {
            Long userId = update.getMessage().getFrom().getId();

            //Each photo in an album arrives as a separate message: rate limit and confirm the album once, as its first photo
            String mediaGroupId = update.getMessage().getMediaGroupId();
            Album lastAlbum = mediaGroupId == null ? null : lastAlbums.get(userId);
            boolean albumContinuation = lastAlbum != null && lastAlbum.mediaGroupId().equals(mediaGroupId);

            RateLimiter.Result rateLimit;
            if(albumContinuation) {
                rateLimit = lastAlbum.rateLimit() == RateLimiter.Result.ACQUIRED ? RateLimiter.Result.ACQUIRED : RateLimiter.Result.REJECTED_AGAIN;
            } else {
                rateLimit = rateLimiter.tryAcquire(userId);
                if(mediaGroupId != null) {
                    lastAlbums.put(userId, new Album(mediaGroupId, rateLimit));
                }
            }

            if(rateLimit != RateLimiter.Result.ACQUIRED) {
                if(rateLimit == RateLimiter.Result.REJECTED) {
                    sendRateLimitedMessage(update);
                }
            } else if(update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                String command = text.split("\\s+", 2)[0];
                if((BAN_COMMAND.equals(command) || UNBAN_COMMAND.equals(command)) && isGroupAdmin(userId)) {
                    handleBanCommand(update.getMessage().getChatId(), BAN_COMMAND.equals(command), text.substring(command.length()).strip());
                } else if(update.getMessage().getText().startsWith("/start")) {
                    sendStartMessage(update);
                } else {
                    forwardText(update, userId.toString());
                }
            } else if(update.getMessage().hasPhoto()) {
                forwardPhoto(update, userId.toString(), albumContinuation);
            }
        } else if(update.hasEditedMessage() && update.getEditedMessage().hasText() && update.getEditedMessage().getChat().isUserChat()) {
            forwardEditedMessage(update.getEditedMessage());
        } else if(update.getMessageReaction() != null && update.getMessageReaction().getChat().isUserChat()) {
            forwardReaction(update.getMessageReaction());
        } else if(update.hasMessage() && isParrotGroup(update.getMessage().getChat())) {
            if(update.getMessage().getReplyToMessage() != null && store.hasSentMessage(update.getMessage().getReplyToMessage().getMessageId())) {
                forwardReply(update, update.getMessage().getFrom().getFirstName());
            }
        }
        if(update.hasChatMember() && isParrotGroup(update.getChatMember().getChat())) {
            ChatMember oldChatMember = update.getChatMember().getOldChatMember();
            ChatMember newChatMember = update.getChatMember().getNewChatMember();
            if(!isInChat(oldChatMember) && isInChat(newChatMember)) {
                replaceWelcomeMessage();
                //Only restrict plain members: overwriting an existing restriction with the timed cooldown would lift it when the cooldown expires
                if(MEMBER.equals(newChatMember.getStatus())) {
                    restrictNewUser(newChatMember.getUser().getId());
                }
            }

            if(!KICKED.equals(oldChatMember.getStatus()) && KICKED.equals(newChatMember.getStatus())) {
                String nym = NymGenerator.getNym(newChatMember.getUser().getId().toString());
                banNym(null, nym);
            } else if(KICKED.equals(oldChatMember.getStatus()) && !KICKED.equals(newChatMember.getStatus())) {
                String nym = NymGenerator.getNym(newChatMember.getUser().getId().toString());
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

        String text = nym + ": " + update.getMessage().getText();
        if(text.length() > MAX_MESSAGE_LENGTH) {
            sendPrivateMessage(update.getMessage().getChatId(), "Your message is too long to forward. Please keep it under " +
                    (MAX_MESSAGE_LENGTH - nym.length() - 2) + " characters, or split it into several messages.");
            return;
        }

        SendMessage sendMessage = SendMessage.builder()
                .text(text)
                .replyToMessageId(replyToMessageId)
                .allowSendingWithoutReply(true)
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
            sendPrivateMessage(update.getMessage().getChatId(), "Your message" + FORWARD_FAILED);
        }
    }

    private void forwardPhoto(Update update, String userId, boolean albumContinuation) {
        String nym = NymGenerator.getNym(userId);
        if(store.hasBannedNym(nym)) {
            if(!albumContinuation) {
                sendBannedMessage(update.getMessage().getChatId());
            }
            return;
        }

        Integer replyToMessageId = null;
        if(update.getMessage().getReplyToMessage() != null && store.hasSentReply(update.getMessage().getReplyToMessage().getMessageId())) {
            replyToMessageId = store.getSentReply(update.getMessage().getReplyToMessage().getMessageId());
        }

        String caption = update.getMessage().getCaption() == null ? "From " + nym : nym + ": " + update.getMessage().getCaption();
        if(caption.length() > MAX_CAPTION_LENGTH) {
            sendPrivateMessage(update.getMessage().getChatId(), "Your image caption is too long to forward. Please keep it under " +
                    (MAX_CAPTION_LENGTH - nym.length() - 2) + " characters, or send the image and text as separate messages.");
            return;
        }

        PhotoSize photoSize = update.getMessage().getPhoto().getLast();
        InputFile inputFile = new InputFile(photoSize.getFileId());
        SendPhoto sendPhoto = SendPhoto.builder()
                .photo(inputFile)
                .caption(caption)
                .replyToMessageId(replyToMessageId)
                .allowSendingWithoutReply(true)
                .chatId(getGroupId()).build();

        try {
            Message sentMessage = telegramClient.execute(sendPhoto);
            store.addSentNymMessage(nym, sentMessage.getMessageId());
            store.addForwardedMessage(update.getMessage().getMessageId(), sentMessage.getMessageId());
            store.addSentMessage(sentMessage.getMessageId(), new ForwardedMessage(update.getMessage().getChatId(), update.getMessage().getMessageId()));
            if(replyToMessageId == null && !albumContinuation) {
                sendForwardConfirmation(update.getMessage().getChatId(), true);
            }
        } catch(TelegramApiException e) {
            log.error("Error forwarding photo", e);
            sendPrivateMessage(update.getMessage().getChatId(), "Your image" + FORWARD_FAILED);
        }
    }

    private void forwardEditedMessage(Message editedMessage) {
        String nym = NymGenerator.getNym(editedMessage.getFrom().getId().toString());
        if(store.hasBannedNym(nym)) {
            sendBannedMessage(editedMessage.getChatId());
            return;
        }

        Integer forwardedMessageId = store.getForwardedMessageId(editedMessage.getMessageId());
        if(forwardedMessageId != null) {
            String text = nym + ": " + editedMessage.getText();
            if(text.length() > MAX_MESSAGE_LENGTH) {
                sendPrivateMessage(editedMessage.getChatId(), "Your edited message is too long to forward. Please keep it under " +
                        (MAX_MESSAGE_LENGTH - nym.length() - 2) + " characters.");
                return;
            }

            EditMessageText editMessageText = EditMessageText.builder()
                    .messageId(forwardedMessageId)
                    .chatId(getGroupId())
                    .text(text).build();

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
        Message message = update.getMessage();
        ForwardedMessage forwardedMessage = store.getSentMessage(message.getReplyToMessage().getMessageId());

        try {
            if(message.hasText()) {
                SendMessage replyMessage = SendMessage.builder()
                        .text(truncate(userName + ": " + message.getText(), MAX_MESSAGE_LENGTH))
                        .replyToMessageId(forwardedMessage.messageId())
                        .allowSendingWithoutReply(true)
                        .chatId(forwardedMessage.chatId()).build();
                Message sentMessage = telegramClient.execute(replyMessage);
                store.addSentReply(sentMessage.getMessageId(), message.getMessageId());
            } else if(message.hasPhoto()) {
                PhotoSize photoSize = message.getPhoto().getLast();
                SendPhoto replyPhoto = SendPhoto.builder()
                        .photo(new InputFile(photoSize.getFileId()))
                        .caption(truncate(getReplyCaption(message, userName), MAX_CAPTION_LENGTH))
                        .replyToMessageId(forwardedMessage.messageId())
                        .allowSendingWithoutReply(true)
                        .chatId(forwardedMessage.chatId()).build();
                Message sentMessage = telegramClient.execute(replyPhoto);
                store.addSentReply(sentMessage.getMessageId(), message.getMessageId());
            } else if(message.hasVideo() || message.hasAnimation() || message.hasDocument() || message.hasAudio() || message.hasVoice()) {
                Integer copiedMessageId = copyReply(message, forwardedMessage, forwardedMessage.messageId(), truncate(getReplyCaption(message, userName), MAX_CAPTION_LENGTH));
                store.addSentReply(copiedMessageId, message.getMessageId());
            } else {
                //Stickers, video notes, polls and similar cannot be captioned, so attribute them with a preceding message
                SendMessage attribution = SendMessage.builder()
                        .text(userName + " replied:")
                        .replyToMessageId(forwardedMessage.messageId())
                        .allowSendingWithoutReply(true)
                        .chatId(forwardedMessage.chatId()).build();
                Message attributionMessage = telegramClient.execute(attribution);
                store.addSentReply(attributionMessage.getMessageId(), message.getMessageId());

                Integer copiedMessageId = copyReply(message, forwardedMessage, attributionMessage.getMessageId(), null);
                store.addSentReply(copiedMessageId, message.getMessageId());
            }
        } catch(TelegramApiException e) {
            log.error("Error sending reply to forwarded message", e);
        }
    }

    private Integer copyReply(Message message, ForwardedMessage forwardedMessage, Integer replyToMessageId, String caption) throws TelegramApiException {
        CopyMessage copyMessage = CopyMessage.builder()
                .fromChatId(message.getChatId())
                .messageId(message.getMessageId())
                .caption(caption)
                .replyToMessageId(replyToMessageId)
                .allowSendingWithoutReply(true)
                .chatId(forwardedMessage.chatId()).build();
        return telegramClient.execute(copyMessage).getMessageId().intValue();
    }

    private static String getReplyCaption(Message message, String userName) {
        return message.getCaption() == null ? "From " + userName : userName + ": " + message.getCaption();
    }

    private static String truncate(String text, int maxLength) {
        if(text.length() <= maxLength) {
            return text;
        }

        int end = maxLength - 1;
        if(Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }

        return text.substring(0, end) + "…";
    }

    /**
     * Posts a new welcome message and deletes the previous one, so only the latest is visible. During a burst of joins the
     * welcome is reposted at most once per minute, to avoid flooding the group and hitting Telegram's rate limits.
     */
    private void replaceWelcomeMessage() {
        long now = System.currentTimeMillis();
        if(now - lastWelcomeMessageTime < WELCOME_MESSAGE_MIN_INTERVAL) {
            return;
        }
        lastWelcomeMessageTime = now;

        String welcomeText = """
                ⚠️WARNING: There are scammers impersonating admins in this chat ⚠️
                
                If you are a new user, you are STRONGLY RECOMMENDED to send messages anonymously via [BOT_NAME]. Any messages sent to the bot will be forwarded here under a pseudonym.
                
                """;

        Integer cooldownPeriodMinutes = getCooldownPeriodMinutes();
        if(cooldownPeriodMinutes != null && cooldownPeriodMinutes > 0) {
            welcomeText += """
                To ensure you have read and considered this advice, you will not be able to post directly to this chat for [COOLDOWN_PERIOD]. You can however ask questions using [BOT_NAME] during this period.
                
                """.replace("[COOLDOWN_PERIOD]", cooldownPeriodMinutes > 1 ? cooldownPeriodMinutes + " minutes" : cooldownPeriodMinutes + " minute");
        }

        welcomeText += """
                If you ignore this advice, you may be contacted by scammers impersonating admins. YOU HAVE BEEN WARNED.
                """;

        SendMessage welcomeMessage = new SendMessage(getGroupId(), welcomeText.replace("[BOT_NAME]", getBotUserName()));
        try {
            Message sentMessage = telegramClient.execute(welcomeMessage);
            Integer previousMessageId = store.getWelcomeMessageId();
            store.setWelcomeMessageId(sentMessage.getMessageId());
            if(previousMessageId != null) {
                deleteWelcomeMessage(previousMessageId);
            }
        } catch(TelegramApiException e) {
            log.error("Error sending welcome message", e);
        }
    }

    private void deleteWelcomeMessage(Integer messageId) {
        try {
            //DeleteMessages skips messages that no longer exist or are too old to delete, rather than failing
            Boolean result = telegramClient.execute(new DeleteMessages(getGroupId(), List.of(messageId)));
            if(result == null || !result) {
                log.error("Bot does not have permission to delete old welcome messages, add Delete Messages permission");
            }
        } catch(TelegramApiException e) {
            log.error("Error deleting old welcome message", e);
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
                .text("Too many messages, please wait a minute before sending again. Messages sent until then will not be forwarded.").build();

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

    private void handleBanCommand(Long chatId, boolean ban, String argument) {
        String command = ban ? BAN_COMMAND : UNBAN_COMMAND;
        if(argument.isEmpty()) {
            sendPrivateMessage(chatId, "Usage: " + command + " #NymName");
            return;
        }

        String nym = NymGenerator.toCanonicalNym(argument);
        if(nym == null) {
            sendPrivateMessage(chatId, argument + " is not a valid nym (check spelling and capitalisation), nobody has been " + (ban ? "banned" : "unbanned") +
                    ". Usage: " + command + " #NymName");
            return;
        }

        if(ban) {
            banNym(chatId, nym);
        } else {
            unbanNym(chatId, nym);
        }
    }

    private void banNym(Long chatId, String nym) {
        boolean alreadyBanned = store.hasBannedNym(nym);
        store.addBannedNym(nym);

        String deletionResult = "No forwarded messages from this nym were found to delete.";
        List<Integer> sentMessageIds = store.getSentNymMessageIds(nym);
        if(sentMessageIds != null && !sentMessageIds.isEmpty()) {
            if(sentMessageIds.size() > 100) {
                sentMessageIds = new ArrayList<>(sentMessageIds.subList(sentMessageIds.size() - 100, sentMessageIds.size()));
            }

            deletionResult = "Their forwarded messages could not be deleted, check the bot has the Delete Messages permission.";
            try {
                DeleteMessages deleteMessages = new DeleteMessages(getGroupId(), sentMessageIds);
                Boolean result = telegramClient.execute(deleteMessages);
                if(result == null || !result) {
                    log.error("Bot does not have permission to delete messages from banned nym, add Delete Messages permission");
                } else {
                    deletionResult = "Deleted their last " + sentMessageIds.size() + " forwarded message" + (sentMessageIds.size() == 1 ? "" : "s") +
                            " (messages older than 48 hours cannot be deleted by the bot).";
                }
            } catch(TelegramApiException e) {
                log.error("Error deleting banned nym messages", e);
            }
        }

        if(chatId != null) {
            sendPrivateMessage(chatId, nym + (alreadyBanned ? " was already banned. " : " has been banned. ") + deletionResult);
        }
    }

    private void unbanNym(Long chatId, String nym) {
        boolean wasBanned = store.removeBannedNym(nym);

        if(chatId != null) {
            sendPrivateMessage(chatId, nym + (wasBanned ? " has been unbanned." : " was not banned."));
        }
    }

    private void sendPrivateMessage(Long chatId, String text) {
        SendMessage sendMessage = SendMessage.builder().chatId(chatId).text(text).build();
        try {
            telegramClient.execute(sendMessage);
        } catch(TelegramApiException e) {
            log.error("Error sending private message", e);
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

    private static boolean isInChat(ChatMember chatMember) {
        return switch(chatMember) {
            case ChatMemberRestricted restricted -> Boolean.TRUE.equals(restricted.getIsMember());
            case ChatMemberLeft _, ChatMemberBanned _ -> false;
            default -> true;
        };
    }

    private boolean isParrotGroup(Chat chat) {
        return chat.isSuperGroupChat() && chat.getId().toString().equals(getGroupId().trim());
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

    public String getBotToken() {
        return System.getenv(PARROT_BOT_TOKEN);
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

    private record Album(String mediaGroupId, RateLimiter.Result rateLimit) {}
}
