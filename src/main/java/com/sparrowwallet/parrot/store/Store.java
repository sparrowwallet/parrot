package com.sparrowwallet.parrot.store;

import com.sparrowwallet.parrot.ForwardedMessage;

import java.util.List;

public interface Store {
    void addSentNymMessage(String nym, Integer messageId);
    List<Integer> getSentNymMessageIds(String nym);
    void addSentMessage(Integer messageId, ForwardedMessage forwardedMessage);
    boolean hasSentMessage(Integer messageId);
    ForwardedMessage getSentMessage(Integer messageId);
    void addSentReply(Integer replyId, Integer messageId);
    boolean hasSentReply(Integer replyId);
    Integer getSentReply(Integer replyId);
    void addBannedNym(String nym);
    boolean hasBannedNym(String nym);
    boolean removeBannedNym(String nym);
}
