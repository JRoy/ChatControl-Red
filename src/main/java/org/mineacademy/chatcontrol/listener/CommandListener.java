package org.mineacademy.chatcontrol.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.Checker;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.event.SimpleListener;

import lombok.Getter;

/**
 * Represents a simple command listener for rules
 */
public final class CommandListener extends SimpleListener<PlayerCommandPreprocessEvent> {

	/**
	 * The instance of this class
	 */
	@Getter
	private static final CommandListener instance = new CommandListener();

	/*
	 * Create a new listener
	 */
	private CommandListener() {
		super(PlayerCommandPreprocessEvent.class, EventPriority.HIGHEST, true);
	}

	/**
	 * @see org.mineacademy.fo.event.SimpleListener#execute(org.bukkit.event.Event)
	 */
	@Override
	protected void execute(PlayerCommandPreprocessEvent event) {
		final Player player = event.getPlayer();
		final PlayerCache cache = PlayerCache.from(player);
		final SenderCache senderCache = SenderCache.from(player);

		String message = event.getMessage();
		final String[] args = message.split(" ");
		final String label = args[0];

		checkBoolean(!senderCache.isLoadingMySQL(), Lang.of("Data_Loading"));
		checkBoolean(!Mute.isCommandMuted(player, label), Lang.of("Commands.Mute.Cannot_Command"));

		// Newcomers
		if (Settings.Newcomer.RESTRICT_COMMANDS && Newcomer.isNewcomer(player) && !Settings.Newcomer.RESTRICT_COMMANDS_WHITELIST.isInList(label))
			cancel(Lang.of("Player.Newcomer_Cannot_Command"));

		// Filters
		message = Checker.filterCommand(player, message, cache.getWriteChannel()).getMessage();

		// Send to spying players and log but prevent duplicates
		if ((!Valid.isInList(label, Settings.Mail.COMMAND_ALIASES) || !Settings.Mail.ENABLED) &&

				((!Valid.isInList(label, Settings.PrivateMessages.TELL_ALIASES) && !Valid.isInList(label, Settings.PrivateMessages.REPLY_ALIASES)) ||
						!Settings.PrivateMessages.ENABLED)
				&&

				!(Valid.isInList(label, Settings.MAIN_COMMAND_ALIASES) && args.length > 1 && "internal".equals(args[1]))) {

			if (!Valid.isInList(label, Settings.Me.COMMAND_ALIASES))
				Spy.broadcastCommand(player, message);

			Log.logCommand(player, message);
		}

		// Set the command back
		event.setMessage(message);
	}
}
