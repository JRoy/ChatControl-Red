package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.menu.ColorMenu;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.ItemUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.remain.CompChatColor;
import org.mineacademy.fo.remain.Remain;

public final class ChatControlColor extends ChatControlSubCommand {

	public ChatControlColor() {
		super("color/c");

		setUsage(Lang.of("Commands.Color.Usage"));
		setDescription(Lang.of("Commands.Color.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.COLOR);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		final List<String> lines = new ArrayList<>();

		lines.addAll(Lang.ofList("Commands.Color.Usages_1"));

		if (Remain.hasHexColors())
			lines.addAll(Lang.ofList("Commands.Color.Usages_Hex_1"));

		lines.addAll(Lang.ofList("Commands.Color.Usages_2"));

		if (Remain.hasHexColors())
			lines.addAll(Lang.ofList("Commands.Color.Usages_Hex_2"));

		return Common.toArray(lines);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length < 4);
		checkBoolean(isPlayer() || (args.length > ("status".equals(args[0]) ? 1 : 2)), Lang.of("Commands.Console_Missing_Player_Name"));

		pollCache(args.length == 2 && "status".equals(args[0]) ? args[1] : args.length == 3 ? args[2] : getPlayer().getName(), cache -> {
			final String playerName = cache.getPlayerName();

			final String colorName = args[0];
			final String decorationName = args.length >= 2 ? args[1] : "";

			final List<String> playerMessage = new ArrayList<>();
			final boolean self = playerName.equals(sender.getName());
			final String pronoun = (self ? Lang.of("Your").toLowerCase() : Lang.of("Player.Possessive_Form", playerName)).replace("{player}", cache.getPlayerName());

			if (!self)
				checkPerm(Permissions.Command.COLOR_OTHERS);

			if ("menu".equals(colorName)) {
				checkConsole();

				ColorMenu.showTo(getPlayer());
				return;

			} else if ("status".equals(colorName)) {
				checkBoolean(cache.hasChatColor() || cache.hasChatDecoration(), Lang.of("Commands.Color.Not_Saved", cache.getPlayerName()));

				String message = cache.getPlayerName() + " " + Lang.of("Player.Has") + " ";

				if (cache.hasChatColor())
					message += cache.getChatColor().toReadableString() + " " + Lang.of("Commands.Color.Chat_Color");

				if (cache.hasChatDecoration())
					message += (cache.hasChatColor() ? " " + Lang.of("And") + " " : "") + cache.getChatDecoration().getName() + " " + Lang.of("Commands.Color.Decoration");

				tellInfo(message + ".");
				return;

			} else if ("reset".equals(colorName)) {
				cache.setChatColor(null);

				playerMessage.add(Lang.of("Commands.Color.Reset", pronoun));

			} else {
				CompChatColor color;

				try {
					color = CompChatColor.of(colorName);

				} catch (final IllegalArgumentException ex) {
					tellError(Lang.ofScript("Commands.Color.Invalid_Color", SerializedMap.of("hasHex", Remain.hasHexColors()), Common.join(Colors.getGuiColorsForPermission(sender), ", ", c -> c.getName().toLowerCase())));

					return;
				}

				checkBoolean(color.getColor() != null, Lang.of("Commands.Color.Decoration_Not_Allowed"));
				checkPerm(Colors.getGuiColorPermission(sender, color));

				cache.setChatColor(color);
				playerMessage.add(Lang.of("Commands.Color.Success_Color", pronoun, color.toReadableString()));
			}

			if ("reset".equals(decorationName)) {
				cache.setChatDecoration(null);

				playerMessage.add(Lang.of("Commands.Color.Reset_Decoration", pronoun));

			} else if (!"".equals(decorationName)) {
				CompChatColor decoration;

				try {
					decoration = CompChatColor.of(decorationName);

				} catch (final IllegalArgumentException ex) {
					tellError(Lang.of("Commands.Color.Invalid_Decoration", Common.join(Colors.getGuiDecorationsForPermission(sender), ", ", c -> c.getName().toLowerCase())));
					return;
				}

				checkBoolean(decoration.getColor() == null, Lang.of("Commands.Color.Color_Not_Allowed"));
				checkPerm(Permissions.Color.GUI.replace("{color}", decoration.getName()));

				cache.setChatDecoration(decoration);
				playerMessage.add(Lang.of("Commands.Color.Success_Decoration", pronoun, decoration + ItemUtil.bountify(decoration.getName())));
			}

			final String message = Common.join(playerMessage, " " + Lang.of("And") + " ").toLowerCase() + ".";
			tellSuccess(message.substring(0, 1).toUpperCase() + message.substring(1));

			updateBungeeData(cache);
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord(Common.convert(Colors.getGuiColorsForPermission(sender), CompChatColor::getName), Arrays.asList("status", "reset", "menu"));

		if (args.length == 2)
			if ("status".equals(args[0]))
				return completeLastWordPlayerNames();
			else
				return completeLastWord(Common.joinArrays(Common.convert(Colors.getGuiDecorationsForPermission(sender), CompChatColor::getName), Arrays.asList("reset")));

		if (args.length == 3 && !"status".equals(args[0]) && hasPerm(Permissions.Command.COLOR_OTHERS))
			return completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
