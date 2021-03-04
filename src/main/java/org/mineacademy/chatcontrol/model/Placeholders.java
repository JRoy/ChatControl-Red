package org.mineacademy.chatcontrol.model;

import java.util.Date;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleExpansion;
import org.mineacademy.fo.settings.SimpleSettings;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Dynamically insert data variables for PlaceholderAPI
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Placeholders extends SimpleExpansion {

	/**
	 * The singleton of this class
	 */
	@Getter
	private static final SimpleExpansion instance = new Placeholders();

	/**
	 * @see org.mineacademy.fo.model.SimpleExpansion#onReplace(org.bukkit.command.CommandSender, java.lang.String)
	 */
	@Override
	protected String onReplace(@NonNull CommandSender sender, String identifier) {
		final Player player = sender instanceof Player && ((Player) sender).isOnline() ? (Player) sender : null;
		final PlayerCache cache = player != null ? PlayerCache.from((Player) sender) : null;

		//
		// Variables that do not require any sender
		//
		switch (identifier) {
			case "label_channel":
				return Settings.Channels.COMMAND_ALIASES.get(0);
			case "label_ignore":
				return Settings.Ignore.COMMAND_ALIASES.get(0);
			case "label_mail":
				return Settings.Mail.COMMAND_ALIASES.get(0);
			case "label_me":
				return Settings.Me.COMMAND_ALIASES.get(0);
			case "label_mute":
				return Settings.Mute.COMMAND_ALIASES.get(0);
			case "label_motd":
				return Settings.Motd.COMMAND_ALIASES.get(0);
			case "label_tag":
				return Settings.Tag.COMMAND_ALIASES.get(0);
			case "label_reply":
				return Settings.PrivateMessages.REPLY_ALIASES.get(0);
			case "label_spy":
				return Settings.Spy.COMMAND_ALIASES.get(0);
			case "label_tell":
				return Settings.PrivateMessages.TELL_ALIASES.get(0);
			case "label_toggle":
				return Settings.Toggle.COMMAND_ALIASES.get(0);
		}

		//
		// Variables that accept any command sender
		//
		if (Settings.Channels.ENABLED) {
			if (identifier.startsWith("player_is_spying_") || identifier.startsWith("player_in_channel_")) {

				// Fix for Discord sender
				if (!(sender instanceof Player))
					return "false";

				final String channelName = join(3);

				if (identifier.startsWith("player_is_spying_")) {
					if (!Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT) || cache.isInChannel(channelName))
						return "false";

					return String.valueOf(cache.isSpyingChannel(channelName) || cache.getSpyingSectors().contains(Spy.Type.CHAT));
				}

				else
					return String.valueOf(cache.isInChannel(channelName));
			}

			else if (identifier.startsWith("player_channel_mode_")) {

				// Fix for Discord sender
				if (!(sender instanceof Player))
					return Lang.of("None");

				final String channelName = join(3);
				final Channel channel = Channel.findChannel(channelName);

				if (channel != null)
					return cache.isInChannel(channelName) ? cache.getChannelMode(channel).getKey() : Lang.of("None");

				else
					return Lang.of("None");
			}
		}

		//
		// Player-only variables
		//
		if (player != null) {

			final long lastActive = player.getLastPlayed();

			if ("channel".equals(identifier) && Settings.Channels.ENABLED && !Settings.Channels.IGNORE_WORLDS.contains(((Player) sender).getWorld().getName())) {
				final Channel writeChannel = cache.getWriteChannel();

				return writeChannel != null ? writeChannel.getName() : Lang.of("None").toLowerCase();
			}

			else if ("nick".equals(identifier) || "player_nick".equals(identifier) || "nick_tag".equals(identifier) || "tag_nick".equals(identifier)) {

				// If nicks are enabled use our own
				if (Settings.Tag.APPLY_ON.contains(Tag.Type.NICK)) {
					final boolean hasNick = cache.hasTag(Tag.Type.NICK);

					return hasNick ? Settings.Tag.NICK_PREFIX + cache.getTag(Tag.Type.NICK) : player.getName();
				}

				return HookManager.getNickColored(sender);

			} else if (("prefix_tag".equals(identifier) || "tag_prefix".equals(identifier)) && Settings.Tag.APPLY_ON.contains(Tag.Type.PREFIX))
				return Common.getOrDefault(cache != null ? cache.getTag(Tag.Type.PREFIX) : null, "");

			else if (("suffix_tag".equals(identifier) || "tag_suffix".equals(identifier)) && Settings.Tag.APPLY_ON.contains(Tag.Type.SUFFIX))
				return Common.getOrDefault(cache != null ? cache.getTag(Tag.Type.SUFFIX) : null, "");

			else if ("player_newcomer".equals(identifier))
				return String.valueOf(Newcomer.isNewcomer(player));

			else if ("last_active".equals(identifier))
				return lastActive == 0 ? Lang.of("None").toLowerCase() : SimpleSettings.TIMESTAMP_FORMAT.format(new Date(lastActive));

			else if ("last_active_elapsed".equals(identifier))
				return lastActive == 0 ? Lang.of("None").toLowerCase() : TimeUtil.formatTimeShort((System.currentTimeMillis() - lastActive) / 1000);

			else if ("last_active_elapsed_seconds".equals(identifier))
				return lastActive == 0 ? Lang.of("None").toLowerCase() : String.valueOf((System.currentTimeMillis() - lastActive) / 1000);

			if (args.length > 1 && "data".equalsIgnoreCase(args[0])) {
				final String key = join(1);
				final Object value = cache.getRuleData(key);

				return value != null ? SerializeUtil.serialize(value).toString() : "";
			}
		}

		return NO_REPLACE;
	}
}
