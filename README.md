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