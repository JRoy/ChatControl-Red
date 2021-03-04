package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings.MySQL;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.Variables;

public final class ChatControlInfo extends ChatControlSubCommand {

	public ChatControlInfo() {
		super("info");

		setUsage(Lang.of("Commands.Info.Usage"));
		setDescription(Lang.of("Commands.Info.Description"));
		setMinArguments(2);
		setPermission(Permissions.Command.INFO);
	}

	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Info.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length >= 2);

		final String param = args[0];

		if ("cache".equals(param) || "nick".equals(param)) {
			checkUsage(args.length > 1);

			pollCache(args[1], cache -> {
				if ("cache".equals(param)) {
					tellNoPrefix(Lang.of("Commands.Info.Cache_Player", cache.getPlayerName()));
					tellNoPrefix(Lang.of("Commands.Info.Cache_Location") + (MySQL.ENABLED ? "MySQL" : "data.db"));
					tellNoPrefix(cache.serialize().toStringFormatted()
							.replace("\t", "    ")
							.replace("'", "")
							.replace("=", "&7=&f")
							.replace("[", "&7[&f")
							.replace("]", "&7]&f")
							.replace("{", "&7{&f")
							.replace("}", "&7}&f"));
				}

				else
					tellInfo(Lang.of("Commands.Info.Cache_Nick", cache.getPlayerName(),
							Common.getOrDefaultStrict(cache.getTag(Tag.Type.NICK), "&7&o" + Lang.of("None"))));
			});

			return;
		}

		final Player player = findPlayer(args[1]);

		if ("newcomer".equals(param))
			tellInfo(Lang.ofScript("Commands.Info.Newcomer", SerializedMap.of("newcomer", Newcomer.isNewcomer(player)), player.getName()));

		else if ("variables".equals(param) || "variable".equals(param)) {
			checkArgs(3, Lang.of("Commands.Info.Variables_No_Message"));

			final String message = joinArgs(2);
			final long now = System.currentTimeMillis();
			final String replaced = Variables.replace(message, player);

			tellNoPrefix(Lang.ofScript("Commands.Info.Variables", SerializedMap.of("replaced", replaced), (System.currentTimeMillis() - now)));

		} else
			returnTell(Lang.ofArray("Commands.Invalid_Param", param, "cache, newcomer, variables"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("nick", "cache", "newcomer", "variables");

		if (args.length == 2)
			return completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
