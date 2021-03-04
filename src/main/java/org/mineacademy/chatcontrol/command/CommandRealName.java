package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.operator.Tag.Type;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.settings.SimpleLocalization;

public final class CommandRealName extends ChatControlCommand {

	public CommandRealName() {
		super(Settings.RealName.COMMAND_ALIASES);

		setUsage(Lang.of("Commands.Real_Name.Usage"));
		setDescription(Lang.of("Commands.Real_Name.Description"));
		setPermission(Permissions.Command.REAL_NAME);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length <= 1);
		checkBoolean(args.length == 1 || isPlayer(), SimpleLocalization.Commands.CONSOLE_MISSING_PLAYER_NAME);

		pollCache(args.length == 1 ? args[0] : sender.getName(), cache -> {
			final boolean hasNick = Settings.Tag.APPLY_ON.contains(Type.NICK) && cache.hasTag(Type.NICK);

			tellInfo(Lang.ofScript("Commands.Real_Name.Success", SerializedMap.of("hasNick", hasNick), cache.getPlayerName(), Common.getOrEmpty(cache.getTag(Type.NICK))));
		});
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
