package org.mineacademy.chatcontrol.model;

import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Some settings in settings.yml can be customized according to what permission
 * the player has.
 *
 * Permissions are named "groups" so that the group "vip" will apply if player
 * has chatcontrol.group.vip permission.
 *
 * Groups override default settings from settings.yml
 *
 * @param <T>
 */
@RequiredArgsConstructor
public final class PlayerGroup<T> {

	/**
	 * The setting key, used to prevent errors
	 */
	private final Type type;

	/**
	 * The defalt value from settings.yml
	 */
	private final T defaultValue;

	/**
	 * See {@link #getFor(Player)}, except that we return {@link #defaultValue}
	 * when sender is not player
	 *
	 * @param sender
	 * @return
	 */
	public T getFor(CommandSender sender) {
		return this.getFor(sender, null);
	}

	/**
	 * See {@link #getFor(Player)}, except that we return {@link #defaultValue}
	 * when sender is not player
	 *
	 * @param sender
	 * @return
	 */
	public T getFor(CommandSender sender, T defaultValue) {
		return sender instanceof Player ? this.getFor((Player) sender, defaultValue) : Common.getOrDefault(defaultValue, this.defaultValue);
	}

	/**
	 * Return the group setting value for the given player, reverting to the default one
	 * if not set
	 *
	 * @param player
	 * @return
	 */
	public T getFor(Player player) {
		return this.getFor(player, null);
	}

	/**
	 * Return the group setting value for the given player, reverting to the default one
	 * if not set
	 *
	 * @param player
	 * @param defaultValue
	 *
	 * @return
	 */
	public T getFor(Player player, T defaultValue) {
		return filter(groupName -> PlayerUtil.hasPerm(player, Permissions.GROUP.replace("{group}", groupName)), defaultValue);
	}

	/**
	 * Return the group setting value for the given player name, reverting to the default one
	 * if not set
	 *
	 * @param nameOrNick
	 * @return
	 */
	public T getForUUID(UUID uuid) {
		final OfflinePlayer offline = Remain.getOfflinePlayerByUUID(uuid);

		return filter(groupName -> HookManager.hasVaultPermission(offline, Permissions.GROUP.replace("{group}", groupName)), defaultValue);
	}

	/*
	 * Retrieve the first player group setting value that matches the given filter
	 */
	private T filter(Predicate<String> filter, T defaultValue) {
		for (final Entry<String, StrictMap<PlayerGroup.Type, Object>> entry : Settings.Groups.LIST.entrySet()) {
			final String groupName = entry.getKey();
			final Object groupSetting = entry.getValue().get(this.type);

			// If the group contains this and player has permission, return it
			if (groupSetting != null && filter.test(groupName))
				return (T) groupSetting;
		}

		return Common.getOrDefault(defaultValue, this.defaultValue);
	}

	/**
	 * Represent different group setting types that
	 * override defaults from settings.yml
	 */
	@Getter
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Limit for reading channels
		 */
		MAX_READ_CHANNELS(Integer.class, "Max_Read_Channels"),

		/**
		 * Chat message delay for antispam
		 */
		MESSAGE_DELAY(SimpleTime.class, "Message_Delay"),

		/**
		 * Chat message similarity for antispam
		 */
		MESSAGE_SIMILARITY(Double.class, "Message_Similarity"),

		/**
		 * Command message delay for antispam
		 */
		COMMAND_DELAY(SimpleTime.class, "Command_Delay"),

		/**
		 * Command similarity for antispam
		 */
		COMMAND_SIMILARITY(Double.class, "Command_Similarity"),

		/**
		 * Anti bot join delay
		 */
		REJOIN_COOLDOWN(SimpleTime.class, "Rejoin_Cooldown"),

		/**
		 * Sound notify color
		 */
		SOUND_NOTIFY_COLOR(String.class, "Sound_Notify_Color"),

		/**
		 * Message of the day
		 */
		MOTD(String.class, "Motd_Format"),

		;

		/**
		 * The value class that the settings must be parseable from
		 */
		private final Class<?> validClass;

		/**
		 * The config key, unobfuscateable
		 */
		private final String key;

		/**
		 * Return group setting from the config key
		 *
		 * @param key
		 * @return
		 */
		public static Type fromKey(String key) {
			for (final Type setting : values())
				if (setting.getKey().equalsIgnoreCase(key))
					return setting;

			throw new FoException("No group setting named '" + key + "'. Available: " + Common.join(values()));
		}

		@Override
		public String toString() {
			return this.key;
		}
	}
}
