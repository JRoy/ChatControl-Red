package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.apache.commons.lang.WordUtils;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.operator.Tag.TagCheck;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;

public final class CommandTag extends ChatControlCommand {

	public CommandTag() {
		super(Settings.Tag.COMMAND_ALIASES);

		setUsage(Lang.of("Commands.Tag.Usage"));
		setDescription(Lang.of("Commands.Tag.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.TAG.replace(".{type}", ""));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Tag.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkConsole();

		final PlayerCache cache = PlayerCache.from(getPlayer());

		if ("list".equals(args[0])) {
			checkUsage(args.length == 1);

			tellInfo(Lang.of("Commands.Tag.Your_Tags"));

			boolean shownAtLeastOne = false;

			for (final Tag.Type tag : Settings.Tag.APPLY_ON)
				if (hasPerm(Permissions.Command.TAG.replace("{type}", tag.getKey()))) {
					tellNoPrefix(" &7- " + WordUtils.capitalize(tag.getKey()) + ": &f" + Common.getOrDefault(cache.getTag(tag), Lang.of("None") + "."));

					shownAtLeastOne = true;
				}

			if (!shownAtLeastOne)
				tellNoPrefix("&7 - " + Lang.of("None"));

			return;
		}

		else if ("off".equals(args[0])) {
			checkBoolean(!cache.getTags().isEmpty(), Lang.of("Commands.Tag.Off_No_Tags"));

			for (final Tag.Type tag : Settings.Tag.APPLY_ON)
				cache.setTag(tag, null);

			final Player cachePlayer = cache.toPlayer();

			if (cachePlayer != null)
				Players.setTablistName(sender, cachePlayer);

			tellSuccess(Lang.of("Commands.Tag.Off_All"));
			return;
		}

		final Tag.Type type = findTag(args[0]);
		checkPerm(Permissions.Command.TAG.replace("{type}", type.getKey()));

		if (args.length == 1) {
			tellInfo(Lang.of("Commands.Tag.Status_Self", type, Common.getOrDefault(cache.getTag(type), Lang.of("None") + ".")));

			return;
		}

		checkBoolean(type != Tag.Type.NICK || args.length == 2, Lang.of("Commands.Tag.No_Spaces"));

		String tag = joinArgs(1);

		// Colorize according to senders permissions
		tag = Common.stripColorsLetter(Colors.addColorsForPerms(sender, tag, type.getColorType()));

		final String colorlessTag = Common.stripColors(tag);

		checkBoolean(!colorlessTag.isEmpty(), Lang.of("Commands.Tag.No_Colors_Only"));
		checkBoolean(colorlessTag.length() <= Settings.Tag.MAX_NICK_LENGTH, Lang.of("Commands.Tag.Exceeded_Length", colorlessTag.length(), Settings.Tag.MAX_NICK_LENGTH));

		// Apply rules!
		final TagCheck check = Tag.filter(type, sender, tag);
		checkBoolean(!check.isCancelledSilently(), Lang.of("Commands.Tag.Not_Allowed"));

		tag = check.getMessage();

		setTag(type, cache, tag);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord(Settings.Tag.APPLY_ON, "list");

		if (args.length == 2)
			return completeLastWord("off");

		return NO_COMPLETE;
	}
}