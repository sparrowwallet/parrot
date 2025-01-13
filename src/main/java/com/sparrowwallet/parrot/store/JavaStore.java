package com.sparrowwallet.parrot.store;

import com.sparrowwallet.parrot.ForwardedMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConditionalOnProperty(name = "store.implementation", havingValue = "javaStore")
public class JavaStore implements Store {
    private final Map<String, List<Integer>> sentNymMessages = new HashMap<>();
    private final Map<Integer, Integer> forwardedMessages = new HashMap<>();
    private final Map<Integer, ForwardedMessage> sentMessages = new HashMap<>();
    private final Map<Integer, Integer> sentReplies = new HashMap<>();
    private final Set<String> bannedNyms = new HashSet<>();
    private final Map<Long, Long> newUserTimestamps = new HashMap<>();

    @Override
    public void addSentNymMessage(String nym, Integer messageId) {
        sentNymMessages.computeIfAbsent(nym, _ -> new ArrayList<>()).add(messageId);
    }

    @Override
    public List<Integer> getSentNymMessageIds(String nym) {
        return sentNymMessages.get(nym);
    }

    @Override
    public void addForwardedMessage(Integer messageId, Integer forwardedMessageId) {
        forwardedMessages.put(messageId, forwardedMessageId);
    }

    @Override
    public Integer getForwardedMessageId(Integer messageId) {
        return forwardedMessages.get(messageId);
    }

    @Override
    public void addSentMessage(Integer messageId, ForwardedMessage forwardedMessage) {
        sentMessages.put(messageId, forwardedMessage);
    }

    @Override
    public boolean hasSentMessage(Integer messageId) {
        return sentMessages.containsKey(messageId);
    }

    @Override
    public ForwardedMessage getSentMessage(Integer messageId) {
        return sentMessages.get(messageId);
    }

    @Override
    public void addSentReply(Integer replyId, Integer messageId) {
        sentReplies.put(replyId, messageId);
    }

    @Override
    public boolean hasSentReply(Integer replyId) {
        return sentReplies.containsKey(replyId);
    }

    @Override
    public Integer getSentReply(Integer replyId) {
        return sentReplies.get(replyId);
    }

    @Override
    public void addBannedNym(String nym) {
        bannedNyms.add(nym);
    }

    @Override
    public boolean hasBannedNym(String nym) {
        return bannedNyms.contains(nym);
    }

    @Override
    public boolean removeBannedNym(String nym) {
        return bannedNyms.remove(nym);
    }

    @Override
    public void newUserAdded(Long userId, Long timestamp) {
        newUserTimestamps.put(userId, timestamp);
    }

    @Override
    public Long getNewUserTimestamp(Long userId) {
        return newUserTimestamps.get(userId);
    }

    @Override
    public void close() {
        //nothing required
    }
}
