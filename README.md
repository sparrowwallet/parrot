# Parrot

Telegram bot to forward messages pseudonymously to a group. 
Parrot ensures that replies are correctly threaded so a user can converse entirely using the bot.
A welcome message is displayed, and a cooldown period can be added to prevent new users from messaging the group for a configurable period.

## Building

Build Parrot with

```shell
./gradlew clean shadowJar
```

## Running

First set enviroment variables:
```shell
PARROT_BOT_TOKEN=XXXXXXXXXXXX:XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
PARROT_BOT_USERNAME=@BotName
PARROT_GROUP_ID=-XXXXXXXXX
PARROT_NYM_SECRET=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

`PARROT_NYM_SECRET` is the key used to derive each user's pseudonym from their Telegram user ID, and must be at least 32 characters long.
Generate one with `openssl rand -hex 32` and keep it private: anyone who knows it can link pseudonyms to group members.
Changing it changes every user's pseudonym and makes existing bans ineffective.

Optionally, add another environmental variable to set a cooldown period for new users joining the group:
```shell
PARROT_COOLDOWN_PERIOD_MINUTES=10
```

Parrot stores its data in Redis at `localhost:6379` by default. To connect elsewhere or use a password, set the Redis URL:
```shell
PARROT_REDIS_URL=redis://:password@localhost:6379
```
Characters in the password that are special in URLs (such as `@`, `:`, `/` or `%`) must be percent-encoded, or use a hex password from `openssl rand -hex 32`.

For testing without Redis, an in-memory store that is lost on restart can be used instead:
```shell
PARROT_STORE=java
```

Then run with Java 25 or later
```
java -Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:ReservedCodeCacheSize=32m -jar build/libs/parrot-1.0.jar
```
The JVM options are optional, and reduce memory use to around 120MB.

## Configuration

You will need to add the bot to the Telegram group, and make it an admin of the group to ensure it has the necessary permissions to forward messages.
You will need to give it the Delete Messages permission to delete messages from banned users.
If you are using the cooldown period feature, the bot will also need the Ban Users permission, and the Manage Video Chats.

## Usage

The bot should just work, forwarding messages back and forth, displaying the welcome message, and restricting users during the cooldown period if configured.
The bot currently implements rate limiting, allowing 5 messages every minute.

Admins of the group can also ban and unban users. Banning users will also delete all messages from them forwarded from the bot to the group.
To ban a user, in the bot use the command `/ban #NymName`. To unban a user, use `/unban #NymName`.