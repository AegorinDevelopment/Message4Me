package de.aegorin.message4me;

import org.slf4j.*;

import io.github.cdimascio.dotenv.*;
import net.dv8tion.jda.api.*;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        String token = dotenv.get("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            log.error("Missing TOKEN. Please configure it via environment variable or .env file.");
            System.exit(1);
        }

        String guildId = dotenv.get("GUILD_ID");
        boolean upsertCommands = parseBoolean(dotenv.get("UPSERT_COMMANDS"));

        log.info("Starting JDA. UPSERT_COMMANDS={}, GUILD_ID={}", upsertCommands, (guildId == null ? "<none>" : guildId));
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new BotCommandListener(guildId, upsertCommands))
                .build();

        jda.awaitReady();
        log.info("Bot is online.");
    }

    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return v.equals("1") || v.equals("true") || v.equals("yes") || v.equals("y") || v.equals("on");
    }
}