package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PrivateMessage;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.collection.SerializedMap;

public final class CommandTell extends ChatControlCommand {

	public CommandTell() {
		super(Settings.PrivateMessages.TELL_ALIASES);

		setUsage(Lang.of("Commands.Tell.Usage"));
		setDescription(Lang.of("Commands.Tell.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.TELL);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Tell.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		final boolean isOff = "off".equalsIgnoreCase(args[0]);
		final String message = joinArgs(1);

		final SyncedCache syncedCache = SyncedCache.from(args[0]);

		if (isOff || message.isEmpty()) {
			checkConsole();

			final PlayerCache senderCache = PlayerCache.from(getPlayer());

			if (isOff) {
				checkNotNull(senderCache.getConversingPlayer(), Lang.of("Commands.Tell.Conversation_Mode_Not_Conversing"));

				tellSuccess(Lang.of("Commands.Tell.Conversation_Mode_Off", senderCache.getConversingPlayer()));
				senderCache.setConversingPlayer(null, null);
			}

			else {
				checkNotNull(syncedCache, Lang.of("Player.Not_Online").replace("{player}", args[0]));

				final String conversingName = syncedCache.getPlayerName();
				final boolean isConversing = senderCache.getConversingPlayer() != null && conversingName.equals(senderCache.getConversingPlayer().getKey());

				senderCache.setConversingPlayer(isConversing ? null : conversingName, isConversing ? null : syncedCache.getUniqueId());
				tellSuccess(Lang.ofScript("Commands.Tell.Conversation_Mode_Toggle", SerializedMap.of("isConversing", isConversing), conversingName));
			}

			return;
		}
		checkNotNull(syncedCache, Lang.of("Player.Not_Online").replace("{player}", args[0]));

		PrivateMessage.send(sender, syncedCache, message);

	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return completeLastWordPlayerNames();
	}
}