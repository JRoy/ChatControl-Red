package org.mineacademy.chatcontrol.api;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Checker;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.RuleCheck;

/**
 * The main class of the ChatControl's API.
 *
 * @author kangarko
 */
public final class ChatControlAPI {

	/**
	 * Get if the chat has globally been muted via /mute.
	 *
	 * @return if the chat has been globally muted
	 */
	public static boolean isChatMuted() {
		return ServerCache.getInstance().isMuted();
	}

	/**
	 * Get the player cache. Creates cache if it does not exist.
	 * <p>
	 * Please use with caution since we do create this cache in PlayerJoinEvent and in onEnable to
	 * handle reload.
	 *
	 * @param player the player
	 * @return
	 */
	public static PlayerCache getPlayerCache(Player player) {
		return PlayerCache.from(player);
	}

	/**
	 * Run antispam, anticaps, time and delay checks as well as rules for the given message
	 *
	 * @param sender
	 * @param message
	 * @return
	 */
	public static Checker checkMessage(Player sender, String message) {
		final Checker checker = Checker.filterChannel(sender, message, null);

		return checker;
	}

	/**
	 * Apply the given rules type for the message sent by the player
	 *
	 * @param type
	 * @param sender
	 * @param message
	 * @return
	 */
	public static RuleCheck<?> checkRules(Rule.Type type, Player sender, String message) {
		return checkRules(type, sender, message, null);
	}

	/**
	 * Apply the given rules type for the message sent by the player
	 *
	 * @param type
	 * @param sender
	 * @param message
	 * @param channel
	 * @return
	 */
	public static RuleCheck<?> checkRules(Rule.Type type, Player sender, String message, Channel channel) {
		return Rule.filter(type, sender, message, channel);
	}

	/**
	 * Return if the player is newcomer according to the Newcomer settings.
	 *
	 * @param player the player
	 * @return if ChatControl considers the player a newcomer
	 */
	public static boolean isNewcomer(Player player) {
		return Newcomer.isNewcomer(player);
	}
}
