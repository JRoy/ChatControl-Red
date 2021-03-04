package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.List;

import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;

public final class ChatControlTag extends ChatControlSubCommand {

	public ChatControlTag() {
		super("tag");

		setUsage(Lang.of("Commands.Tag.Admin_Usage"));
		setDescription(Lang.of("Commands.Tag.Admin_Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.TAG_ADMIN);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Tag.Admin_Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {

		if ("list".equals(args[0])) {
			checkUsage(args.length <= 2);

			final Tag.Type type = args.length == 2 ? findTag(args[1]) : null;
			final List<SimpleComponent> list = new ArrayList<>();

			pollCaches(caches -> {

				for (final PlayerCache cache : caches) {
					final String playerName = cache.getPlayerName();

					// Has no tags what so evar or doesnt hath tags we did giventh
					if (cache.getTags().isEmpty() || (type != null && !cache.hasTag(type)))
						continue;

					final String nick = Settings.Tag.APPLY_ON.contains(Tag.Type.NICK) ? cache.getTag(Tag.Type.NICK) : null;
					final String prefix = Settings.Tag.APPLY_ON.contains(Tag.Type.PREFIX) ? cache.getTag(Tag.Type.PREFIX) : null;
					final String suffix = Settings.Tag.APPLY_ON.contains(Tag.Type.SUFFIX) ? cache.getTag(Tag.Type.SUFFIX) : null;

					final SimpleComponent line = SimpleComponent.of(" &7- &f");

					if (prefix != null)
						line
								.append(prefix + " ")
								.onHover(Lang.of("Commands.Tag.Admin_Tooltip_Remove", "prefix", playerName))
								.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " prefix " + playerName + " off");

					line.append((nick != null ? nick : playerName) + " ");

					if (nick != null)
						line
								.onHover(Lang.of("Commands.Tag.Admin_Tooltip_Remove", "nick", playerName))
								.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " nick " + playerName + " off");

					if (suffix != null)
						line
								.append(suffix + " ")
								.onHover(Lang.of("Commands.Tag.Admin_Tooltip_Remove", "suffix", playerName))
								.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " suffix " + playerName + " off");

					if (nick != null)
						line.append("&7(" + playerName + ")");

					list.add(line);
				}

				checkBoolean(!list.isEmpty(), Lang.of("Commands.Tag.No_Tags"));

				new ChatPaginator()
						.setFoundationHeader(Lang.of("Commands.Tag.Admin_List"))
						.setPages(list)
						.send(sender);
			});

			return;
		}

		checkArgs(2, Lang.of("Commands.Tag.Admin_Invalid_Params"));
		final Tag.Type type = findTag(args[0]);

		pollCache(args[1], cache -> {
			final String tag = joinArgs(2);

			if (tag == null || tag.isEmpty()) {
				tellInfo(Lang
						.of("Commands.Tag.Admin_Status")
						.replace("{possessive_form}", Lang.of("Player.Possessive_Form").replace("{player}", cache.getPlayerName()))
						.replace("{type}", type.getKey())
						.replace("{tag}", Common.getOrDefault(cache.getTag(type), Lang.of("None"))));

				return;
			}

			checkBoolean(type != Tag.Type.NICK || !tag.contains(" "), Lang.of("Commands.Tag.No_Spaces"));
			checkBoolean(!Common.stripColors(tag).isEmpty(), Lang.of("Commands.Tag.No_Colors_Only"));

			setTag(type, cache, tag);
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord(Settings.Tag.APPLY_ON, "list");

		if (args.length == 2)
			if ("list".equals(args[0]))
				return completeLastWord(Settings.Tag.APPLY_ON);
			else
				return completeLastWordPlayerNames();

		if (args.length == 3 && !"list".equals(args[0]))
			return completeLastWord("off");

		return NO_COMPLETE;
	}
}
