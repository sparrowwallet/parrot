# Parrot

Telegram bot to forward messages pseudonymously to a group. 
Parrot ensures that replies are correctly threaded so a user can converse entirely using the bot.
A welcome message is displayed, and a cooldown period can be added to prevent new users from messaging the group for a configurable period.

## Building

Build Parrot with

```shell
./gradlew clean bootJar
```

## Running

First set enviroment variables:
```shell
PARROT_BOT_TOKEN=XXXXXXXXXXXX:XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
PARROT_BOT_USERNAME=@BotName
PARROT_GROUP_ID=-XXXXXXXXX
```

Optionally, add another environmental variable to set a cooldown period for new users joining the group:
```shell
PARROT_COOLDOWN_PERIOD_MINUTES=10
```

Then run with
```
java -jar build/libs/parrot-1.0.jar
```

## Configuration

You will need to add the bot to the Telegram group, and make it an admin of the group to ensure it has the necessary permissions to forward messages.
You will need to give it the Delete Messages permission to delete messages from banned users.
If you are using the cooldown period feature, the bot will also need the Ban Users permission, and the Manage Video Chats.

## Usage

The bot should just work, forwarding messages back and forth, displaying the welcome message, and restricting users during the cooldown period if configured.

Admins of the group can also ban and unban users. Banning users will also delete all messages from them forwarded from the bot to the group.
To ban a user, in the bot use the command `/ban #NymName`. To unban a user, use `/unban #NymName`.