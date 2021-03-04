package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.WordUtils;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Toggle;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.PlayerMessage.Type;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;

public final class CommandToggle extends ChatControlCommand {

	public CommandToggle() {
		super(Settings.Toggle.COMMAND_ALIASES);

		setUsage("<type> [group]");
		setDescription("Toggle seeing parts of the plugin.");
		setMinArguments(1);
		setPermission(Permissions.Command.TOGGLE.replace(".{type}", ""));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		final List<String> lines = new ArrayList<>();

		lines.addAll(Lang.ofList("Commands.Toggle.Usages_1", Common.join(Settings.Toggle.APPLY_ON), Common.join(Settings.Messages.APPLY_ON)));

		if (canToggle(Toggle.CHAT))
			lines.add(Lang.of("Commands.Toggle.Usages_Chat"));

		if (canToggle(PlayerMessage.Type.JOIN))
			lines.add(Lang.of("Commands.Toggle.Usages_Join"));

		if (canToggle(PlayerMessage.Type.TIMED))
			lines.add(Lang.of("Commands.Toggle.Usages_Timed"));

		if (canToggle(Toggle.PM))
			lines.add(Lang.of("Commands.Toggle.Usages_Private_Message"));

		lines.add(Lang.of("Commands.Toggle.Usages_2"));

		return Common.toArray(lines);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkConsole();
		checkUsage(args.length <= 2);

		final String typeRaw = args[0];

		final PlayerCache cache = PlayerCache.from(getPlayer());
		Toggle toggle = null;
		PlayerMessage.Type messageType = null;

		try {
			toggle = Toggle.fromKey(typeRaw);

			if (!canToggle(toggle))
				toggle = null;

		} catch (final IllegalArgumentException ex) {
		}

		try {
			messageType = PlayerMessage.Type.fromKey(typeRaw);

			if (!canToggle(messageType))
				messageType = null;

		} catch (final IllegalArgumentException ex) {
		}

		if ("list".equals(typeRaw)) {
			final List<SimpleComponent> pages = new ArrayList<>();

			if (!Settings.Messages.APPLY_ON.isEmpty()) {
				for (final Entry<Type, Set<String>> entry : cache.getIgnoredMessages().entrySet())
					if (canToggle(entry.getKey())) {
						pages.add(Lang.ofComponent("Commands.Toggle.List_Line", WordUtils.capitalizeFully(entry.getKey().getLocalized())));

						for (final String groupName : entry.getValue())
							pages.add(SimpleComponent
									.of(" &7- &f")
									.append(groupName)
									.onHover(Lang.ofArray("Commands.Toggle.List_Tooltip"))
									.onClickRunCmd("/" + getLabel() + " " + entry.getKey() + " " + groupName));
					}

				if (!pages.isEmpty())
					pages.add(SimpleComponent.empty());
			}

			if (!Settings.Toggle.APPLY_ON.isEmpty()) {
				pages.add(SimpleComponent.of(Lang.of("Commands.Toggle.List_Plugin_Parts_Title")));

				for (final Toggle playerToggle : Toggle.values())
					if (canToggle(playerToggle))
						pages.add(SimpleComponent
								.of(" &7- &f" + WordUtils.capitalizeFully(playerToggle.getDescription()) + "&7: ")
								.append(Lang.ofScript("Commands.Toggle.List_Plugin_Parts", SerializedMap.of("isIgnoring", cache.isIgnoringPart(playerToggle))))
								.onHover(Lang.ofArray("Commands.Toggle.List_Plugin_Parts_Toggle"))
								.onClickRunCmd("/" + getLabel() + " " + playerToggle.getKey() + " " + cache.getPlayerName())

						);
			}

			new ChatPaginator()
					.setPages(pages)
					.setFoundationHeader(Lang.of("Commands.Toggle.List_Plugin_Parts_Header", cache.getPlayerName()))
					.send(sender);

			return;
		}

		checkBoolean(toggle != null || messageType != null, Lang.of("Commands.Toggle.Invalid_Type", Common.join(Settings.Toggle.APPLY_ON), Common.join(Settings.Messages.APPLY_ON)));

		if (messageType != null) {
			if (args.length == 2) {
				final PlayerMessage message = findMessage(messageType, args[1]);
				final boolean ignoring = cache.isIgnoringMessage(message);

				cache.setIgnoringMessage(message, !ignoring);
				tellSuccess(Lang.ofScript("Commands.Toggle.Toggled_Group", SerializedMap.of("ignoring", ignoring), messageType.getLocalized(), message.getGroup()));
			}

			else {
				final boolean ignoring = cache.isIgnoringMessages(messageType);

				cache.setIgnoringMessages(messageType, !ignoring);
				tellSuccess(Lang.ofScript("Commands.Toggle.Toggled_Group_All", SerializedMap.of("ignoring", ignoring), messageType.getLocalized()));
			}

		} else {
			final boolean ignoring = cache.isIgnoringPart(toggle);

			cache.setIgnoredPart(toggle, !ignoring);
			tellSuccess(Lang.ofScript("Commands.Toggled_Plugin_Part", SerializedMap.of("ignoring", ignoring), toggle.getDescription()));
		}
	}

	/*
	 * Return true if the toggle is enabled from settings
	 */
	private boolean canToggle(Toggle toggle) {
		return Settings.Toggle.APPLY_ON.contains(toggle) && hasPerm(Permissions.Command.TOGGLE.replace("{type}", toggle.getKey()));
	}

	/*
	 * Return true if the player message is enabled from settings
	 */
	private boolean canToggle(PlayerMessage.Type type) {
		return Settings.Messages.APPLY_ON.contains(type) && hasPerm(Permissions.Command.TOGGLE.replace("{type}", type.getKey()));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1) {
			final Set<String> completed = Common.newSet("list");

			for (final PlayerMessage.Type type : Settings.Messages.APPLY_ON)
				if (canToggle(type))
					completed.add(type.getKey());

			for (final Toggle type : Settings.Toggle.APPLY_ON)
				if (canToggle(type))
					completed.add(type.getKey());

			return completeLastWord(completed);
		}

		if (args.length == 2) {
			PlayerMessage.Type type;

			try {
				type = PlayerMessage.Type.fromKey(args[0]);

				if (type != null && !canToggle(type))
					return NO_COMPLETE;

			} catch (final IllegalArgumentException ex) {
				return NO_COMPLETE;
			}

			return completeLastWord(PlayerMessages.getInstance().getMessageNames(type));
		}

		return NO_COMPLETE;
	}
}
