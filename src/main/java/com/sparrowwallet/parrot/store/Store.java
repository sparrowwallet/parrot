package com.sparrowwallet.parrot.store;

import com.sparrowwallet.parrot.ForwardedMessage;

import java.io.Closeable;
import java.util.List;

public interface Store extends Closeable {
    void addSentNymMessage(String nym, Integer messageId);
    List<Integer> getSentNymMessageIds(String nym);
    void addForwardedMessage(Integer messageId, Integer forwardedMessageId);
    Integer getForwardedMessageId(Integer messageId);
    void addSentMessage(Integer messageId, ForwardedMessage forwardedMessage);
    boolean hasSentMessage(Integer messageId);
    ForwardedMessage getSentMessage(Integer messageId);
    void addSentReply(Integer replyId, Integer messageId);
    boolean hasSentReply(Integer replyId);
    Integer getSentReply(Integer replyId);
    void addBannedNym(String nym);
    boolean hasBannedNym(String nym);
    boolean removeBannedNym(String nym);
    void newUserAdded(Long userId, Long timestamp);
    Long getNewUserTimestamp(Long userId);
}
