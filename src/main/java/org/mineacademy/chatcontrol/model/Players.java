package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.Remain;

import lombok.NonNull;
import lombok.Setter;

/**
 * Show methods related to players
 */
public final class Players {

	/**
	 * Internal flag indicating nicks are on, for best performance
	 */
	@Setter
	private static boolean nicksEnabled = false;

	/**
	 * Render the message of the day to the player
	 *
	 * @param player
	 * @param delay
	 */
	public static void showMotd(Player player, boolean delay) {

		// If player joined less than 5 seconds ago count as newcomer
		final boolean firstTime = ((System.currentTimeMillis() - player.getFirstPlayed()) / 1000) < 5;

		Common.runLater(delay ? Settings.Motd.DELAY.getTimeTicks() : 3, () -> {
			final String motd = firstTime ? Settings.Motd.FORMAT_MOTD_FIRST_TIME : Newcomer.isNewcomer(player) ? Settings.Motd.FORMAT_MOTD_NEWCOMER : Settings.Motd.FORMAT_MOTD.getFor(player);

			Format
					.parse(motd)
					.build(player, "")
					.send(player);

			Settings.Motd.SOUND.play(player);
		});
	}

	/**
	 * Broadcast the /me command to players
	 *
	 * @param senderId
	 * @param bypassReach
	 * @param component
	 */
	public static void showMe(UUID senderId, boolean bypassReach, SimpleComponent component) {
		for (final Player online : Remain.getOnlinePlayers()) {
			final PlayerCache cache = PlayerCache.from(online);

			if (Settings.Toggle.APPLY_ON.contains(Toggle.ME) && cache.isIgnoringPart(Toggle.ME) && !senderId.equals(online.getUniqueId()))
				continue;

			if (!bypassReach && cache.isIgnoringPlayer(senderId))
				continue;

			component.send(online);
		}
	}

	/**
	 * Update player tablist name with colors the player has permissions for
	 * 
	 * @param player
	 */
	public static void setTablistName(@NonNull Player player) {
		setTablistName(player, player);
	}

	/**
	 * Update player tablist name with colors the initiator has permissions for
	 * 
	 * @param initiator the sender of the command that we evaluate against what permissions to allow
	 *        in the players tab name.
	 *        
	 * @param player
	 */
	public static void setTablistName(@NonNull CommandSender initiator, @NonNull Player player) {
		if (!player.isOnline())
			return;

		if (Settings.TabList.ENABLED) {
			String tabName = Settings.TabList.FORMAT;

			tabName = Colors.addColorsForPerms(initiator, tabName, Colors.Type.NICK);
			tabName = Variables.replace(tabName, player);

			try {
				player.setPlayerListName(tabName);

			} catch (final Throwable t) {
				// MC unsupported
			}
		}

		if (Settings.Tag.APPLY_ON.contains(Tag.Type.NICK)) {
			final PlayerCache cache = PlayerCache.from(player);
			final boolean hasNick = cache.hasTag(Tag.Type.NICK);
			final String nick = cache.getTag(Tag.Type.NICK);

			if (Settings.Tag.CHANGE_DISPLAYNAME) {
				try {
					player.setDisplayName(hasNick ? nick : null);

				} catch (final Throwable t) {
					// MC unsupported
				}
			}

			if (Settings.Tag.CHANGE_CUSTOMNAME) {
				try {
					player.setCustomName(hasNick ? nick : null);
					player.setCustomNameVisible(hasNick);

				} catch (final Throwable t) {
					// MC unsupported
				}
			}

			HookManager.setNick(player.getUniqueId(), nick);
		}
	}

	/**
	 * @see #clearChat(CommandSender) for dudes
	 */
	public static void clearChatFromBungee(boolean broadcastStaffMessage) {
		clearChat(null, broadcastStaffMessage);
	}

	/**
	 * Clear all dude' windows.
	 */
	public static void clearChat(@Nullable CommandSender sender, boolean broadcastStaffMessage) {
		for (final Player online : Remain.getOnlinePlayers()) {

			if (PlayerUtil.hasPerm(online, Permissions.Bypass.CLEAR)) {
				if (broadcastStaffMessage && sender != null)
					Common.tell(online, Variables.replace(Lang.of("Commands.Clear.Success_Staff", Common.resolveSenderName(sender)), sender));
			}

			else
				for (int line = 0; line < 100; line++)
					Common.tell(online, "&r");

		}
	}

	/**
	 * Broadcast a message to all players with permission.
	 *
	 * @param permission
	 * @param component
	 */
	public static void broadcastWithPermission(String permission, SimpleComponent component) {
		for (final Player online : Remain.getOnlinePlayers())
			if (PlayerUtil.hasPerm(online, permission))
				component.send(online);
	}

	/**
	 * Retrieve a player by his name or nickname if set
	 *
	 * @param nameOrNick
	 * @return
	 */
	public static Player getPlayer(@NonNull String nameOrNick) {
		if (nicksEnabled) {
			for (final Player online : Remain.getOnlinePlayers()) {
				final String nick = getNickColorless(online);

				if (nick.equalsIgnoreCase(nameOrNick) || online.getName().equalsIgnoreCase(nameOrNick))
					return online;
			}

			return null;
		}

		return Bukkit.getPlayer(nameOrNick);
	}

	/**
	 * Return the nick or the name of the player
	 *
	 * @param player
	 * @return
	 */
	public static String getNickColorless(Player player) {
		return Common.stripColors(getNickColored(player));
	}

	/**
	 * Return the nick or the name of the player
	 *
	 * @param player
	 * @return
	 */
	public static String getNickColored(Player player) {
		final String name = player.getName();

		if (nicksEnabled)
			return Common.getOrDefaultStrict(UserMap.getInstance().getNick(name), name);

		return name;
	}

	/**
	 * Compile a list of all online players for the given receiver,
	 * returning a list of their nicknames. Vanished players
	 * are included only if receiver has bypass reach permission.
	 *
	 * @param requester
	 * @return
	 */
	public static List<String> getPlayerNames(@NonNull CommandSender requester) {
		return getPlayerNames(PlayerUtil.hasPerm(requester, Permissions.Bypass.REACH));
	}

	/**
	 * Compile a list of all online players returning a list of their nicknames.
	 * Vanished players are included only if the flag is true.
	 *
	 * @param includeVanished the flag to include vanished dudes
	 * @return
	 */
	public static List<String> getPlayerNames(boolean includeVanished) {
		final Set<String> players = new HashSet<>();

		for (final Player player : Remain.getOnlinePlayers())
			if (includeVanished || !PlayerUtil.isVanished(player)) {
				final String nick = getNickColorless(player);

				players.add(nick);
			}

		// Add players from the network
		if (BungeeCord.ENABLED)
			for (final SyncedCache cache : SyncedCache.getCaches())
				if (includeVanished || !cache.isVanished())
					players.add(cache.getNameOrNickColorless());

		// Sort and return
		final List<String> sorted = new ArrayList<>(players);
		Collections.sort(sorted);

		return sorted;
	}

	/**
	 * Remove players from the list of suggestions that match online players
	 * names or nicknames and they are vanished
	 *
	 * @param suggestions
	 */
	public static void removeVanished(List<String> suggestions) {
		for (final Iterator<String> it = suggestions.iterator(); it.hasNext();) {
			final String suggestion = it.next();
			final Player player = getPlayer(suggestion);

			if (player != null && PlayerUtil.isVanished(player))
				it.remove();
		}
	}
}
