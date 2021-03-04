package org.mineacademy.chatcontrol.model;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.model.HookManager;

/**
 * Utilities related to muting players
 */
public final class Mute {

	/**
	 * Return true if the given setting is true, the mute is enabled in settings,
	 * player does not have bypass permission and its cache or the server is muted
	 *
	 * @param setting
	 * @param player
	 * @return
	 */
	public static boolean isPartMuted(boolean setting, Player player) {
		if (!setting)
			return false;

		// Enable other plugins from muting players even if our system is disabled
		if (HookManager.isMuted(player))
			return true;

		if (!Settings.Mute.ENABLED)
			return false;

		if (PlayerUtil.hasPerm(player, Permissions.Bypass.MUTE))
			return false;

		final PlayerCache cache = PlayerCache.from(player);

		return isServerMuted() || cache.isMuted();
	}

	/**
	 * Return true if player does not have bypass permission
	 * and he, the server or the channel he writes in are muted
	 *
	 * @param player
	 * @return
	 */
	public static boolean isChatMuted(Player player) {

		// Enable other plugins from muting players even if our system is disabled
		if (HookManager.isMuted(player))
			return true;

		if (!Settings.Mute.ENABLED)
			return false;

		if (PlayerUtil.hasPerm(player, Permissions.Bypass.MUTE))
			return false;

		final PlayerCache cache = PlayerCache.from(player);

		return isServerMuted() || isPlayerOrHisChannelMuted(cache);
	}

	/**
	 * Return true if the command is disabled during mute
	 * and player or server is muted
	 *
	 * @param player
	 * @param label
	 * @return
	 */
	public static boolean isCommandMuted(Player player, String label) {

		if (!Settings.Mute.ENABLED)
			return false;

		if (PlayerUtil.hasPerm(player, Permissions.Bypass.MUTE))
			return false;

		if (!Settings.Mute.PREVENT_COMMANDS.isInList(label))
			return false;

		if (HookManager.isMuted(player))
			return true;

		final PlayerCache cache = PlayerCache.from(player);

		return isServerMuted() || cache.isMuted();
	}

	/*
	 * Return true if player is muted or he is writing into a channel that is muted
	 */
	private static boolean isPlayerOrHisChannelMuted(PlayerCache cache) {
		return cache.isMuted() || (cache.getWriteChannel() != null && cache.getWriteChannel().isMuted());
	}

	/**
	 * Return true if the server is muted
	 *
	 * @return
	 */
	public static boolean isServerMuted() {
		return ServerCache.getInstance().isMuted();
	}
}
