package org.mineacademy.chatcontrol.listener;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Checker;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Toggle;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.event.SimpleListener;
import org.mineacademy.fo.model.Tuple;

import lombok.Getter;

/**
 * The main listener for async chat
 */
public final class ChatListener extends SimpleListener<AsyncPlayerChatEvent> {

	// Special workaround for concurrency issues (pressing F2 rapidly)
	private static volatile Object LOCK = new Object();

	/**
	 * The singleton instance
	 */
	@Getter
	private static final ChatListener instance = new ChatListener();

	/*
	 * The active player instance
	 */
	private Player player;

	/*
	 * Creates new listener
	 */
	private ChatListener() {
		super(AsyncPlayerChatEvent.class, Settings.CHAT_LISTENER_PRIORITY, true);
	}

	/**
	 * @see org.mineacademy.fo.event.SimpleListener#execute(org.bukkit.event.Event)
	 */
	@Override
	protected void execute(AsyncPlayerChatEvent event) {
		synchronized (LOCK) {
			final Player player = event.getPlayer();

			this.player = player;

			final PlayerCache cache = PlayerCache.from(player);
			final SenderCache senderCache = SenderCache.from(player);
			final Set<Player> recipients = event.getRecipients();
			String message = event.getMessage();

			checkBoolean(!senderCache.isLoadingMySQL(), Lang.of("Data_Loading"));
			checkPerm(Permissions.Chat.WRITE, Lang.of("Player.No_Write_Chat_Permission", Permissions.Chat.WRITE));

			// Newcomer
			if (Settings.Newcomer.RESTRICT_CHAT && Newcomer.isNewcomer(player) && !Settings.Newcomer.RESTRICT_CHAT_WHITELIST.isInList(message))
				cancel(Lang.of("Player.Newcomer_Cannot_Write"));

			checkBoolean(!Common.stripColors(message).isEmpty(), Lang.of("Checker.No_Text"));

			// Remove recipients who can't read the message
			for (final Iterator<Player> it = recipients.iterator(); it.hasNext();) {
				final Player recipient = it.next();

				if (!PlayerUtil.hasPerm(recipient, Permissions.Chat.READ)
						|| (Settings.Newcomer.RESTRICT_SEEING_CHAT && Newcomer.isNewcomer(recipient))
						|| cache.isIgnoringPart(Toggle.CHAT))

					it.remove();
			}

			// Auto conversation mode
			if (cache.getConversingPlayer() != null) {
				final Tuple<String, UUID> conversingPlayer = cache.getConversingPlayer();

				if (SyncedCache.isPlayerConnected(conversingPlayer.getValue())) {
					final String finalMessage = message;

					// Must invoke the chat() method to apply rules and filtering
					Common.runLater(() -> player.chat("/" + Settings.PrivateMessages.TELL_ALIASES.get(0) + " " + conversingPlayer.getKey() + " " + finalMessage));

				} else {
					Messenger.warn(player, Lang.of("Commands.Tell.Conversation_Offline", cache.getConversingPlayer()));

					Common.runLater(() -> cache.setConversingPlayer(null, null));
				}

				cancel();
			}

			// Do not use channels
			if (!Settings.Channels.ENABLED || Settings.Channels.IGNORE_WORLDS.contains(player.getWorld().getName())) {

				// Mute
				checkBoolean(!Mute.isChatMuted(player), Lang.of("Commands.Mute.Cannot_Chat"));

				final Checker checker = Checker.filterChannel(player, message, null);

				if (checker.isCancelledSilently())
					recipients.removeIf(recipient -> !recipient.getName().equals(player.getName()));

				// Update message from antispam/rules
				message = checker.getMessage();

				// Apply colors
				message = Colors.addColorsForPermsAndChat(player, message);

				// Remove ignored players
				recipients.removeIf(recipient -> {

					// Prevent recipients on worlds where channels are enabled from seeing the message
					if (Settings.Channels.ENABLED && !Settings.Channels.IGNORE_WORLDS.contains(recipient.getWorld().getName()))
						return true;

					if (Settings.Ignore.ENABLED && Settings.Ignore.HIDE_CHAT && !hasPerm(Permissions.Bypass.REACH) && PlayerCache.from(recipient).isIgnoringPlayer(player.getUniqueId()))
						return true;

					return false;
				});

				// Log to file and db
				Log.logChat(player, null, message);

				// Update the message
				event.setMessage(message);

				return;
			}

			final Channel writeChannel = cache.getWriteChannel();

			checkPerm(Permissions.Chat.WRITE);
			checkNotNull(writeChannel, Lang.of(Channel.canJoinAnyChannel(player) ? "Player.No_Channel" : "Player.No_Possible_Channel"));

			// Prevent accidental typing
			if (Settings.Channels.PREVENT_VANISH_CHAT && PlayerUtil.isVanished(player))
				cancel(Lang.of("Player.Cannot_Chat_Vanished"));

			// Send to channel and return the edited message
			final Channel.Result result = writeChannel.sendMessage(player, message);

			// Act as cancel at the pipeline
			if (result.isCancelledSilently()) {
				event.setCancelled(true);

				return;
			}

			// Update the message for other plugins
			event.setMessage(result.getMessage());

			// Clear recipient list so that Bukkit does not send anyone the message
			// but other plugins can still catch the event.
			// By this time we already sent the message in our own way using interactive chat.
			recipients.clear();

			// Do not log to the console by canceling event - causes incompatibilities but we want the user enough
			final String consoleLog = result.getConsoleLog();

			if ("none".equalsIgnoreCase(consoleLog))
				event.setCancelled(true);

			else
				// Log to console, but avoid String.format crashing when % is typed
				event.setFormat(consoleLog.replace("%", "%%"));
		}
	}

	/**
	 * @see org.mineacademy.fo.event.SimpleListener#findPlayer()
	 */
	@Override
	protected Player findPlayer() {
		return this.player;
	}
}
