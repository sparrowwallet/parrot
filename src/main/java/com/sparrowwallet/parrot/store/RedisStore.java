package com.sparrowwallet.parrot.store;

import com.sparrowwallet.parrot.ForwardedMessage;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "store.implementation", havingValue = "redisStore")
public class RedisStore implements Store {
    private static final Logger log = LoggerFactory.getLogger(RedisStore.class);

    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;

    public RedisStore() {
        log.info("Opening redis connection");
        this.redisClient = RedisClient.create("redis://localhost:6379");
        this.connection = redisClient.connect();
    }

    @Override
    public void addSentNymMessage(String nym, Integer messageId) {
        RedisCommands<String, String> sync = connection.sync();
        sync.rpush("nymMsg:" + nym, messageId.toString());
    }

    @Override
    public List<Integer> getSentNymMessageIds(String nym) {
        RedisCommands<String, String> sync = connection.sync();
        List<String> range = sync.lrange("nymMsg:" + nym, 0, -1);
        return range == null ? null : range.stream().map(Integer::parseInt).toList();
    }

    @Override
    public void addSentMessage(Integer messageId, ForwardedMessage forwardedMessage) {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("sentMsg:" + messageId.toString(), forwardedMessage.chatId() + ":" + forwardedMessage.messageId());
    }

    @Override
    public boolean hasSentMessage(Integer messageId) {
        RedisCommands<String, String> sync = connection.sync();
        return sync.get("sentMsg:" + messageId) != null;
    }

    @Override
    public ForwardedMessage getSentMessage(Integer messageId) {
        RedisCommands<String, String> sync = connection.sync();
        String value = sync.get("sentMsg:" + messageId);
        if(value != null) {
            String[] parts = value.split(":");
            return new ForwardedMessage(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
        }

        return null;
    }

    @Override
    public void addSentReply(Integer replyId, Integer messageId) {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("sentReply:" + replyId.toString(), messageId.toString());
    }

    @Override
    public boolean hasSentReply(Integer replyId) {
        RedisCommands<String, String> sync = connection.sync();
        return sync.get("sentReply:" + replyId) != null;
    }

    @Override
    public Integer getSentReply(Integer replyId) {
        RedisCommands<String, String> sync = connection.sync();
        String value = sync.get("sentReply:" + replyId);
        return value == null ? null : Integer.parseInt(value);
    }

    @Override
    public void addBannedNym(String nym) {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("nymBanned:" + nym, "true");
    }

    @Override
    public boolean hasBannedNym(String nym) {
        RedisCommands<String, String> sync = connection.sync();
        String value = sync.get("nymBanned:" + nym);
        return Boolean.parseBoolean(value);
    }

    @Override
    public boolean removeBannedNym(String nym) {
        RedisCommands<String, String> sync = connection.sync();
        sync.del("nymBanned:" + nym);
        return true;
    }

    @Override
    public void newUserAdded(Long userId, Long timestamp) {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("user:" + userId.toString(), timestamp.toString());
    }

    @Override
    public Long getNewUserTimestamp(Long userId) {
        RedisCommands<String, String> sync = connection.sync();
        String value = sync.get("user:" + userId.toString());
        return value == null ? null : Long.parseLong(value);
    }

    @Override
    public void close() {
        connection.close();
        redisClient.shutdown();
    }
}
