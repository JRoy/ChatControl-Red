package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.ListPlayers;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.constants.FoConstants;

public final class CommandList extends ChatControlCommand {

	public CommandList() {
		super(Settings.ListPlayers.COMMAND_ALIASES);

		setUsage(BungeeCord.ENABLED ? Lang.of("Commands.List.Usage_Server") : "");
		setDescription(Lang.of("Commands.List.Description"));
		setPermission(Permissions.Command.LIST);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		final List<String> hover = new ArrayList<>();

		hover.add(Lang.ofScript("Commands.List.Usage_1", SerializedMap.of("bungee", BungeeCord.ENABLED)));

		if (BungeeCord.ENABLED)
			hover.add(Lang.of("Commands.List.Usage_2"));

		return Common.toArray(hover);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length <= 1);

		final String prefix = Settings.ListPlayers.SORT_PREFIX;

		if (BungeeCord.ENABLED) {
			final String server = args.length == 1 ? args[0] : "";
			final UUID senderUUID = isPlayer() ? getPlayer().getUniqueId() : FoConstants.NULL_UUID;

			BungeeUtil.tellBungee(BungeePacket.LIST_PLAYERS_REQUEST, senderUUID, server, prefix);

		} else
			ListPlayers.listPlayers(sender, prefix);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return BungeeCord.ENABLED ? completeLastWord(SyncedCache.getServers()) : NO_COMPLETE;

		return NO_COMPLETE;
	}
}
