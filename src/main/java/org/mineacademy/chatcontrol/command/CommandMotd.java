package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.settings.SimpleLocalization;

public final class CommandMotd extends ChatControlCommand {

	public CommandMotd() {
		super(Settings.Motd.COMMAND_ALIASES);

		setUsage(Lang.of("Commands.Motd.Usage"));
		setDescription(Lang.of("Commands.Motd.Description"));
		setPermission(Permissions.Command.MOTD);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Motd.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length <= 1);
		checkBoolean(isPlayer() || args.length == 1, Lang.of("Commands.Console_Missing_Player_Name"));

		pollCache(args.length == 1 ? args[0] : sender.getName(), cache -> {
			checkBoolean(SyncedCache.isPlayerConnected(cache.getUniqueId()), SimpleLocalization.Player.NOT_ONLINE.replace("{player}", cache.getPlayerName()));

			final boolean self = cache.getPlayerName().equals(sender.getName());

			if (!self)
				checkPerm(Permissions.Command.MOTD_OTHERS);

			final Player player = Bukkit.getPlayerExact(cache.getPlayerName());

			if (player != null)
				Players.showMotd(player, false);

			else if (BungeeCord.ENABLED)
				BungeeUtil.tellBungee(BungeePacket.MOTD, cache.getUniqueId());

			if (!self)
				tellSuccess(Lang.of("Commands.Motd.Success"));
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return completeLastWordPlayerNames();
	}
}
