package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.menu.SpyMenu;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.Spy.Type;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;

public final class CommandSpy extends ChatControlCommand {

	public CommandSpy() {
		super(Settings.Spy.COMMAND_ALIASES);

		setUsage(Lang.of("Commands.Spy.Usage"));
		setDescription(Lang.of("Commands.Spy.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.SPY);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		final List<String> lines = new ArrayList<>();

		lines.addAll(Lang.ofList("Commands.Spy.Usages_1"));

		if (Settings.Channels.ENABLED && Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT))
			lines.addAll(Lang.ofList("Commands.Spy.Usages_2"));

		lines.addAll(Lang.ofList("Commands.Spy.Usages_3"));

		return Common.toArray(lines);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		final String param = args[0];

		if ("list".equals(param)) {

			pollCaches(caches -> {
				checkBoolean(!caches.isEmpty(), Lang.of("Commands.Spy.No_Stored"));

				final List<SimpleComponent> list = new ArrayList<>();

				for (final PlayerCache diskCache : caches) {
					final boolean spyingSomething = diskCache.isSpyingSomethingEnabled();
					final String playerName = diskCache.getPlayerName();

					if (spyingSomething)
						list.add(SimpleComponent
								.of(" &7- ")

								.append("&8[&4X&8]")
								.onHover(Lang.ofArray("Commands.Spy.List_Tooltip", playerName))
								.onClickRunCmd("/" + getLabel() + " off " + playerName)

								.append(" &f" + playerName)
								.onHover(getSpyingStatus(diskCache))

						);
				}

				new ChatPaginator()
						.setFoundationHeader(Lang.of("Commands.Spy.List_Header"))
						.setPages(list)
						.send(sender);
			});

			return;
		}

		if ("toggle".equals(param)) {
			checkArgs(2, Lang.of("Commands.Spy.Toggle_No_Type", Common.join(Settings.Spy.APPLY_ON)));

			final Spy.Type type = findEnum(Spy.Type.class, args[1], condition -> Settings.Spy.APPLY_ON.contains(condition),
					Lang.of("Commands.Invalid_Type", args[1], Common.join(Settings.Spy.APPLY_ON)));
			final boolean isChat = type == Type.CHAT;

			Channel channel = null;

			if (isChat) {
				checkArgs(3, Lang.of("Commands.Spy.Toggle_No_Channel", Common.join(Channel.getChannelNames())));

				channel = findChannel(args[2]);
			}

			final Channel finalChannel = channel;

			checkBoolean(isPlayer() || args.length == (isChat ? 4 : 3), Lang.of("Commands.Console_Missing_Player_Name"));

			pollCache(args.length == (isChat ? 4 : 3) ? args[isChat ? 3 : 2] : sender.getName(), cache -> {
				final boolean isSpying = isChat ? cache.isSpyingChannel(finalChannel) : cache.isSpying(type);

				if (isChat)
					cache.setSpyingChannel(finalChannel, !isSpying);
				else
					cache.setSpying(type, !isSpying);

				tellSuccess(Lang.ofScript("Commands.Spy.Toggle", SerializedMap.of("isSpying", isSpying), cache.getPlayerName(), isChat ? Lang.of("Commands.Spy.Type_Channel") + " " + finalChannel.getName() : type.getLocalized()));
			});

			return;
		}

		checkUsage(args.length <= 2);
		checkBoolean(isPlayer() || args.length == 2, Lang.of("Commands.Console_Missing_Player_Name"));

		pollDiskCacheOrSelf(args.length == 2 ? args[1] : sender.getName(), cache -> {
			if ("status".equals(param)) {
				checkBoolean(cache.isSpyingSomething(), Lang.of("Commands.Spy.No_Spying", cache.getPlayerName()));

				tellNoPrefix(Lang.of("Commands.Spy.Status_1", cache.getPlayerName()).replace("{chat_line_smooth}", Common.chatLineSmooth()));
				tellNoPrefix(getSpyingStatus(cache));
				tellNoPrefix(Lang.of("Commands.Spy.Status_2", cache.getPlayerName()).replace("{chat_line_smooth}", Common.chatLineSmooth()));
			}

			else if ("menu".equals(param)) {
				checkConsole();

				SpyMenu.showTo(cache, getPlayer());
			}

			else if ("off".equals(param)) {
				checkBoolean(cache.isSpyingSomethingEnabled(), Lang.of("Commands.Spy.No_Spying", cache.getPlayerName()));

				cache.setSpyingOff();
				updateBungeeData(cache);

				tellSuccess(Lang.of("Commands.Spy.Toggle_Off", cache.getPlayerName()));

			} else if ("on".equals(param)) {
				cache.setSpyingOn();
				updateBungeeData(cache);

				tellSuccess(Lang.of("Commands.Spy.Toggle_On", cache.getPlayerName()));

			} else
				returnInvalidArgs();
		});
	}

	private List<String> getSpyingStatus(PlayerCache diskCache) {
		final List<String> hover = new ArrayList<>();

		if (!diskCache.getSpyingSectors().isEmpty()) {
			hover.add(Lang.of("Commands.Spy.Status_Sectors"));

			for (final Spy.Type type : diskCache.getSpyingSectors())
				if (type != Type.CHAT && Settings.Spy.APPLY_ON.contains(type))
					hover.add(" &7- &f" + type.getLocalized());
		}

		if (Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT))
			if (!diskCache.getSpyingChannels().isEmpty()) {
				if (!hover.isEmpty())
					hover.add("&r");

				hover.add(Lang.of("Commands.Spy.Status_Channels"));

				for (final String channelName : diskCache.getSpyingChannels())
					hover.add(" &7- &f" + channelName);
			}

		return hover;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("status", "menu", "off", "toggle", "list");

		if (args.length == 2 && Arrays.asList("status", "menu", "off", "on").contains(args[0]))
			return completeLastWordPlayerNames();

		if (args.length > 1 && "toggle".equals(args[0])) {
			if (args.length == 2)
				return completeLastWord(Settings.Spy.APPLY_ON);

			if (args.length == 3)
				return "chat".equals(args[1]) && Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT) ? completeLastWord(Channel.getChannelNames()) : completeLastWordPlayerNames();

			if (args.length == 4 && "chat".equals(args[1]))
				return completeLastWordPlayerNames();
		}

		return NO_COMPLETE;
	}
}
