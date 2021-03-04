package org.mineacademy.chatcontrol.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.DiscordListener;
import org.mineacademy.fo.model.DiscordSender;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePreProcessEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.exceptions.ErrorResponseException;
import github.scarsz.discordsrv.dependencies.jda.api.exceptions.HierarchyException;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.WebhookUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents our Discord connection
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Discord extends DiscordListener {

	/**
	 * The instance for this class
	 */
	@Getter
	private static final Discord instance = new Discord();

	/**
	 * Discord channel, message ID - message body
	 */
	private final Map<String, Map<Long, String>> messages = new HashMap<>();

	/**
	 * Old ID to New ID map, because when we edit a message, its ID is changed
	 */
	private final Map<Long, Long> editedMessages = new HashMap<>();

	/**
	 * Completely prevent DiscordSRV from dealing with chat if we have channels on
	 */
	@Override
	protected void onMessageSent(GameChatMessagePreProcessEvent event) {
		if (Settings.Channels.ENABLED && Settings.Integration.Discord.ENABLED)
			event.setCancelled(true);
	}

	/**
	 * @see org.mineacademy.fo.model.DiscordListener#onMessageReceived(github.scarsz.discordsrv.api.events.DiscordGuildMessagePreProcessEvent)
	 */
	@Override
	protected void onMessageReceived(DiscordGuildMessagePreProcessEvent event) {

		if (!Settings.Channels.ENABLED || !Settings.Integration.Discord.ENABLED)
			return;

		final Map<String, String> connectedChannels = Settings.Integration.Discord.CONNECTED_CHANNELS;

		final TextChannel discordChannel = event.getChannel();
		final Message discordMessage = event.getMessage();

		final String message = discordMessage.getContentDisplay();
		final String discordChannelId = discordChannel.getId();
		final String chatControlChannel = connectedChannels.get(discordChannelId);

		if (chatControlChannel != null) {
			final Channel channel = Channel.findChannel(chatControlChannel);

			if (channel != null) {

				if (channel.isDiscord())
					try {
						final UUID linkedId = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getAuthor().getId());
						String minecraftName = Common.getOrDefaultStrict(event.getMember().getNickname(), event.getAuthor().getName());

						if (linkedId != null)
							minecraftName = UserMap.getInstance().getName(linkedId);

						final Channel.Result result = channel.sendMessage(new DiscordSender(minecraftName, event.getAuthor(), discordChannel, discordMessage), message);

						// Rewrite the message - applying antispam, rules etc.
						if (Settings.Integration.Discord.SEND_MESSAGES_AS_BOT)
							editMessageById(discordChannel, discordMessage.getIdLong(), result.getMessage());

					} catch (final EventHandledException ex) {
						for (final String errorMessage : ex.getMessages())
							flashMessage(discordChannel, errorMessage);

						if (ex.isCancelled())
							deleteMessageById(discordChannel, discordMessage.getIdLong());
					}
				else
					Common.log("Received Discord message to ChatControl channel '" + discordChannel.getName() + "' not having 'Discord' option on true. Not sending. Message: " + message);

			} else
				Common.log("Received Discord message to non-existing ChatControl channel '" + chatControlChannel + "'. Message: " + message);
		}

		else
			Debugger.debug("discord", "Received Discord message to non-linked ChatControl channel '" + discordChannel.getName() + "' id " + discordChannel.getId() + ", Message: " + message);

		// Prevent DiscordSRV handling
		event.setCancelled(true);
	}

	/**
	 * Sends a channel message to Discord
	 *
	 * @param sender
	 * @param channelName
	 * @param message
	 */
	public void sendChannelMessageNoPlayer(String channelName, String message) {
		this.sendChannelMessage(null, channelName, message, null);
	}

	/**
	 * Sends a channel message to Discord
	 * with an optional JSON argument that is cached and used to delete messages
	 *
	 * @param sender
	 * @param channelName
	 * @param message
	 * @param json
	 *
	 */
	public void sendChannelMessage(CommandSender sender, String channelName, String message, @Nullable String json) {
		if (!Settings.Integration.Discord.ENABLED)
			return;

		final TextChannel channel = findDiscordChannel(channelName);

		if (channel == null) {
			Debugger.debug("discord", "Failed to find connected Discord channel from ChatControl channel " + channelName + ". Message was not sent: " + message);

			return;
		}

		// Send the message
		Common.runAsync(() -> {
			try {

				// Webhooks do not support callback
				if (sender instanceof Player && Settings.Integration.Discord.WEBHOOK)
					WebhookUtil.deliverMessage(channel, (Player) sender, message);

				else {
					final Message discordDessage = channel.sendMessage(message).complete();

					// Mark it
					if (json != null)
						markReceivedMessage(channelName, discordDessage.getIdLong(), json);
				}

			} catch (final ErrorResponseException ex) {
				Debugger.debug("discord", "Unable to send message to Discord channel " + channelName + ", message: " + message);
			}
		});
	}

	/**
	 * Marks a message as received with the JSON representation of interactive in Minecraft chat
	 *
	 * @param channelName
	 * @param sender
	 * @param json
	 */
	public void markReceivedMessage(String channelName, DiscordSender sender, String json) {
		this.markReceivedMessage(channelName, sender.getMessage().getIdLong(), json);
	}

	/*
	 * Mark the message as received
	 */
	private void markReceivedMessage(String channelName, long messageId, String json) {
		synchronized (this.messages) {
			final Map<Long, String> pastChannelMessages = this.messages.getOrDefault(channelName, new HashMap<>());

			pastChannelMessages.put(messageId, json);
			this.messages.put(channelName, pastChannelMessages);
		}
	}

	/**
	 * Remove a channel message by its given unique ID
	 *
	 * @param uniqueId
	 */
	public void removeChannelMessage(UUID uniqueId) {
		if (Settings.Integration.Discord.ENABLED)
			for (final Entry<String, Map<Long, String>> entry : this.messages.entrySet()) {
				final TextChannel channel = findDiscordChannel(entry.getKey());

				if (channel == null)
					continue;

				for (final Map.Entry<Long, String> message : entry.getValue().entrySet())
					if (message.getValue().contains(uniqueId.toString())) {
						long id = message.getKey();

						// Try to get the latest ID if we edited the message
						id = editedMessages.getOrDefault(id, id);

						deleteMessageById(channel, id);
					}
			}
	}

	/*
	 * Convert channel name of ChatControl into a Discord channel
	 * Returning null if not found
	 */
	private TextChannel findDiscordChannel(String channelName) {
		final Map<String, String> connectedChannels = Settings.Integration.Discord.CONNECTED_CHANNELS;

		for (final Map.Entry<String, String> entry : connectedChannels.entrySet()) {
			final String discordChannelId = entry.getKey();
			final String chatControlChannel = entry.getValue();

			if (chatControlChannel.equals(channelName)) {
				final JDA jda = DiscordUtil.getJda();

				// JDA can be null when server is starting or connecting
				if (jda != null) {
					final TextChannel channel = jda.getTextChannelById(discordChannelId);

					if (channel != null)
						return channel;
				}
			}
		}

		return null;
	}

	/*
	 * Remove the given message by ID
	 */
	private void deleteMessageById(TextChannel channel, long messageId) {
		Common.runAsync(() -> {
			try {
				channel.deleteMessageById(messageId).complete();

			} catch (final Throwable t) {

				// ignore already deleted
				if (!(t instanceof github.scarsz.discordsrv.dependencies.jda.api.exceptions.ErrorResponseException))
					t.printStackTrace();

				else
					Log.logTip("TIP Alert: Could not remove Discord message in channel '" + channel.getName() + "' id " + messageId + ", it was probably deleted otherwise or this is a bug.");
			}
		});
	}

	/*
	 * Edit the given message by ID
	 */
	private void editMessageById(TextChannel channel, long messageId, String newMessage) {
		Common.runAsync(() -> {
			final Message message = channel.retrieveMessageById(messageId).complete();

			try {
				channel.deleteMessageById(messageId).complete();

				final Message newSentMessage = channel
						.sendMessage(message.getAuthor().getName() + ": " + newMessage.replace("*", "\\*").replace("_", "\\_").replace("@", "\\@"))
						.complete();

				editedMessages.put(messageId, newSentMessage.getIdLong());
			} catch (final Throwable t) {
				if (!t.toString().contains("Unknown Message"))
					t.printStackTrace();
			}
		});
	}

	/*
	 * Send message for five seconds
	 */
	private void flashMessage(TextChannel channel, String message) {
		final String finalMessage = Common.stripColors(message);

		Common.runAsync(() -> {
			final Message sentMessage = channel.sendMessage(finalMessage).complete();

			Common.runLaterAsync(5 * 20, () -> channel.deleteMessageById(sentMessage.getIdLong()).complete());
		});
	}

	/**
	 * Attempt to kick the player name from the channel
	 *
	 * @param discordSender
	 * @param reason
	 */
	public void kickMember(DiscordSender discordSender, String reason) {
		if (Settings.Integration.Discord.ENABLED)
			Common.runAsync(() -> {
				try {
					final Member member = DiscordUtil.getMemberById(discordSender.getUser().getId());

					if (member != null)
						member.kick(reason).complete();

				} catch (final HierarchyException ex) {
					Common.log("Unable to kick " + discordSender.getName() + " because he appears to be Discord administrator");
				}
			});
	}
}
