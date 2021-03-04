package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.command.ChatControlCommands.CommandFlagged;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.Variables;

public final class ChatControlClear extends CommandFlagged {

	public ChatControlClear() {
		super("clear/cl", Lang.of("Commands.Clear.Usage"), Lang.of("Commands.Clear.Description"));

		setPermission(Permissions.Command.CLEAR);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Clear.Usages");
	}

	@Override
	protected void execute(boolean console, boolean anonymous, boolean silent, boolean raw, String reason) {

		// Compile message
		final String announceMessage = silent ? ""
				: Lang.ofScript("Commands.Clear.Success", SerializedMap.ofArray(
						"bungee", BungeeCord.ENABLED,
						"anonymous", anonymous,
						"reason", reason),
						Common.resolveSenderName(sender), reason);

		// Do the actual clear
		if (console) {
			for (int i = 0; i < 5000; i++)
				System.out.println("             ");
		} else
			Players.clearChat(sender, announceMessage.isEmpty());

		if (console) {
			checkPerm(Permissions.Command.CLEAR_CONSOLE);
			final String message = Lang.of("Commands.Clear.Success_Console", Common.resolveSenderName(sender));

			tellSuccess(message);
			Common.log(message);

			return;
		}

		if (!announceMessage.isEmpty())
			Messenger.broadcastAnnounce(announceMessage);

		if (!isPlayer())
			tellNoPrefix(Variables.replace(announceMessage.isEmpty() ? Lang.of("Commands.Clear.Success_Staff", Common.resolveSenderName(sender)) : announceMessage, sender));

		if (BungeeCord.ENABLED)
			BungeeUtil.tellBungee(BungeePacket.CLEAR_CHAT, announceMessage);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		if (args.length < 3)
			return completeLastWord("-anonymous", "-a", "-console", "-c", "-silent", "-s");

		return super.tabComplete();
	}
}
