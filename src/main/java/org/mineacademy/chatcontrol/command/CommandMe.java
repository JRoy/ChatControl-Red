package org.mineacademy.chatcontrol.command;

import java.util.List;
import java.util.UUID;

import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.model.SimpleComponent;

public final class CommandMe extends ChatControlCommand {

	public CommandMe() {
		super(Settings.Me.COMMAND_ALIASES);

		setUsage(Lang.of("Commands.Me.Usage"));
		setDescription(Lang.of("Commands.Me.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.ME);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Me.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		final String message = Colors.addColorsForPerms(sender, joinArgs(0), Colors.Type.ME);
		final Format format = Format.parse(Settings.Me.FORMAT);

		final SimpleComponent component = format.build(sender, message);
		final UUID senderId = isPlayer() ? getPlayer().getUniqueId() : FoConstants.NULL_UUID;
		final boolean bypassReach = hasPerm(Permissions.Bypass.REACH);

		Players.showMe(senderId, bypassReach, component);

		if (!isPlayer())
			component.send(sender);

		if (BungeeCord.ENABLED)
			BungeeUtil.tellBungee(BungeePacket.ME, senderId, bypassReach, component.serialize());
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return completeLastWordPlayerNames();
	}
}
