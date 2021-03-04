package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;

public final class ChatControlUpdate extends ChatControlSubCommand {

	public ChatControlUpdate() {
		super("update");

		setUsage(Lang.of("Commands.Update.Usage"));
		setDescription(Lang.of("Commands.Update.Description"));
		setPermission(Permissions.Command.UPDATE);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length <= 1);
		checkBoolean(Settings.TabList.ENABLED, Lang.of("Commands.Update.Disabled"));

		final Player player = findPlayerOrSelf(args.length == 1 ? args[0] : null);

		Players.setTablistName(player);
		tellSuccess(Lang.of("Commands.Update.Success", player.getName()));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
