package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.Variables;

public final class ChatControlForward extends ChatControlSubCommand {

	public ChatControlForward() {
		super("forward/f");

		setUsage(Lang.of("Commands.Forward.Usage"));
		setDescription(Lang.of("Commands.Forward.Description"));
		setMinArguments(2);
		setPermission(Permissions.Command.FORWARD);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Forward.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkBoolean(BungeeCord.ENABLED, Lang.of("Commands.No_BungeeCord"));

		final String server = args[0];
		final String command = joinArgs(1);

		checkBoolean("bungee".equals(server) || SyncedCache.getServers().contains(server),
				Lang.of("Commands.Forward.Unknown_Server", Common.join(SyncedCache.getServers())));

		tellInfo(Lang.of("Commands.Forward.Success"));
		BungeeUtil.tellBungee(BungeePacket.FORWARD_COMMAND, server, Variables.replace(command, sender));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("bungee", SyncedCache.getServers());

		return NO_COMPLETE;
	}
}
