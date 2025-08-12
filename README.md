# Message4Me: Message Branding on Discord made easy

Send official-looking messages for your Project without account sharing by letting a custom bot send them for you.

Use `/say4me` to write and preview your message ephemerally and confirm with a button. Simple, safe, and stateless.

- Ephemeral preview with Send/Abort
- Posts into the current channel/thread
- Stateless (survives restarts)
- Config via environment variables or .env file
- Optional command upsert to avoid rate limits

## Quick start

First, create a Bot in the Discord Developer Portal.

Then pull and run from GHCR:

```shell script
docker run -d \
  -e DISCORD_TOKEN=<your-bot-token> \
  -e GUILD_ID=<your-guild-id> \
  -e UPSERT_COMMANDS=true \
  ghcr.io/AegorinDevelopment/Message4Me:latest
```

or with Docker Compose:

```yaml
services:
  message4me:
    image: ghcr.io/AegorinDevelopment/Message4Me:latest
    restart: unless-stopped
    environment:
      DISCORD_TOKEN: "<your-bot-token>"
      # Optional: fast command updates in one guild
      GUILD_ID: "123456789012345678"
      # Avoid rate limits in prod (set true only when changing commands)
      UPSERT_COMMANDS: true
```

## Configuration

- DISCORD_TOKEN: required, bot token
- GUILD_ID: optional, will register the commands guild-scoped for instantaneous availability
- UPSERT_COMMANDS: optional, when true the bot will update the commands.<br>!!You need to do this once when first setting up the Bot!!

## Usage

- In any server channel/thread, run `/say4me`
- A Modal for the Text Input will appear
- Bot shows an ephemeral preview with Send/Abort
- Bot needs permission to send messages in that channel/thread

## Local development

```shell script
mvn -B package
java -jar target/message4me-1.0-SNAPSHOT.jar
```

Requires Java 21+
