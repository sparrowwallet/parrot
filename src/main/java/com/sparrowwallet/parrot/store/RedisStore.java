package com.sparrowwallet.parrot.store;

import com.sparrowwallet.parrot.ForwardedMessage;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RedisStore implements Store {
    private static final Logger log = LoggerFactory.getLogger(RedisStore.class);
    public static final String DEFAULT_REDIS_URL = "redis://localhost:6379";

    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;

    public RedisStore() {
        this(DEFAULT_REDIS_URL);
    }

    public RedisStore(String redisUrl) {
        RedisURI redisUri = RedisURI.create(redisUrl);
        //Log only the host and port, as the URL may contain a password
        log.info("Opening redis connection to " + redisUri.getHost() + ":" + redisUri.getPort());
        this.redisClient = RedisClient.create(redisUri);
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
    public void addForwardedMessage(Integer messageId, Integer forwardedMessageId) {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("fwdMsg:" + messageId.toString(), forwardedMessageId.toString());
    }

    @Override
    public Integer getForwardedMessageId(Integer messageId) {
        RedisCommands<String, String> sync = connection.sync();
        String value = sync.get("fwdMsg:" + messageId);
        return value == null ? null : Integer.parseInt(value);
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
        return sync.del("nymBanned:" + nym) > 0;
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
    public void setWelcomeMessageId(Integer messageId) {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("welcomeMsg", messageId.toString());
    }

    @Override
    public Integer getWelcomeMessageId() {
        RedisCommands<String, String> sync = connection.sync();
        String value = sync.get("welcomeMsg");
        return value == null ? null : Integer.parseInt(value);
    }

    @Override
    public void close() {
        connection.close();
        redisClient.shutdown();
    }
}
