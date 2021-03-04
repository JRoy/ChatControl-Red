package org.mineacademy.chatcontrol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Channel.Mode;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Database;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.Toggle;
import org.mineacademy.chatcontrol.model.UserMap;
import org.mineacademy.chatcontrol.model.UserMap.Record;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.MySQL;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictList;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.remain.CompChatColor;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.YamlSectionConfig;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a cache saved to data.db for living players
 */
@Getter
public final class PlayerCache extends YamlSectionConfig {

	/**
	 * The player cache
	 */
	private static volatile Map<UUID, PlayerCache> cacheMap = new HashMap<>();

	/**
	 * The players unique id
	 */
	private final UUID uniqueId;

	/**
	 * The player name
	 */
	private final String playerName;

	/**
	 * The chat color
	 */
	private CompChatColor chatColor;

	/**
	 * The chat decoration
	 */
	private CompChatColor chatDecoration;

	/**
	 * Set of channels the player has left by command to prevent
	 * autojoining again
	 */
	private Set<String> leftChannels = new HashSet<>();

	/**
	 * Set of players this player is ignoring
	 */
	private Set<UUID> ignoredPlayers = new HashSet<>();

	/**
	 * What parts of the plugin has player opted out from receiving? Such as mails etc.
	 */
	private Set<Toggle> ignoredParts = new HashSet<>();

	/**
	 * Set of timed message broadcast groups this player is not receiving
	 */
	private Map<PlayerMessage.Type, Set<String>> ignoredMessages = new HashMap<>();

	/**
	 * The player's tags
	 */
	private Map<Tag.Type, String> tags = new HashMap<>();

	/**
	 * Represents list of set names with their warn points
	 */
	private Map<String, Integer> warnPoints = new HashMap<>();

	/**
	 * Player channel list with channel name-mode pairs
	 */
	private Map<String, Channel.Mode> channels = new HashMap<>();

	/**
	 * Data stored from rules
	 */
	private Map<String, Object> ruleData = new HashMap<>();

	/**
	 * If the player is muted, this is the unmute time in the future
	 * where he will no longer be muted
	 */
	private Long unmuteTime;

	/**
	 * The parts of the plugin the player is spying
	 */
	private Set<Spy.Type> spyingSectors = new HashSet<>();

	/**
	 * The channels the player is spying
	 */
	private Set<String> spyingChannels = new HashSet<>();

	/**
	 * Represents the email that is being sent to receiver
	 * when the sender has autoresponder on
	 */
	private Tuple<Book, Long> autoResponder;

	/**
	 * If conversation mode is enabled this holds the player the
	 * sender is conversing with, otherwise null as bull
	 */
	private Tuple<String, UUID> conversingPlayer;

	/**
	 * Indicates when the cache was last manipulated with
	 */
	private long lastActive;

	/**
	 * Do we allow this cache to be saved? Defaults to true
	 * and is NOT saved to file so it reverts to true every time
	 * cache is re/loaded
	 */
	@Getter
	@Setter // Store only locally
	private boolean allowSave = true;

	/**
	 * The time when, if this sender is a player, his reply got updated
	 */
	// Stored only locally
	private long lastReplyUpdate;

	/*
	 * Create a new player cache (see at the bottom)
	 */
	private PlayerCache(String name, UUID uniqueId) {
		super("Players." + uniqueId.toString());

		this.playerName = name;
		this.uniqueId = uniqueId;

		this.loadConfiguration(NO_DEFAULT, FoConstants.File.DATA);
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#onLoadFinish()
	 */
	@Override
	protected void onLoadFinish() {
		this.loadFromData(getMap(""));
	}

	/**
	 * Check if player has not exceeded any cache limits and disable appropriately
	 *
	 * @param player
	 */
	public void checkLimits(Player player) {

		// Check if player still has permissions for their custom color/decoration
		// removing them if not
		if (hasChatColor() && !PlayerUtil.hasPerm(player, Colors.getGuiColorPermission(player, this.chatColor))) {
			Log.logTip("TIP Alert: Removing chat color due to lost permission");

			setChatColor(null);
		}

		if (hasChatDecoration() && !PlayerUtil.hasPerm(player, Colors.getGuiColorPermission(player, this.chatDecoration))) {
			Log.logTip("TIP Alert: Removing chat decoration due to lost permission");

			setChatDecoration(null);
		}

		// Check max write/read channel limits and remove if over them
		int readChannels = 0;
		int writeChannels = 0;
		boolean save = false;

		final int maxReadChannels = Settings.Channels.MAX_READ_CHANNELS.getFor(player);

		for (final Iterator<Entry<String, Mode>> it = this.channels.entrySet().iterator(); it.hasNext();) {
			final Entry<String, Mode> entry = it.next();
			final String otherChannel = entry.getKey();
			final Mode otherMode = entry.getValue();

			final Channel channelInstance = Channel.findChannel(otherChannel);

			// Skip channels not installed
			if (channelInstance == null)
				continue;

			final String joinPermission = Permissions.Channel.JOIN.replace("{channel}", otherChannel).replace("{mode}", otherMode.getKey());

			if (!PlayerUtil.hasPerm(player, joinPermission)) {
				Log.logTip("TIP Alert: Removing channel " + otherChannel + " in mode " + otherMode + " due to not having " + joinPermission + " permission");

				it.remove();
				save = true;

				continue;
			}

			if (otherMode == Mode.WRITE && ++writeChannels > 1) {
				Log.logTip("TIP Alert: Removing channel " + otherChannel + " in mode " + otherMode + " due to having another channel on write already (hard limit is 1)");

				it.remove();
				save = true;

				continue;
			}

			if (otherMode == Mode.READ && ++readChannels > maxReadChannels) {
				Log.logTip("TIP Alert: Removing channel " + otherChannel + " in mode " + otherMode + " due to having another channels on read already (player-specific limit is " + maxReadChannels + ")");

				it.remove();
				save = true;
			}

			Log.logTip("TIP: Joining player to his saved channel " + otherChannel + " in mode " + otherMode);
		}

		if (save)
			save();
	}

	/**
	 * Return this class as a map
	 *
	 * @return
	 */
	public SerializedMap serialize() {
		final SerializedMap map = new SerializedMap();

		map.putIf("Chat_Color", this.chatColor);
		map.putIf("Chat_Decoration", this.chatDecoration);
		map.putIf("Left_Channels", this.leftChannels);
		map.putIf("Ignored_Players", this.ignoredPlayers);
		map.putIf("Ignored_Parts", this.ignoredParts);
		map.putIf("Ignored_Messages", this.ignoredMessages);
		map.putIf("Tags", this.tags);
		map.putIf("Warn_Points", this.warnPoints);
		map.putIf("Channels", this.channels);
		map.putIf("Rule_Data", this.ruleData);
		map.putIf("Unmute_Time", this.unmuteTime);
		map.putIf("Spying_Sectors", this.spyingSectors);
		map.putIf("Spying_Channels", this.spyingChannels);
		map.putIf("Auto_Responder", this.autoResponder);

		// Store these separatedly for now
		if (this.conversingPlayer != null) {
			map.putIf("Conversing_Player", this.conversingPlayer.getKey());
			map.putIf("Conversing_Player_UUID", this.conversingPlayer.getValue());
		}

		// Add the last active flag only when some of the keys above is actually added
		if (!Valid.isNullOrEmptyValues(map))
			map.put("Last_Active", System.currentTimeMillis());

		return map;
	}

	/*
	 * Load this cache data from the given map
	 */
	private void loadFromData(SerializedMap map) {
		this.chatColor = map.get("Chat_Color", CompChatColor.class);
		this.chatDecoration = map.get("Chat_Decoration", CompChatColor.class);
		this.leftChannels = map.getSet("Left_Channels", String.class);
		this.ignoredPlayers = map.getSet("Ignored_Players", UUID.class);
		this.ignoredParts = map.getSet("Ignored_Parts", Toggle.class);
		this.ignoredMessages = map.getMapSet("Ignored_Messages", PlayerMessage.Type.class, String.class);
		this.tags = map.getMap("Tags", Tag.Type.class, String.class);
		this.warnPoints = map.getMap("Warn_Points", String.class, Integer.class);
		this.channels = map.getMap("Channels", String.class, Channel.Mode.class);
		this.ruleData = map.getMap("Rule_Data", String.class, Object.class);
		this.unmuteTime = map.getLong("Unmute_Time");
		this.spyingSectors = map.getSet("Spying_Sectors", Spy.Type.class);
		this.spyingChannels = map.getSet("Spying_Channels", String.class);
		this.autoResponder = map.containsKey("Auto_Responder") ? Tuple.deserialize(map.getMap("Auto_Responder"), Book.class, Long.class) : null;

		if (map.containsKey("Conversing_Player") && map.containsKey("Conversing_Player_UUID"))
			this.conversingPlayer = new Tuple<>(map.getString("Conversing_Player"), UUID.fromString(map.getString("Conversing_Player_UUID")));

		this.lastActive = map.getLong("Last_Active", -1L);
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#save()
	 */
	@Override
	public void save() {
		if (!this.allowSave)
			return;

		if (Settings.MySQL.ENABLED)
			Common.runAsync(() -> Database.getInstance().saveCache(this));
		else
			writeToDisk();
	}

	/**
	 * Force save the data we have to data.db
	 */
	public void writeToDisk() {
		final SerializedMap map = serialize();

		if (!map.isEmpty() && !"{}".equals(map.toString())) { // We serialize null values too but they are not saved
			for (final Map.Entry<String, Object> entry : map.entrySet())
				setNoSave(entry.getKey(), entry.getValue());

			super.save();
		}
	}

	/* ------------------------------------------------------------------------------- */
	/* Methods */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return if the chat color has been set
	 *
	 * @return
	 */
	public boolean hasChatColor() {
		return this.chatColor != null;
	}

	/**
	 * Return if the chat decoration has been set
	 *
	 * @return
	 */
	public boolean hasChatDecoration() {
		return this.chatDecoration != null;
	}

	/**
	 * Set a new chat color
	 *
	 * @param chatColor
	 */
	public void setChatColor(@Nullable CompChatColor chatColor) {
		this.chatColor = chatColor;

		save();
	}

	/**
	 * Set a new chat decoration
	 *
	 * @param chatDecoration
	 */
	public void setChatDecoration(@Nullable CompChatColor chatDecoration) {
		this.chatDecoration = chatDecoration;

		save();
	}

	/**
	 * Return true if the given unique ID is being ignored
	 *
	 * @param uniqueId
	 * @return
	 */
	public boolean isIgnoringPlayer(UUID uniqueId) {
		return Settings.Ignore.ENABLED && this.ignoredPlayers.contains(uniqueId);
	}

	/**
	 * Set the given player to be ignored or not
	 *
	 * @param uniqueId
	 * @param ignored
	 */
	public void setIgnoredPlayer(UUID uniqueId, boolean ignored) {
		if (ignored)
			this.ignoredPlayers.add(uniqueId);
		else
			this.ignoredPlayers.remove(uniqueId);

		save();
	}

	/**
	 * Return true if the given part is being ignored
	 *
	 * @param toggle
	 * @return
	 */
	public boolean isIgnoringPart(Toggle toggle) {
		return this.ignoredParts.contains(toggle);
	}

	/**
	 * Set the given toggle to be ignored or not
	 *
	 * @param toggle
	 * @param ignored
	 */
	public void setIgnoredPart(Toggle toggle, boolean ignored) {
		if (ignored)
			this.ignoredParts.add(toggle);
		else
			this.ignoredParts.remove(toggle);

		save();
	}

	/**
	 * Did the player use /ch leave command to leave the channel?
	 *
	 * @param channel
	 * @return
	 */
	public boolean hasLeftChannel(Channel channel) {
		return this.leftChannels.contains(channel.getName());
	}

	/**
	 * Mark the given channel as left
	 *
	 * @param channel
	 */
	public void markLeftChannel(Channel channel) {
		this.leftChannels.add(channel.getName());

		save();
	}

	/**
	 * Return if this player is ignoring the given broadcast all messages
	 *
	 * @param broadcast
	 * @return
	 */
	public boolean isIgnoringMessages(PlayerMessage.Type type) {
		final Set<String> messages = this.ignoredMessages.getOrDefault(type, new HashSet<>());

		return messages.contains("*");
	}

	/**
	 * Return if this player is ignoring the given broadcast from the given type
	 *
	 * @param broadcast
	 * @return
	 */
	public boolean isIgnoringMessage(PlayerMessage message) {
		final Set<String> messages = this.ignoredMessages.getOrDefault(message.getType(), new HashSet<>());

		return messages.contains(message.getGroup());
	}

	/**
	 * Sets the given broadcast as ignored or not
	 *
	 * @param broadcast
	 * @param ignoring
	 */
	public void setIgnoringMessage(PlayerMessage message, boolean ignoring) {
		final PlayerMessage.Type type = message.getType();
		final Set<String> messages = this.ignoredMessages.getOrDefault(type, new HashSet<>());

		if (ignoring)
			messages.add(message.getGroup());
		else
			messages.remove(message.getGroup());

		if (messages.isEmpty())
			this.ignoredMessages.remove(type);
		else
			this.ignoredMessages.put(type, messages);

		save();
	}

	/**
	 * Sets the all broadcasts of this type are ignored or not
	 *
	 * @param broadcast
	 * @param ignoring
	 */
	public void setIgnoringMessages(PlayerMessage.Type type, boolean ignoring) {
		this.ignoredMessages.remove(type);

		if (ignoring)
			this.ignoredMessages.put(type, Common.newSet("*"));

		save();
	}

	/**
	 * Return the nick without colors, or null if not set
	 *
	 * @return
	 */
	public String getTagColorless(Tag.Type type) {
		final String tag = Common.stripColors(getTag(type));

		return tag != null && !tag.isEmpty() ? tag : null;
	}

	/**
	 * Return true if the given player tag is set
	 *
	 * @param type
	 * @return
	 */
	public boolean hasTag(Tag.Type type) {
		final String tag = getTag(type);

		return tag != null && !tag.isEmpty();
	}

	/**
	 * Return a tag or null if not set
	 *
	 * @param type
	 * @return
	 */
	public String getTag(Tag.Type type) {
		return type == Tag.Type.NICK ? UserMap.getInstance().getNick(this.playerName) : this.tags.get(type);
	}

	/**
	 * Return a read-only map of tags
	 *
	 * @return
	 */
	public Map<Tag.Type, String> getTags() {
		final Map<Tag.Type, String> copy = new HashMap<>();

		// We have to pull in nick from other source
		for (final Tag.Type type : Tag.Type.values()) {
			final String tag = getTag(type);

			if (tag != null && !tag.isEmpty())
				copy.put(type, tag);
		}

		return Collections.unmodifiableMap(copy);
	}

	/**
	 * Set a tag for a player or remove it if tag is null
	 *
	 * @param type
	 * @param tag
	 */
	public void setTag(Tag.Type type, @Nullable String tag) {
		Valid.checkBoolean(tag == null || !tag.trim().isEmpty(), "Cannot save an empty tag, to remove it, set it to null");

		// Use usermap for nicks
		if (type == Tag.Type.NICK) {

			// Save to db
			UserMap.getInstance().save(this.playerName, this.uniqueId, tag);

			// Hook into other plugins
			HookManager.setNick(this.uniqueId, tag);

		} else {
			if (tag != null)
				this.tags.put(type, tag);
			else
				this.tags.remove(type);

			save();
		}
	}

	/**
	 * Return a set's warning points or 0
	 *
	 * @param warnSet
	 * @return
	 */
	public int getWarnPoints(String warnSet) {
		return this.warnPoints.getOrDefault(warnSet, 0);
	}

	/**
	 * Set warning points of a set to the given amount
	 *
	 * @param warnSet
	 * @param points
	 */
	public void setWarnPointsNoSave(String warnSet, int points) {
		if (points == 0)
			this.warnPoints.remove(warnSet);

		else
			this.warnPoints.put(warnSet, points);

		setNoSave("Warn_Points", this.warnPoints.isEmpty() ? null : this.warnPoints);
	}

	/**
	 * Return immutable map of all stored warning sets with their points
	 *
	 * @return
	 */
	public Map<String, Integer> getWarnPoints() {
		return Collections.unmodifiableMap(new HashMap<>(this.warnPoints));
	}

	/**
	 * Return true if the player is in a channel
	 *
	 * @param channelName
	 * @return
	 */
	public boolean isInChannel(String channelName) {
		return this.channels.containsKey(channelName.toLowerCase());
	}

	/**
	 * Return write channel for player or null
	 * Players may only write to one channel at a time
	 *
	 * @return
	 */
	public Channel getWriteChannel() {
		final List<Channel> list = getChannels(Mode.WRITE);

		if (!list.isEmpty()) {

			// Force kick from multiple write channels
			if (list.size() > 1) {
				final Player player = toPlayer();

				if (player != null)
					checkLimits(player);
			}

			return list.get(0);
		}

		return null;
	}

	/**
	 * Return all channels for the given mode
	 *
	 * @param mode
	 * @return
	 */
	public List<Channel> getChannels(Channel.Mode mode) {
		final StrictList<Channel> channels = new StrictList<>();

		for (final Entry<Channel, Channel.Mode> entry : getChannels().entrySet())
			if (entry.getValue() == mode)
				channels.add(entry.getKey());

		return channels.getSource();
	}

	/**
	 * Return all channels with their modes in
	 *
	 * @return
	 */
	public Map<Channel, Channel.Mode> getChannels() {
		final StrictMap<Channel, Channel.Mode> map = new StrictMap<>();

		for (final Entry<String, Channel.Mode> entry : this.channels.entrySet()) {
			final Channel channel = Channel.findChannel(entry.getKey());

			if (channel != null)
				map.put(channel, entry.getValue());
		}

		return Collections.unmodifiableMap(map.getSource());
	}

	/**
	 * Return the mode for a channel or null if not joined
	 *
	 * @param channel
	 * @return
	 */
	public Channel.Mode getChannelMode(Channel channel) {
		return this.channels.get(channel.getName().toLowerCase());
	}

	/**
	 * Return the mode for a channel or null if not joined
	 *
	 * @param channelName
	 * @return
	 */
	public Channel.Mode getChannelMode(String channelName) {
		return this.channels.get(channelName.toLowerCase());
	}

	/**
	 * Update data.db with new information about channel and its mode
	 *
	 * Internal use only! Use {@link Channel} methods as API means
	 *
	 * @param channel
	 * @param mode
	 */
	public void updateChannelMode(Channel channel, @Nullable Channel.Mode mode) {
		final String channelName = channel.getName().toLowerCase();

		if (mode == null)
			this.channels.remove(channelName);

		else
			this.channels.put(channelName, mode);

		save();
	}

	/**
	 * Return true if player has rule data
	 *
	 * @param key
	 * @return
	 */
	public boolean hasRuleData(String key) {
		return this.ruleData.containsKey(key);
	}

	/**
	 * Get rule data
	 *
	 * @param key
	 * @return
	 */
	public Object getRuleData(String key) {
		return this.ruleData.get(key);
	}

	/**
	 * Save the given rule data pair
	 *
	 * @param key
	 * @param object
	 */
	public void setRuleData(String key, @Nullable Object object) {

		if (object == null || object.toString().trim().equals("") || object.toString().equalsIgnoreCase("null"))
			this.ruleData.remove(key);

		else
			this.ruleData.put(key, object);

		save();
	}

	/**
	 * Return true if the player is muted
	 *
	 * @return
	 */
	public boolean isMuted() {
		return this.unmuteTime != null && this.unmuteTime > System.currentTimeMillis();
	}

	/**
	 * Set the mute for this player
	 *
	 * @param duration how long, null to unmute
	 */
	public void setMuted(@Nullable SimpleTime duration) {
		this.unmuteTime = duration == null ? null : System.currentTimeMillis() + (duration.getTimeSeconds() * 1000);

		save();
	}

	/**
	 * Return if the player is spying something
	 *
	 * @return
	 */
	public boolean isSpyingSomething() {
		return !this.spyingChannels.isEmpty() || !this.spyingSectors.isEmpty();
	}

	/**
	 * Return if the player is spying something that is in the Apply_On
	 * list in settings.yml
	 *
	 * @return
	 */
	public boolean isSpyingSomethingEnabled() {
		return (Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT) && !this.spyingChannels.isEmpty())
				|| !this.spyingSectors.stream().filter(sector -> Settings.Spy.APPLY_ON.contains(sector)).collect(Collectors.toList()).isEmpty();
	}

	/**
	 * Disable all spying
	 */
	public void setSpyingOff() {
		this.spyingChannels.clear();
		this.spyingSectors.clear();

		save();
	}

	/**
	 * Enable all spying
	 */
	public void setSpyingOn() {
		for (final Spy.Type type : Spy.Type.values())
			if (Settings.Spy.APPLY_ON.contains(type))
				this.spyingSectors.add(type);

		for (final Channel channel : Channel.getChannels())
			this.spyingChannels.add(channel.getName());

		save();
	}

	/**
	 * Return if the player is spying the given sector
	 *
	 * @param type
	 */
	public boolean isSpying(Spy.Type type) {
		Valid.checkBoolean(type != Spy.Type.CHAT, "When checking for spying channels use isSpyingChannel instead!");

		return this.spyingSectors.contains(type);
	}

	/**
	 * Update the player's spying mode
	 *
	 * @param type what game sector to spy
	 * @param spying true or false
	 */
	public void setSpying(Spy.Type type, boolean spying) {
		Valid.checkBoolean(type != Spy.Type.CHAT, "When setting spying channels use setSpyingChannel instead!");

		if (spying)
			this.spyingSectors.add(type);
		else
			this.spyingSectors.remove(type);

		save();
	}

	/**
	 * Return if the player is spying the given channel
	 *
	 * @param type
	 */
	public boolean isSpyingChannel(Channel channel) {
		return this.spyingChannels.contains(channel.getName());
	}

	/**
	 * Return if the player is spying the given channel
	 *
	 * @param channelName
	 * @return
	 */
	public boolean isSpyingChannel(String channelName) {
		return this.spyingChannels.contains(channelName);
	}

	/**
	 * Update the player's spying mode
	 *
	 * @param channel what channel to spy
	 * @param spying true or false
	 */
	public void setSpyingChannel(Channel channel, boolean spying) {

		if (spying) {
			this.spyingSectors.add(Spy.Type.CHAT);
			this.spyingChannels.add(channel.getName());

		} else {
			this.spyingSectors.remove(Spy.Type.CHAT);
			this.spyingChannels.remove(channel.getName());
		}

		save();
	}

	/**
	 * Return if player has autoresponder and its expiration date is valid
	 *
	 * @return
	 */
	public boolean isAutoResponderValid() {
		return this.hasAutoResponder() && System.currentTimeMillis() < this.autoResponder.getValue();
	}

	/**
	 * Return if any (even expired) auto responder is set
	 *
	 * @return
	 */
	public boolean hasAutoResponder() {
		return this.autoResponder != null;
	}

	/**
	 * Updates an autoresponder's date if {@link #hasAutoResponder()} is true
	 *
	 * @param futureExpirationDate
	 */
	public void setAutoResponderDate(long futureExpirationDate) {
		Valid.checkBoolean(this.hasAutoResponder(), "Cannot update auto responder date if none is set");

		this.setAutoResponder(this.autoResponder.getKey(), futureExpirationDate);
	}

	/**
	 * Set the book autoresponder
	 *
	 * @param book
	 * @param futureExpirationDate
	 */
	public void setAutoResponder(Book book, long futureExpirationDate) {
		this.autoResponder = new Tuple<>(book, futureExpirationDate);

		save();
	}

	/**
	 * Remove autoresponder or throw error if does not exist
	 */
	public void removeAutoResponder() {
		Valid.checkBoolean(this.hasAutoResponder(), "Cannot remove an auto responder player does not have");

		this.autoResponder = null;
		save();
	}

	/**
	 * Set the player this player is conversing with, automatically
	 *
	 * @param conversingPlayer the conversingPlayer to set
	 */
	public void setConversingPlayer(@Nullable String conversingPlayer, @Nullable UUID uniqueId) {
		if (conversingPlayer == null)
			this.conversingPlayer = null;
		else
			this.conversingPlayer = new Tuple<>(conversingPlayer, uniqueId);

		save();
	}

	/**
	 * Return player from cache if online or null otherwise
	 *
	 * @return
	 */
	@Nullable
	public Player toPlayer() {
		final Player player = Remain.getPlayerByUUID(this.uniqueId);

		return player != null && player.isOnline() ? player : null;
	}

	/**
	 * Remove cache record from memory if it exists
	 *
	 * @param uniqueId
	 */
	public void removeFromMemory() {
		synchronized (cacheMap) {
			cacheMap.remove(this.uniqueId);
		}
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlSectionConfig#toString()
	 */
	@Override
	public String toString() {
		return "PlayerCache{" + this.playerName + "}";
	}

	/* ------------------------------------------------------------------------------- */
	/* Staticus Belavaros - Just kidding, no idea what that fucking means */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return or create new player cache for the given player
	 *
	 * @param player
	 * @return
	 */
	public static PlayerCache from(Player player) {
		synchronized (cacheMap) {
			final UUID uniqueId = player.getUniqueId();
			final String playerName = player.getName();

			PlayerCache cache = cacheMap.get(uniqueId);

			if (cache == null) {
				cache = new PlayerCache(playerName, uniqueId);

				// Add the player to our map
				UserMap.getInstance().save(cache);

				cacheMap.put(uniqueId, cache);
			}

			return cache;
		}
	}

	/**
	 * Attempts to get a player cache from name or nick, from data.db or database
	 * Due to blocking call we handle stuff in a synced callback
	 *
	 * @param nameOrNick
	 * @param syncCallback
	 */
	public static void poll(String nameOrNick, Consumer<PlayerCache> syncCallback) {
		Valid.checkSync("Polling cache must be called sync!");

		Common.runAsync(() -> {
			UserMap.Record localRecord = UserMap.getInstance().getRecord(nameOrNick);
			SerializedMap remoteData = null;

			if (MySQL.ENABLED && localRecord == null) {
				final Tuple<UserMap.Record, SerializedMap> data = Database.getInstance().getData(nameOrNick);

				// May as well be we pulled some Mr Captaign Does Not Exists out there
				if (data != null) {
					final UserMap.Record remoteRecord = data.getKey();

					remoteData = data != null ? data.getValue() : null;

					// User is not stored, give up
					if (remoteRecord == null && localRecord == null) {
						Common.runLater(() -> syncCallback.accept(null));

						return;
					}

					// Odd case
					if (remoteRecord == null && localRecord != null) {
						Common.log("&cWarning: Local record found for " + localRecord.getName() + " but the one on database was not stored, updating..");

						Database.getInstance().addUserToMap(localRecord.getName(), localRecord.getUniqueId(), localRecord.getNick());
					}

					if (localRecord == null) {
						UserMap.getInstance().cacheLocally(remoteRecord);

						localRecord = remoteRecord;
					}
				}
			}

			// User is not stored, give up
			if (localRecord == null) {
				Common.runLater(() -> syncCallback.accept(null));

				return;
			}

			final String name = localRecord.getName();
			final UUID uniqueId = localRecord.getUniqueId();
			final SerializedMap finalData = remoteData;

			Common.runLater(() -> {
				PlayerCache cache = cacheMap.get(uniqueId);

				if (cache == null) {
					cache = new PlayerCache(name, uniqueId);

					if (finalData != null)
						cache.loadFromData(finalData);

					cacheMap.put(uniqueId, cache);
				}

				syncCallback.accept(cache);
			});
		});
	}

	/**
	 * Attempts to get a player cache from name or nick, from data.db or database
	 * Due to blocking call we handle stuff in a synced callback
	 *
	 * @param nameOrNick
	 * @param syncCallback
	 */
	public static void pollAll(Consumer<List<PlayerCache>> syncCallback) {
		Valid.checkSync("Polling cache must be called sync!");

		final List<PlayerCache> caches = new ArrayList<>();

		if (MySQL.ENABLED) {
			Common.runAsync(() -> {
				final Map<Record, SerializedMap> datas = Database.getInstance().getDatas();

				Common.runLater(() -> {
					for (final Map.Entry<Record, SerializedMap> entry : datas.entrySet()) {
						final Record record = entry.getKey();
						final SerializedMap data = entry.getValue();

						if (!data.isEmpty() && !"{}".equals(data.serialize().toString()))
							caches.add(loadOrUpdateCache(record.getName(), record.getUniqueId(), data));
					}

					Collections.sort(caches, (first, second) -> first.getPlayerName().compareTo(second.getPlayerName()));

					syncCallback.accept(caches);
				});
			});
		}

		else {
			Common.runAsync(() -> {

				for (final Record record : UserMap.getInstance().getLocalData()) {
					final UUID uuid = record.getUniqueId();
					final Player player = Remain.getPlayerByUUID(uuid);

					if (player != null)
						caches.add(from(player));

					else {
						final String name = record.getName();

						caches.add(new PlayerCache(name, uuid));
					}
				}

				Collections.sort(caches, (first, second) -> first.getPlayerName().compareTo(second.getPlayerName()));
				Common.runLater(() -> syncCallback.accept(caches));
			});
		}
	}

	/**
	 * Load or update the given player cache with the given data,
	 * the cache is then put into the map but it is not saved.
	 *
	 * Used from MySQL etc.
	 *
	 * @param playerName
	 * @param uniqueId
	 * @param data
	 * @return
	 */
	public static PlayerCache loadOrUpdateCache(String playerName, UUID uniqueId, SerializedMap data) {
		synchronized (cacheMap) {
			PlayerCache cache = cacheMap.get(uniqueId);

			if (cache == null) {
				cache = new PlayerCache(playerName, uniqueId);

				cacheMap.put(uniqueId, cache);
			}

			cache.loadFromData(data);

			return cache;
		}
	}

	/**
	 * Clear das cache map
	 */
	public static void clear() {
		synchronized (cacheMap) {
			cacheMap.clear();
		}
	}
}
