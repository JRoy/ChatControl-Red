package org.mineacademy.chatcontrol.command;

import java.util.List;
import java.util.UUID;

import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Packets;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;

public final class ChatControlPurge extends ChatControlSubCommand {

	public ChatControlPurge() {
		super("purge");

		setUsage(Lang.of("Commands.Purge.Usage"));
		setDescription(Lang.of("Commands.Purge.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.PURGE);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {

		pollCache(args[0], cache -> {
			final UUID uniqueId = cache.getUniqueId();

			Packets.getInstance().removeMessage(Packets.RemoveMode.ALL_MESSAGES_FROM_SENDER, uniqueId);

			if (BungeeCord.ENABLED)
				BungeeUtil.tellBungee(BungeePacket.REMOVE_MESSAGE_BY_UUID, Packets.RemoveMode.ALL_MESSAGES_FROM_SENDER.getKey(), uniqueId);

			tellSuccess(Lang.of("Commands.Purge.Success", cache.getPlayerName()));
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
