package org.mineacademy.chatcontrol;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;

import lombok.Getter;

/**
 * Represents a cache with data from BungeeCord
 */
@Getter
public final class SyncedCache {

	/**
	 * The internal map
	 */
	private static volatile Map<String, SyncedCache> cacheMap = new HashMap<>();

	/**
	 * The player name
	 */
	private final String playerName;

	/**
	 * The server where this player is on
	 */
	private final String serverName;

	/**
	 * The player's unique ID
	 */
	private final UUID uniqueId;

	/**
	 * His nick if any
	 */
	@Nullable
	private final String nick;

	/**
	 * Is the player vanished?
	 */
	private final boolean vanished;

	/**
	 * Is the player a fucking drunk?
	 */
	private final boolean afk;

	/**
	 * Does this plugin not give a damn about private messages?
	 */
	private final boolean ignoringPMs;

	/**
	 * List of ignored dudes
	 */
	private final Set<UUID> ignoredPlayers;

	/**
	 * Map of channel names and modes this synced man is in
	 */
	private final Map<String, Channel.Mode> channels;

	/*
	 * Create a synced cache from the given data map
	 */
	private SyncedCache(String playerName, SerializedMap data) {
		Valid.checkBoolean(!data.isEmpty(), "Cannot decompile empty data!");

		this.playerName = playerName;
		this.serverName = data.getString("Server");
		this.uniqueId = data.get("UUID", UUID.class);
		this.nick = data.getString("Nick");
		this.vanished = data.getBoolean("Vanished");
		this.afk = data.getBoolean("Afk");
		this.ignoringPMs = data.getBoolean("Ignoring_PMs");
		this.ignoredPlayers = data.getSet("Ignored_Players", UUID.class);
		this.channels = data.getMap("Channels", String.class, Channel.Mode.class);
	}

	/**
	 * Return a dude's name or nick if set
	 *
	 * @return
	 */
	public String getNameOrNickColorless() {
		return Common.stripColors(this.getNameOrNickColored());
	}

	/**
	 * Return a dude's name or nick if set
	 *
	 * @return
	 */
	public String getNameOrNickColored() {
		return Common.getOrDefaultStrict(this.nick, this.playerName);
	}

	/**
	 * Return true if this dude is ignoring the other dude's unique id
	 * Females not supported
	 *
	 * @param uniqueId
	 * @return
	 */
	public boolean isIgnoringPlayer(UUID uniqueId) {
		return this.ignoredPlayers.contains(uniqueId);
	}

	/**
	 * Return the channel mode if player is in the given channel else null
	 *
	 * @param channel
	 * @return
	 */
	@Nullable
	public Channel.Mode getChannelMode(Channel channel) {
		return this.channels.get(channel.getName());
	}

	/**
	 * Return the player or null if he is connected on another server
	 *
	 * @return
	 */
	@Nullable
	public Player toPlayer() {
		return Bukkit.getPlayerExact(this.playerName);
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SyncedCache{" + this.playerName + ",nick=" + this.nick + "}";
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return true if the given player is connected and synced on BungeeCord
	 *
	 * @param playerUUID
	 * @return
	 */
	public static boolean isPlayerConnected(UUID playerUUID) {
		synchronized (cacheMap) {
			for (final SyncedCache cache : cacheMap.values())
				if (cache.getUniqueId().equals(playerUUID))
					return true;

			return false;
		}
	}

	/**
	 * Return true should the given server name match a valid server we know of...
	 *
	 * @param serverName
	 * @return
	 */
	public static boolean doesServerExist(String serverName) {
		synchronized (cacheMap) {
			for (final SyncedCache cache : cacheMap.values())
				if (cache.getServerName().equalsIgnoreCase(serverName))
					return true;

			return false;
		}
	}

	public static SyncedCache fromUUID(UUID playerUUID) {
		synchronized (cacheMap) {
			for (final SyncedCache cache : cacheMap.values())
				if (cache.getUniqueId().equals(playerUUID))
					return cache;

			return null;
		}
	}

	/**
	 * Return the synced cache (or null) from the player name or nick
	 *
	 * @deprecated
	 * @param playerName
	 * @return
	 */
	@Deprecated
	@Nullable
	public static SyncedCache from(String nameOrNick) {
		for (final SyncedCache cache : cacheMap.values())
			if (cache.getPlayerName().equalsIgnoreCase(nameOrNick) || (cache.getNick() != null && Valid.colorlessEquals(nameOrNick, cache.getNick())))
				return cache;

		return null;
	}

	/**
	 * Return a set of all known servers on BungeeCord where players are on
	 *
	 * @return
	 */
	public static Set<String> getServers() {
		synchronized (cacheMap) {
			final Set<String> servers = new HashSet<>();

			for (final SyncedCache cache : getCaches())
				servers.add(cache.getServerName());

			return servers;
		}
	}

	/**
	 * Return all caches stored in memory
	 *
	 * @return
	 */
	public static Collection<SyncedCache> getCaches() {
		synchronized (cacheMap) {
			return cacheMap.values();
		}
	}

	/**
	 * Retrieve (or create) a sender cache
	 *
	 * @param senderName
	 * @return
	 */
	public static void upload(SerializedMap data) {
		synchronized (cacheMap) {
			cacheMap.clear();

			for (final Map.Entry<String, Object> entry : data.entrySet()) {
				final String playerName = entry.getKey();
				final SerializedMap playerData = SerializedMap.fromJson(entry.getValue().toString());

				cacheMap.put(playerName, new SyncedCache(playerName, playerData));
			}
		}
	}
}
