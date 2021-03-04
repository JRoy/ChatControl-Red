package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PrivateMessage;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;

public final class CommandReply extends ChatControlCommand {

	public CommandReply() {
		super(Settings.PrivateMessages.REPLY_ALIASES);

		setUsage(Lang.of("Commands.Reply.Dosage"));
		setDescription(Lang.of("Commands.Reply.Prescription"));
		setMinArguments(1);
		setPermission(Permissions.Command.REPLY);
		setAutoHandleHelp(false);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkConsole();

		final String message = joinArgs(0);
		final SenderCache senderCache = SenderCache.from(sender);

		checkBoolean(senderCache.getReplyPlayer() != null, Lang.of("Commands.Reply.Alone"));

		// Handle replying to console
		if (senderCache.getReplyPlayer().getKey().equals("CONSOLE")) {
			final SerializedMap variables = SerializedMap.ofArray(
					"receiver", Lang.of("Console_Name"),
					"player", Lang.of("Console_Name"),
					"sender", Common.resolveSenderName(this.sender));

			Format
					.parse(Settings.PrivateMessages.FORMAT_SENDER).build(this.sender, message, variables)
					.send(sender);

			Format
					.parse(Settings.PrivateMessages.FORMAT_RECEIVER).build(this.sender, message, variables)
					.send(Bukkit.getConsoleSender());

			return;
		}

		final SyncedCache syncedCache = SyncedCache.fromUUID(senderCache.getReplyPlayer().getValue());
		checkNotNull(syncedCache, Lang.of("Player.Not_Online").replace("{player}", senderCache.getReplyPlayer().getKey()));

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