package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.remain.CompChatColor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Class holding color-related utilities
 */
public final class Colors {

	/**
	 * Return the message with colors applied for those the player has permission
	 * and also prefixed with player's /chc color preferences
	 *
	 * @param sender
	 * @param message
	 * @param type
	 *
	 * @return
	 */
	public static String addColorsForPermsAndChat(CommandSender sender, String message) {
		if (sender instanceof Player) {
			final PlayerCache cache = PlayerCache.from((Player) sender);

			if (cache.hasChatDecoration())
				message = cache.getChatDecoration() + message;

			if (cache.hasChatColor())
				message = cache.getChatColor() + message;
		}

		return addColorsForPerms(sender, message, Type.CHAT);
	}

	/**
	 * Return the message with colors applied for those the player has permission
	 *
	 * @param player
	 * @param message
	 * @param type
	 *
	 * @return
	 */
	public static String addColorsForPerms(CommandSender sender, String message, Type type) {

		final boolean enabled = Settings.Colors.APPLY_ON.contains(type) && PlayerUtil.hasPerm(sender, Permissions.Color.USE.replace("{apply_on}", type.getKey()));

		// Set colors
		for (final CompChatColor color : CompChatColor.values())
			if (PlayerUtil.hasPerm(sender, Permissions.Color.LETTER.replace("{color}", color.getName())))
				message = message.replace("&" + color.getCode(), enabled ? color.toString() : "");

		// HEX support {}
		Matcher match = Common.RGB_HEX_BRACKET_COLOR_REGEX.matcher(message);

		while (match.find()) {
			final String colorCode = match.group(1);
			String replacement = "";

			try {
				replacement = CompChatColor.of("#" + colorCode).toString();

			} catch (final IllegalArgumentException ex) {
				continue;
			}

			// Require permissions per HEX color
			final boolean hasPerm = PlayerUtil.hasPerm(sender, Permissions.Color.HEX.replace("{color}", colorCode));

			message = message.replaceAll("\\{#" + colorCode + "\\}", hasPerm && enabled ? replacement : "");
		}

		// HEX support
		match = Common.RGB_HEX_COLOR_REGEX.matcher(message);

		while (match.find()) {
			final String colorCode = match.group(1);
			String replacement = "";

			try {
				replacement = CompChatColor.of("#" + colorCode).toString();

			} catch (final IllegalArgumentException ex) {
				continue;
			}

			// Require permissions per HEX color
			final boolean hasPerm = PlayerUtil.hasPerm(sender, Permissions.Color.HEX.replace("{color}", colorCode));

			message = message.replaceAll("#" + colorCode, hasPerm && enabled ? replacement : "");
		}

		return message;
	}

	/**
	 * Compile the permission for the color, if HEX or not, for the given sender
	 *
	 * @param sender
	 * @param color
	 * @return
	 */
	public static String getGuiColorPermission(CommandSender sender, CompChatColor color) {
		final String name = color.getName();

		if (color.isHex())
			return Permissions.Color.HEX.replace("{color}", name.substring(1));

		return Permissions.Color.GUI.replace("{color}", name);
	}

	/**
	 * Return list of colors the sender has permission for
	 *
	 * @param sender
	 * @return
	 */
	public static List<CompChatColor> getGuiColorsForPermission(CommandSender sender) {
		return loadGuiColorsForPermission(sender, CompChatColor.getColors());
	}

	/**
	 * Return list of decorations the sender has permission for
	 *
	 * @param sender
	 * @return
	 */
	public static List<CompChatColor> getGuiDecorationsForPermission(CommandSender sender) {
		return loadGuiColorsForPermission(sender, CompChatColor.getDecorations());
	}

	/*
	 * Compile list of colors the sender has permission to use
	 */
	private static List<CompChatColor> loadGuiColorsForPermission(CommandSender sender, List<CompChatColor> list) {
		final List<CompChatColor> selected = new ArrayList<>();

		for (final CompChatColor color : list)
			if (PlayerUtil.hasPerm(sender, Permissions.Color.GUI.replace("{color}", color.getName())))
				selected.add(color);

		return selected;
	}

	/**
	 * Represents a message type
	 */
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Use colors in chat
		 */
		CHAT("chat"),

		/**
		 * Use colors in me
		 */
		ME("me"),

		/**
		 * Use colors in prefixes
		 */
		PREFIX("prefix"),

		/**
		 * Use colors in nicks
		 */
		NICK("nick"),

		/**
		 * Use colors in custom suffix
		 */
		SUFFIX("suffix"),

		/**
		 * Use colors in PMs
		 */
		PRIVATE_MESSAGE("private_message")

		;

		/**
		 * The saveable non-obfuscated key
		 */
		@Getter
		private final String key;

		/**
		 * Attempt to load a log type from the given config key
		 *
		 * @param key
		 * @return
		 */
		public static Type fromKey(String key) {
			for (final Type mode : values())
				if (mode.key.equalsIgnoreCase(key))
					return mode;

			throw new IllegalArgumentException("No such message type: " + key + ". Available: " + Common.join(values()));
		}

		/**
		 * Returns {@link #getKey()}
		 */
		@Override
		public String toString() {
			return this.key;
		}
	}
}
