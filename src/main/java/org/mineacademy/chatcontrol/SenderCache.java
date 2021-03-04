package org.mineacademy.chatcontrol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Mail;
import org.mineacademy.fo.ASCIIUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.model.LimitedQueue;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.region.Region;
import org.mineacademy.fo.visual.VisualizedRegion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Represents a cache that can work for any command sender,
 * such as those coming from Discord too.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SenderCache {

	/**
	 * The internal sender map
	 */
	private static volatile Map<String, SenderCache> cacheMap = new HashMap<>();

	/**
	 * The sender name
	 */
	@Getter
	private final String senderName;

	/**
	 * Sender's last communication
	 */
	private final Map<Log.Type, Queue<Output>> lastCommunication = new HashMap<>();

	/**
	 * Stores last packets sent, caught by ProtocolLib
	 *
	 * 100 is the maximum chat line count you can view in history
	 * This is used to delete messages
	 */
	@Getter
	private final LimitedQueue<String> lastChatPackets = new LimitedQueue<>(100);

	/**
	 * A map of books the sender has written sorted by their unique ID (used to view 'em)
	 */
	@Getter
	private final Map<UUID, Book> books = new HashMap<>();

	/**
	 * The last time the sender has joined the server or -1 if not set
	 */
	@Getter
	@Setter
	private long lastLogin = -1;

	/**
	 * The last time the sender used sound notify successfuly
	 */
	@Getter
	@Setter
	private long lastSoundNotify = -1;

	/**
	 * If sender is player - his join location
	 */
	@Setter
	private Location joinLocation;

	/**
	 * Did the sender move from his {@link #joinLocation}
	 */
	@Getter
	@Setter
	private boolean movedFromJoin;

	/**
	 * The last sign test, null if not yet set
	 */
	@Getter
	@Setter
	@Nullable
	private String[] lastSignText;

	/**
	 * The captcha that was generated for the player when they logged in
	 */
	@Getter
	@Nullable
	private String captcha;

	/**
	 * Return if the sender successfully solved the {@link #captcha}
	 */
	@Getter
	@Setter
	private boolean captchaSolved;

	/**
	 * Represents a region the player is currently creating
	 */
	@Getter
	@Setter
	private VisualizedRegion createdRegion = Region.EMPTY;

	/**
	 * Represents an unfinished mail the player writes
	 */
	@Getter
	@Setter
	private Book pendingMail;

	/**
	 * The mail this player is replying to
	 */
	@Getter
	@Setter
	private Mail pendingMailReply;

	/**
	 * Determines if we are loading cache from mysql right now
	 */
	@Getter
	@Setter
	private boolean loadingMySQL;

	/**
	 * Recent warning messages the sender has received
	 * Used to prevent duplicate warning messages
	 */
	@Getter
	private final Map<UUID, Long> recentWarningMessages = new HashMap<>();

	/**
	 * Is this sender in the middle of loading his join data?
	 */
	@Getter
	@Setter
	private boolean pendingJoin;

	/**
	 * Used for AuthMe to delay join message
	 */
	@Getter
	@Setter
	private String joinMessage;

	/**
	 * Get last reply player
	 */
	@Getter
	private Tuple<String, UUID> replyPlayer;

	/**
	 * Retrieve a list of the last X amount of outputs the sender has issued
	 *
	 * @param type
	 * @param amountInHistory
	 * @param channel
	 * @return
	 */
	public List<Output> getLastOutputs(Log.Type type, int amountInHistory, @Nullable Channel channel) {
		return filterOutputs(type, channel, amountInHistory, null);
	}

	/**
	 * Retrieve a list of all outputs issued on or after the given date
	 *
	 * @param type
	 * @param timestamp
	 * @param channel
	 * @return
	 */
	public List<Output> getOutputsAfter(Log.Type type, long timestamp, @Nullable Channel channel) {
		return filterOutputs(type, channel, -1, output -> output.getTime() >= timestamp);
	}

	/*
	 * Return a list of inputs by the given type, if the type is chat then also from the given channel,
	 * maximum of the given limit and matching the given filter
	 */
	private List<Output> filterOutputs(Log.Type type, @Nullable Channel channel, int limit, @Nullable Predicate<Output> filter) {
		final Queue<Output> allOutputs = lastCommunication.get(type);
		final List<Output> listedOutputs = new ArrayList<>();

		if (allOutputs != null) {
			final Output[] outputArray = allOutputs.toArray(new Output[allOutputs.size()]);

			// Start from the last output
			for (int i = allOutputs.size() - 1; i >= 0; i--) {
				final Output output = outputArray[i];

				// Return if channels set but not equal
				if (output == null)
					continue;

				if (output.getChannel() != null && channel != null && !output.getChannel().equals(channel.getName()))
					continue;

				if (limit != -1 && listedOutputs.size() >= limit)
					break;

				if (filter != null && !filter.test(output))
					break;

				listedOutputs.add(output);
			}
		}

		// Needed to reverse the entire list now
		Collections.reverse(listedOutputs);

		return listedOutputs;
	}

	/**
	 * Cache the given chat message from the given channel
	 *
	 * @param input
	 * @param channel
	 */
	public void cacheMessage(String input, Channel channel) {
		this.record(Log.Type.CHAT, input, channel);
	}

	/**
	 * Cache the given command
	 *
	 * @param input
	 */
	public void cacheCommand(String input) {
		this.record(Log.Type.COMMAND, input, null);
	}

	/*
	 * Internal caching handler method
	 */
	private void record(Log.Type type, String input, @Nullable Channel channel) {
		final Queue<Output> queue = lastCommunication.getOrDefault(type, new LimitedQueue<>(100));
		final Output record = new Output(System.currentTimeMillis(), input, channel == null ? null : channel.getName());

		queue.add(record);
		lastCommunication.put(type, queue);
	}

	/**
	 * Get the join location, throwing exception if not set
	 *
	 * @return the joinLocation
	 */
	public Location getJoinLocation() {
		Valid.checkBoolean(hasJoinLocation(), "Join location has not been set!");

		return this.joinLocation;
	}

	/**
	 * Return if the join location has been set
	 * @return
	 */
	public boolean hasJoinLocation() {
		return this.joinLocation != null;
	}

	/**
	 * Sends the captcha to the sender
	 *
	 * @param sender
	 */
	public void showCaptcha(CommandSender sender) {
		if (this.captcha == null)
			generateCaptcha();

		for (final String line : ASCIIUtil.generate(this.captcha, "&c#"))
			Common.tellNoPrefix(sender, line);
	}

	/**
	 * Regenerate and return a new captcha
	 * A captcha consist of four 0-9 random numbers separated by spaces
	 *
	 * @return
	 */
	public void generateCaptcha() {
		final char[] captcha = new char[] {
				generateCaptchaNumber(), ' ', generateCaptchaNumber(), ' ', generateCaptchaNumber(), ' ', generateCaptchaNumber()
		};

		this.captcha = new String(captcha);
	}

	/*
	 * Helper for generating a random 0-9 number
	 */
	private char generateCaptchaNumber() {
		return String.valueOf(RandomUtil.nextBetween(0, 9)).toCharArray()[0];
	}

	/**
	 * Remove the player for /reply if set
	 */
	public void removeReplyPlayer() {
		this.replyPlayer = null;
	}

	/**
	 * Set the player for /reply
	 *
	 * @param name
	 * @param uniqueId, can be null if console
	 */
	public void setReplyPlayer(@NonNull String name, @Nullable UUID uniqueId) {

		// Prevent changing reply if under timeout
		// This prevents others from interrupting players who are
		// chatting between each other and changing their /reply
		/*if (this.replyPlayer != null && !this.replyPlayer.isEmpty() && Settings.PrivateMessages.REPLY_CHANGE_THRESHOLD.getTimeSeconds() != 0) {
		
			if (this.lastReplyUpdate == 0)
				this.lastReplyUpdate = System.currentTimeMillis();
		
			if (System.currentTimeMillis() - this.lastReplyUpdate < Settings.PrivateMessages.REPLY_CHANGE_THRESHOLD.getTimeTicks() * 50) {
				if (this.replyPlayer.equals(replyPlayer))
					this.lastReplyUpdate = System.currentTimeMillis();
		
				return;
			}
			this.lastReplyUpdate = System.currentTimeMillis();
		}*/

		this.replyPlayer = new Tuple<>(name, uniqueId);
	}

	/**
	 * Convert the sender name to a player is online
	 *
	 * @return
	 */
	public Player toPlayer() {
		final Player player = Bukkit.getPlayerExact(this.senderName);

		return player != null && player.isOnline() ? player : null;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a given senders output
	 */
	@Getter
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class Output {

		/**
		 * The default output with -1 time and a blank message
		 */
		public static final Output NO_OUTPUT = new Output(-1, "", "");

		/**
		 * The time the message was sent
		 */
		private final long time;

		/**
		 * The message content
		 */
		private final String output;

		/**
		 * Message channel or null if not associated (such as for commands)
		 */
		@Nullable
		private final String channel;

		/**
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return time + " '" + output + "' " + channel;
		}
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return all caches stored in memory
	 *
	 * @return
	 */
	public static Collection<SenderCache> getCaches() {
		synchronized (cacheMap) {
			return cacheMap.values();
		}
	}

	/**
	 * Retrieve (or create) a sender cache
	 *
	 * @param sender
	 * @return
	 */
	public static SenderCache from(CommandSender sender) {
		return from(sender.getName());
	}

	/**
	 * Retrieve (or create) a sender cache
	 *
	 * @param senderName
	 * @return
	 */
	public static SenderCache from(String senderName) {
		synchronized (cacheMap) {
			SenderCache cache = cacheMap.get(senderName);

			if (cache == null) {
				cache = new SenderCache(senderName);

				cacheMap.put(senderName, cache);
			}

			return cache;
		}
	}
}
