package com.sparrowwallet.parrot.store;

import com.sparrowwallet.parrot.ForwardedMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "store.implementation", havingValue = "redisStore")
public class RedisStore implements Store {

    @Override
    public void addSentNymMessage(String nym, Integer messageId) {

    }

    @Override
    public List<Integer> getSentNymMessageIds(String nym) {
        return List.of();
    }

    @Override
    public void addSentMessage(Integer messageId, ForwardedMessage forwardedMessage) {

    }

    @Override
    public boolean hasSentMessage(Integer messageId) {
        return false;
    }

    @Override
    public ForwardedMessage getSentMessage(Integer messageId) {
        return null;
    }

    @Override
    public void addSentReply(Integer replyId, Integer messageId) {

    }

    @Override
    public boolean hasSentReply(Integer replyId) {
        return false;
    }

    @Override
    public Integer getSentReply(Integer replyId) {
        return 0;
    }

    @Override
    public void addBannedNym(String nym) {

    }

    @Override
    public boolean hasBannedNym(String nym) {
        return false;
    }

    @Override
    public boolean removeBannedNym(String nym) {
        return false;
    }
}
