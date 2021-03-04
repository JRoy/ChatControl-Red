package org.mineacademy.chatcontrol.command;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.UserMap;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.Remain;

public final class CommandIgnore extends ChatControlCommand {

	public CommandIgnore() {
		super(Settings.Ignore.COMMAND_ALIASES);

		setUsage(Lang.of("Commands.Ignore.Usage"));
		setDescription(Lang.of("Commands.Ignore.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.IGNORE);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Ignore.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length <= 2);

		final String param = args[0];
		final boolean otherPlayer = args.length == 2;

		checkBoolean(isPlayer() || otherPlayer, Lang.of("Commands.Console_Missing_Player_Name"));

		if (otherPlayer)
			checkPerm(Permissions.Command.IGNORE_OTHERS);

		if ("list".equals(param)) {
			checkPerm(Permissions.Command.IGNORE_LIST);

			pollCache(otherPlayer ? args[1] : sender.getName(), listPlayer -> {
				checkBoolean(!listPlayer.getIgnoredPlayers().isEmpty(), Lang.ofScript("Commands.Ignore.Not_Ignoring", SerializedMap.of("otherPlayer", otherPlayer), listPlayer.getPlayerName()));

				new ChatPaginator()
						.setFoundationHeader(Lang.of("Commands.Ignore.List_Header", listPlayer.getPlayerName()))
						.setPages(Common.convert(listPlayer.getIgnoredPlayers(), id -> {
							final String name = Common.getOrDefaultStrict(UserMap.getInstance().getName(id), id.toString());

							return SimpleComponent
									.of(" &7- " + name)
									.onHover(Lang.of("Commands.Ignore.List_Tooltip_Stop", name))
									.onClickRunCmd("/" + getLabel() + " " + listPlayer.getPlayerName() + " " + name);
						}))
						.send(sender);
			});

			return;
		}

		pollCache(otherPlayer ? args[0] : sender.getName(), forCache -> {
			pollCache(args[otherPlayer ? 1 : 0], targetCache -> {
				checkBoolean(!forCache.getPlayerName().equals(targetCache.getPlayerName()), Lang.of("Commands.Ignore.Cannot_Ignore_Self"));

				final UUID targetId = targetCache.getUniqueId();
				final boolean ignored = forCache.isIgnoringPlayer(targetId);

				final Player otherOnline = Remain.getPlayerByUUID(targetId);

				if (!ignored && otherOnline != null && otherOnline.isOnline() && PlayerUtil.hasPerm(otherOnline, Permissions.Bypass.REACH))
					returnTell(Lang.of("Commands.Ignore.Cannot_Ignore_Admin"));

				forCache.setIgnoredPlayer(targetId, !ignored);

				// Hook into CMI/Essentials async to prevent server freeze
				Common.runAsync(() -> HookManager.setIgnore(forCache.getUniqueId(), targetId, !ignored));

				tellSuccess(Lang.ofScript("Commands.Ignore.Success", SerializedMap.ofArray("otherPlayer", otherPlayer, "ignored", ignored), forCache.getPlayerName(), targetCache.getPlayerName()));

				updateBungeeData(forCache);
			});
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord(
					Players.getPlayerNames(sender).stream().filter(name -> !name.equals(sender.getName())).collect(Collectors.toList()),
					Arrays.asList(hasPerm(Permissions.Command.IGNORE_LIST) ? "list" : ""));

		if (args.length == 2 && hasPerm(Permissions.Command.IGNORE_OTHERS))
			return completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}