package de.aegorin.message4me;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class BotCommandListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(BotCommandListener.class);

    private static final String COMMAND = "say4me";

    private static final String BTN_ID_PREFIX = "preview:";
    private static final String BTN_SEND_ID = "preview:send";
    private static final String BTN_CANCEL_ID = "preview:cancel";

    private static final String MODAL_ID = "say4me:modal";
    private static final String INPUT_TEXT_ID = "say4me:text";

    private final String guildId;
    private final boolean upsertCommands;

    public BotCommandListener(String guildId, boolean upsertCommands) {
        this.guildId = guildId;
        this.upsertCommands = upsertCommands;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        log.info("JDA Ready. Considering command registration...");

        var command = Commands.slash(COMMAND, "Send a message via the bot with a preview");

        if (!upsertCommands) {
            log.info("UPSERT_COMMANDS=false -> Skipping command registration to avoid rate limits.");
            return;
        }

        boolean upsertGlobally = true;
        if (guildId != null && !guildId.isBlank()) {
            Guild guild = event.getJDA().getGuildById(guildId);
            if (guild != null) {
                log.info("Upserting guild command in guild {}...", guildId);
                upsertGlobally = false;
                guild.updateCommands().addCommands(command).queue(
                        v -> log.info("Registered guild command: {}", COMMAND),
                        err -> log.error("Failed to register guild command", err)
                );
            } else {
                log.warn("Provided GUILD_ID not found. Falling back to global registration...");
            }
        }
        if (upsertGlobally) {
            log.info("No or invalid GUILD_ID provided. Upserting global command (may take time to propagate)...");
            event.getJDA().updateCommands().addCommands(command).queue(
                    v -> log.info("Registered global command: {}", COMMAND),
                    err -> log.error("Failed to register global command", err)
            );
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(COMMAND)) {
            return;
        }

        log.info("SlashCommand: /{} by {} ({}) in channel {} ({})",
                COMMAND, event.getUser().getName(), event.getUser().getId(),
                event.getChannel().getName(), event.getChannel().getId());

        if (!event.isFromGuild()) {
            log.info("Rejected: used outside a guild.");
            event.reply("This command can only be used in a server").setEphemeral(true).queue();
            return;
        }

        GuildMessageChannel targetChannel = event.getChannel().asGuildMessageChannel();
        var selfMember = Objects.requireNonNull(event.getGuild()).getSelfMember();
        boolean canSend = selfMember.hasAccess(targetChannel)
                && selfMember.hasPermission(targetChannel, net.dv8tion.jda.api.Permission.MESSAGE_SEND);

        if (!canSend) {
            log.info("Rejected: missing permission to send in channel {} ({})",
                    targetChannel.getId(), targetChannel.getName());
            event.reply("I don't have permission to send messages in this channel/thread")
                    .setEphemeral(true).queue();
            return;
        }

        TextInput text = TextInput.create(INPUT_TEXT_ID, "Message content", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Write your message here (Discord markdown supported). You will receive a preview after submitting")
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(2000)
                .build();

        Modal modal = Modal.create(MODAL_ID, "Message4Me")
                .addActionRow(text)
                .build();

        event.replyModal(modal).queue(
                ok -> log.info("Modal shown to user {} ({})", event.getUser().getName(), event.getUser().getId()),
                err -> {
                    log.error("Failed to show modal {}({})", event.getUser().getName(), event.getUser().getId());
                    log.error("Exception: ", err);
                }
        );
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().equals(MODAL_ID)) {
            return;
        }

        log.info("Modal submit: user={}({}), channel={}({})",
                event.getUser().getName(), event.getUser().getId(),
                event.getChannel().getName(), event.getChannel().getId());

        if (!event.isFromGuild()) {
            log.info("Rejected: modal submitted outside a guild.");
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true).queue();
            return;
        }

        var value = event.getValue(INPUT_TEXT_ID);
        String content = value == null ? "" : value.getAsString();
        if (content.isBlank()) {
            event.reply("Please provide some text to send.")
                    .setEphemeral(true).queue();
            return;
        }

        GuildMessageChannel targetChannel = event.getChannel().asGuildMessageChannel();
        var selfMember = Objects.requireNonNull(event.getGuild()).getSelfMember();
        boolean canSend = selfMember.hasAccess(targetChannel)
                && selfMember.hasPermission(targetChannel, net.dv8tion.jda.api.Permission.MESSAGE_SEND);

        if (!canSend) {
            log.info("Rejected: missing permission to send in channel {} (post-modal)", targetChannel.getId());
            event.reply("I don't have permission to send messages in this channel/thread")
                    .setEphemeral(true).queue();
            return;
        }

        // Stateless preview: ephemeral message is exactly the content to be sent
        var confirmButton = Button.success(BTN_SEND_ID, "Send");
        var cancelButton = Button.danger(BTN_CANCEL_ID, "Abort");

        event.reply(content)
                .setEphemeral(true)
                .addActionRow(confirmButton, cancelButton)
                .queue(
                        ok -> log.info("Ephemeral preview sent (from modal)"),
                        err -> log.error("Failed to send ephemeral preview (from modal)", err)
                );
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(BTN_ID_PREFIX)) {
            return;
        }

        log.info("Button clicked: action={}, user={}({}), channel={}({})",
                id, event.getUser().getName(), event.getUser().getId(),
                event.getChannel().getName(), event.getChannel().getId());

        String content = event.getMessage().getContentRaw();
        if (content.isBlank()) {
            log.warn("Preview content missing at interaction time.");
            event.editMessage("Preview content missing. Please run the command again.").setComponents().queue();
            return;
        }

        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();

        switch (id) {
            case BTN_SEND_ID -> {
                var selfMember = channel.getGuild().getSelfMember();
                boolean canSend = selfMember.hasAccess(channel)
                        && selfMember.hasPermission(channel, net.dv8tion.jda.api.Permission.MESSAGE_SEND);
                if (!canSend) {
                    log.info("Cannot send: missing permission at send-time for channel {}", channel.getId());
                    event.editMessage("I no longer have permission to send in this channel/thread.")
                            .setComponents().queue();
                    return;
                }

                log.info("Sending message to channel {} ({})", channel.getName(), channel.getId());
                channel.sendMessage(content).queue(
                        ok -> {
                            log.info("Message sent successfully.");
                            event.editMessage("Message sent.")
                                    .setComponents(ActionRow.of(
                                            Button.success("noop", "Sent").asDisabled(),
                                            Button.secondary("noop2", "Abort").asDisabled()
                                    ))
                                    .queue();
                        },
                        err -> {
                            log.error("Failed to send message", err);
                            event.editMessage("Failed to send message: " + err.getMessage())
                                    .setComponents()
                                    .queue();
                        }
                );
            }
            case BTN_CANCEL_ID -> {
                log.info("Operation aborted by user.");
                event.editMessage("Operation aborted.").setComponents().queue();
            }
            default -> {
                log.info("Unknown action received.");
                event.reply("Unknown action.").setEphemeral(true).queue();
            }
        }
    }
}
