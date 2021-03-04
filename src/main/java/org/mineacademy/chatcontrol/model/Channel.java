package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.api.ChannelJoinEvent;
import org.mineacademy.chatcontrol.api.ChannelLeaveEvent;
import org.mineacademy.chatcontrol.api.ChatChannelBungeeEvent;
import org.mineacademy.chatcontrol.api.ChatChannelEvent;
import org.mineacademy.chatcontrol.api.PlayerMentionEvent;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.ConfigItems;
import org.mineacademy.fo.model.DiscordSender;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.CompChatColor;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.YamlSectionConfig;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a chat channel
 */
@Getter
public final class Channel extends YamlSectionConfig {

	/**
	 * Stores all loaded channels
	 */
	private static final ConfigItems<Channel> loadedChannels = ConfigItems.fromFile("Channels.List", "settings.yml", Channel.class);

	/**
	 * Get the channel name
	 */
	private final String name;

	/**
	 * How the message should look like when typed to the channel?
	 */
	private String format;

	/**
	 * How the message should look like when logged to console? Null to use default, empty to not send.
	 */
	private String consoleFormat;

	/**
	 * How the message should look like when sent to Discord? Null to use default, empty to not send.
	 */
	private String discordFormat;

	/**
	 * Overrides the spy format from settings.yml
	 */
	private String spyFormat;

	/**
	 * Distance players within the world will receive the message.
	 *
	 * null = no range feature, all worlds
	 * whole number = range, radius in senders world
	 * * = range, whole world
	 */
	private String range;

	/**
	 * The linked worlds if range is set to *
	 */
	private Set<String> rangeWorlds;

	/**
	 * If the channel is muted, this is the unmute time in the future
	 * where it will no longer be muted
	 */
	private Long unmuteTime;

	/**
	 * Integration with other plugins supporting their "party" feature such as connecting this channel to Towny etc.
	 */
	private Party party;

	/**
	 * How long should players wait before typing their message into this channel?
	 */
	private SimpleTime messageDelay;

	/**
	 * Shall we send data over BungeeCord? If not set, uses the global setting from settings.yml
	 */
	private boolean bungee;

	/**
	 * Shall we connect this channel to DiscordSRV? You need to integrate it first in the Integration.Discord section
	 */
	private boolean discord;

	/**
	 * Shall we cancel chat events from this channel? Used to hide this on DynMap etc.
	 */
	private boolean cancelEvent;

	/**
	 * Create a new channel by name
	 */
	private Channel(String name) {
		super("Channels.List." + name);

		this.name = name;
		this.loadConfiguration(NO_DEFAULT, "settings.yml");
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#onLoadFinish()
	 */
	@Override
	protected void onLoadFinish() {
		this.format = getString("Format");
		this.consoleFormat = getString("Format_Console");
		this.discordFormat = getString("Format_Discord");
		this.spyFormat = getString("Format_Spy");
		this.range = isSet("Range") ? getObject("Range").toString() : null;
		this.rangeWorlds = getSet("Range_Worlds", String.class);
		this.unmuteTime = getLong("Unmute_Time");
		this.party = isSet("Party") ? Party.fromKey(getString("Party")) : null;
		this.messageDelay = getTime("Message_Delay");
		this.bungee = isSet("Bungee") ? getBoolean("Bungee") : BungeeCord.ENABLED;
		this.discord = isSet("Discord") ? getBoolean("Discord") : false;
		this.cancelEvent = isSet("Cancel_Event") ? getBoolean("Cancel_Event") : false;

		Valid.checkNotEmpty(this.format, "Format for channel '" + getName() + "' must be set!");

		if (this.range != null && !"*".equals(this.range)) {
			Valid.checkInteger(this.range, "Your channel " + this.name + " has option Range which must either be * (for entire world) or a whole number!");
			Valid.checkBoolean(this.rangeWorlds.isEmpty(), "Can only use key Range_Worlds for channel " + this.name + " when Range is set to '*' not: " + this.range);
		}
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#save()
	 */
	@Override
	public void save() {
		final SerializedMap map = new SerializedMap();

		map.put("Format", this.format);
		map.putIf("Format_Console", this.consoleFormat);
		map.putIf("Format_Discord", this.discordFormat);
		map.putIf("Format_Spy", this.spyFormat);
		map.putIf("Range", this.range);
		map.putIf("Range_Worlds", this.rangeWorlds);
		map.putIf("Unmute_Time", this.unmuteTime);
		map.putIf("Party", this.party);
		map.putIf("Message_Delay", this.messageDelay);
		map.putIf("Bungee", this.bungee);
		map.putIf("Discord", this.discord);

		for (final Map.Entry<String, Object> entry : map.entrySet())
			setNoSave(entry.getKey(), entry.getValue());

		super.save();
	}

	// ----------------------------------------------------------------------------------
	// Getters and setters
	// ----------------------------------------------------------------------------------

	/**
	 * Return true if the channel is muted
	 *
	 * @return
	 */
	public boolean isMuted() {
		return this.unmuteTime != null && this.unmuteTime > System.currentTimeMillis();
	}

	/**
	 * Set the mute for this channel
	 *
	 * @param duration how long, null to unmute
	 */
	public void setMuted(@Nullable SimpleTime duration) {
		this.unmuteTime = duration == null ? null : System.currentTimeMillis() + (duration.getTimeSeconds() * 1000);

		this.save();
	}

	// ----------------------------------------------------------------------------------
	// Methods
	// ----------------------------------------------------------------------------------

	/**
	 * Joins the player into this channel for the given mode
	 *
	 * @param player
	 * @param mode
	 *
	 * @return false if API call canceled joining
	 */
	public boolean joinPlayer(@NonNull Player player, @NonNull Mode mode) {
		final PlayerCache cache = PlayerCache.from(player);

		return this.joinPlayer(cache, mode);
	}

	/**
	 * Joins the given player cache into this channel for the given mode
	 * This potentially enables joining of offline players to channels
	 *
	 * @param cache
	 * @param mode
	 * @return false if API call canceled join
	 */
	public boolean joinPlayer(@NonNull PlayerCache cache, @NonNull Mode mode) {
		Valid.checkBoolean(!isInChannel(cache), "Player " + cache.getPlayerName() + " is already in channel: " + this.name);

		if (mode == Mode.WRITE)
			checkSingleWrite(cache);

		if (!Common.callEvent(new ChannelJoinEvent(cache, this, mode)))
			return false;

		// Do not broadcast when switching modes
		if (cache.getChannelMode(this) == null)
			broadcastMessage(Lang.of("Channels.Join.Notification").replace("{player}", cache.getPlayerName()));

		cache.updateChannelMode(this, mode);

		return true;
	}

	/*
	 * Check if player only has one write channel
	 */
	private void checkSingleWrite(PlayerCache cache) {
		String lastWriteChannel = null;

		for (final Entry<Channel, Mode> entry : cache.getChannels().entrySet()) {
			final String otherChannel = entry.getKey().getName();

			if (entry.getValue() == Mode.WRITE) {
				Valid.checkBoolean(otherChannel == null, "Found player " + cache.getPlayerName() + " in more than one write channel: " + otherChannel + " and " + lastWriteChannel);

				lastWriteChannel = otherChannel;
			}
		}
	}

	/**
	 * Kicks player from this channel
	 *
	 * @param player
	 * @return false if API call prevented leaving
	 */
	public boolean leavePlayer(Player player) {
		final PlayerCache cache = PlayerCache.from(player);

		return this.leavePlayer(cache);
	}

	/**
	 * Kicks player cache from this channel
	 * This potentially enables kicking offline players from channels
	 *
	 * @param cache
	 * @return false if API call prevented leaving
	 */
	public boolean leavePlayer(PlayerCache cache) {
		Valid.checkBoolean(isInChannel(cache), "Player " + cache.getPlayerName() + " is not in channel: " + this.name);

		final Channel.Mode mode = cache.getChannelMode(this);

		if (!Common.callEvent(new ChannelLeaveEvent(cache, this, mode)))
			return false;

		cache.updateChannelMode(this, null);
		broadcastMessage(Lang.of("Channels.Leave.Notification").replace("{player}", cache.getPlayerName()));

		return true;
	}

	/**
	 * Return true if player is in channel in any mode
	 *
	 * @param player
	 * @return
	 */
	public boolean isInChannel(@NonNull Player player) {
		return getChannelMode(player) != null;
	}

	/**
	 * Return true if player cache is in channel in any mode
	 *
	 * @param cache
	 * @return
	 */
	public boolean isInChannel(@NonNull PlayerCache cache) {
		return cache.getChannelMode(this) != null;
	}

	/**
	 * Return the channel mode for player, null if not joined
	 *
	 * @param player
	 * @return
	 */
	public Mode getChannelMode(@NonNull Player player) {
		return PlayerCache.from(player).getChannelMode(this);
	}

	/**
	 * Return a map of all players in channel for the given mode
	 *
	 * @param mode
	 * @return
	 */
	public List<Player> getOnlinePlayers(Mode mode) {
		final List<Player> players = new ArrayList<>();

		for (final Player online : Remain.getOnlinePlayers()) {
			final PlayerCache cache = PlayerCache.from(online);
			final Mode otherMode = cache.getChannelMode(this);

			if (otherMode == mode)
				players.add(online);
		}

		return players;
	}

	/**
	 * Return a map of all players in channel
	 *
	 * @return
	 */
	public Map<Player, Mode> getOnlinePlayers() {
		final Map<Player, Mode> players = new HashMap<>();

		for (final Player online : Remain.getOnlinePlayers()) {
			final PlayerCache cache = PlayerCache.from(online);
			final Mode mode = cache.getChannelMode(this);

			if (mode != null)
				players.put(online, mode);
		}

		return players;
	}

	/**
	 * Broadcast message to all channel players
	 *
	 * @param message
	 */
	public void broadcastMessage(String message) {
		this.broadcastMessage(message, null);
	}

	/**
	 * Broadcast message to all channel players
	 * replacing variables for initiator
	 *
	 * @param message
	 * @param initiator
	 */
	public void broadcastMessage(String message, @Nullable CommandSender initiator) {
		message = replaceVariables(message, initiator);

		for (final Player receiver : getOnlinePlayers().keySet())
			Common.tell(receiver, message);
	}

	/*
	 * Replace channel message variables for sender
	 */
	private String replaceVariables(String message, @Nullable CommandSender sender) {
		return Variables.replace(message.replace("{channel}", this.name), sender);
	}

	/**
	 * Send a message to the channel
	 *
	 * @param sender
	 * @param message
	 * @return
	 * @throws EventHandledException
	 */
	public Result sendMessage(CommandSender sender, String message) throws EventHandledException {

		// Measure performance
		final long now = System.currentTimeMillis();

		// Compile receivers
		// Ensure the sender receives the message even if not in channel
		final Tuple<Set<Player>, Set<Player>> tuple = compileReceivers(sender);

		final Set<Player> receivers = tuple.getKey();
		final Set<Player> hiddenReceivers = tuple.getValue();

		final boolean senderIsPlayer = sender instanceof Player;
		final UUID uniqueId = senderIsPlayer ? ((Player) sender).getUniqueId() : FoConstants.NULL_UUID;

		final PlayerCache senderCache = senderIsPlayer ? PlayerCache.from((Player) sender) : null;

		// Remove those who ignore the sender
		if (uniqueId != null) {
			if (Settings.Ignore.ENABLED && Settings.Ignore.HIDE_CHAT && !PlayerUtil.hasPerm(sender, Permissions.Bypass.REACH)) {
				final Predicate<Player> filter = recipient -> PlayerCache.from(recipient).isIgnoringPlayer(uniqueId)
						|| (Settings.Ignore.BIDIRECTIONAL && senderIsPlayer && senderCache.isIgnoringPlayer(recipient.getUniqueId()));

				receivers.removeIf(filter);
				hiddenReceivers.removeIf(filter);
			}

			// Mute
			if (senderIsPlayer && Mute.isChatMuted((Player) sender))
				throw new EventHandledException(true, Lang.of("Commands.Mute.Cannot_Chat_Player_Muted"));
		}

		// API
		final ChatChannelEvent event = new ChatChannelEvent(this, sender, message, receivers);

		if (Common.callEvent(event)) {
			sender = event.getSender();
			message = event.getMessage();

		} else
			throw new EventHandledException(true);

		// Return if muted
		if (isMuted() && !PlayerUtil.hasPerm(sender, Permissions.Bypass.MUTE))
			throw new EventHandledException(true, Lang.of("Commands.Mute.Cannot_Chat_Channel_Muted", this.name));

		// Filters
		final Checker check = Checker.filterChannel(sender, message, this);
		final boolean cancelSilently = check.isCancelledSilently();
		message = check.getMessage();

		// Warn if no visible receivers
		if (receivers.isEmpty() && this.range != null)
			Common.tellTimed(3, sender, Variables.replace(Lang.ofScript("Player.Channel_Range_Notification", SerializedMap.of("hasRange", this.range != null)), sender));

		// Add self
		if (senderIsPlayer)
			receivers.add((Player) sender);

		// Apply colors
		message = Colors.addColorsForPermsAndChat(sender, message);

		// Compile format
		final Format format = Format.parse(this.format);

		if (format == null)
			throw new EventHandledException(true, "Channel " + this.name + " is using non-existing formatting '" + this.format + "&c'. Please contact administrator.");

		// Sound notify
		final String soundNotifyMessage = Settings.SoundNotify.ENABLED ? compileSoundNotify(sender, message, cancelSilently ? new HashSet<>() : receivers) : message;

		// Inject variables
		final SerializedMap variables = SerializedMap.ofArray("channel", this.name, "message_uuid", UUID.randomUUID());

		// Build the component we send -- send the changed message from sound notify
		final SimpleComponent component = format.build(sender, soundNotifyMessage, variables);

		// Replace the hidden variables from this point, they are only needed in the component
		message = message.replace("[#flpc-i]", "").replace("[#flpc-1]", "");

		// Build log
		String consoleFormat = this.consoleFormat != null ? this.consoleFormat : Settings.Channels.FORMAT_CONSOLE;

		if ("none".equals(consoleFormat))
			Log.logOnce("channel-log-none", "Warning: Channel " + this.name + " had Format_Console set to 'none'. The only way to hide console log is to cancel the event, which may conflict with DynMap or other plugins. Be careful!");

		else if ("default".equals(consoleFormat))
			consoleFormat = component.getPlainMessage();

		else
			consoleFormat = Variables.replace(consoleFormat, sender, Common.newHashMap("channel", this.name)).replace("{message}", Common.stripColorsLetter(message));

		final CommandSender finalSender = sender;
		final String finalMessage = message;

		// Send to players or the sender himself only if silently canceled
		if (cancelSilently)
			component.sendAs(sender, Arrays.asList(sender));

		else {

			// Include hidden receivers
			receivers.addAll(hiddenReceivers);

			// Send
			component.sendAs(sender, receivers);

			// Send to spies, ignore players who already see this message
			Spy.broadcastChannel(this, sender, message, Common.convertSet(receivers, Player::getUniqueId), variables);

			// Log to file and db
			Log.logChat(sender, this, message);

			// Send to bungee
			if (this.bungee && Settings.Integration.BungeeCord.ENABLED && !(sender instanceof DiscordSender)) {
				final boolean muteBypass = PlayerUtil.hasPerm(sender, Permissions.Bypass.MUTE);
				final boolean ignoreBypass = PlayerUtil.hasPerm(sender, Permissions.Bypass.REACH);
				final boolean logBypass = PlayerUtil.hasPerm(sender, Permissions.Bypass.LOG);

				BungeeUtil.tellBungee(BungeePacket.CHANNEL,
						this.name,
						sender.getName(),
						uniqueId,
						message,
						component.serialize(),
						Common.getOrEmpty(consoleFormat),
						muteBypass,
						ignoreBypass,
						logBypass);
			}

			// Handle Discord both ways
			if (this.discord && Settings.Integration.Discord.ENABLED && HookManager.isDiscordSRVLoaded()) {
				final String json = Remain.toJson(component.getTextComponent());

				if (!(finalSender instanceof DiscordSender)) {
					final String discordMessage = Variables.replace(this.discordFormat != null ? this.discordFormat : Settings.Channels.FORMAT_DISCORD, finalSender,
							Common.newHashMap("channel", this.name)).replace("{message}", finalMessage);

					if (!discordMessage.equals("none")) {
						final String strippedMessage = Common.revertColorizing(Common.stripColors(discordMessage));

						Discord.getInstance().sendChannelMessage(sender, this.name, strippedMessage, json);
					}

				} else
					Discord.getInstance().markReceivedMessage(this.name, (DiscordSender) finalSender, json);
			}

			Debugger.debug("channel", "Sending message to " + this.name + " took " + (System.currentTimeMillis() - now) + "ms");
		}

		if (this.cancelEvent) {
			check.cancelledSilently = true;

			Common.log(consoleFormat);
		}

		return new Result(message, consoleFormat, check.isCancelledSilently());
	}

	/*
	 * Play the message and edit the message
	 */
	private String compileSoundNotify(CommandSender sender, String message, Set<Player> receivers) {

		final List<SyncedCache> syncedCaches = new ArrayList<>();

		try {
			syncedCaches.addAll(SyncedCache.getCaches());

		} catch (final ConcurrentModificationException ex) {
			// Unfortunately we just received the packet to update synced network players

			return message;
		}
		// Return if no permission
		if (!PlayerUtil.hasPerm(sender, Permissions.SOUND_NOTIFY))
			return message;

		final SenderCache senderCache = SenderCache.from(sender);
		final PlayerCache cache = sender instanceof Player ? PlayerCache.from((Player) sender) : null;
		final Channel senderChannel = cache != null ? cache.getWriteChannel() : null;

		final long cooldown = Settings.SoundNotify.COOLDOWN.getTimeSeconds();
		final long cooldownDelay = (System.currentTimeMillis() - senderCache.getLastSoundNotify()) / 1000;

		final boolean nicksEnabled = Settings.Tag.APPLY_ON.contains(Tag.Type.NICK);
		final boolean canUse = cooldown == 0 || senderCache.getLastSoundNotify() == -1 || cooldownDelay >= cooldown;
		boolean foundAtLeastOne = false;

		final String color = Common.colorize(Settings.SoundNotify.COLOR.getFor(sender));

		for (final SyncedCache syncedCache : syncedCaches) {

			// Ignore since then discontinued players
			if (!syncedCaches.contains(syncedCache))
				continue;

			final Channel.Mode receiverChannel = senderChannel != null ? syncedCache.getChannelMode(senderChannel) : null;

			// Ignore if self, afk or vanished
			if (syncedCache.getPlayerName().equals(sender.getName()) || (sender instanceof Player && syncedCache.isVanished()) || (Settings.SoundNotify.REQUIRE_AFK && !syncedCache.isAfk()))
				continue;

			// Ignore if the other player is spying or not in channel
			if (senderChannel != null && receiverChannel == null)
				continue;

			final String nick = syncedCache.getNick();
			final boolean hasNick = nicksEnabled && nick != null;

			final Pattern pattern = Pattern.compile(
					"(" + Pattern.quote(Settings.SoundNotify.REQUIRE_PREFIX + syncedCache.getPlayerName()) + "|" + Pattern.quote(Settings.SoundNotify.REQUIRE_PREFIX + (hasNick ? nick : syncedCache.getPlayerName())) + ")($| |\\.|\\,)",
					Pattern.CASE_INSENSITIVE);

			final Matcher matcher = pattern.matcher(message);

			boolean found = false;

			while (matcher.find()) {

				// Do not replace but still flag as found to give sender warning they must wait
				if (canUse)
					message = matcher.replaceAll("[#flpc-i]" + color + matcher.group(1) + "[#flpc-1]" + matcher.group(2));

				found = true;
			}

			if (found) {
				if (!canUse) {
					Common.tellLater(0, sender, Lang.of("Checker.Sound_Notify").replace("{seconds}", Lang.ofCase(cooldown - cooldownDelay, "Cases.Second")));

					return message;
				}

				// Call API and finish up
				if (Common.callEvent(new PlayerMentionEvent(syncedCache, receivers))) {

					// Send the sound over network if possible
					final Player onlineReceiver = syncedCache.toPlayer();

					if (onlineReceiver != null)
						Settings.SoundNotify.SOUND.play(onlineReceiver);
					else
						BungeeUtil.tellBungee(BungeePacket.SOUND, syncedCache.getUniqueId(), Settings.SoundNotify.SOUND.toString());

					foundAtLeastOne = true;
				}
			}
		}

		if (foundAtLeastOne && canUse)
			SenderCache.from(sender).setLastSoundNotify(System.currentTimeMillis());

		return message;
	}

	/*
	 * Return receiver list, used for BungeeCord where there is no sender instance
	 */
	private Tuple<Set<Player>, Set<Player>> compileReceivers() {
		return this.compileReceivers(null);
	}

	/*
	 * Return receiver list for player
	 */
	private Tuple<Set<Player>, Set<Player>> compileReceivers(@Nullable CommandSender sender) {

		final Set<Player> receivers = new HashSet<>();
		final Set<Player> hiddenReceivers = new HashSet<>();

		for (final Player receiver : this.getOnlinePlayers().keySet())
			if (sender instanceof Player) {
				final Player player = (Player) sender;
				final PlayerCache cache = PlayerCache.from(receiver);

				// We'll add the player later
				if (receiver.getName().equals(player.getName()))
					continue;

				if (Settings.Channels.ENABLED && Settings.Channels.IGNORE_WORLDS.contains(receiver.getWorld().getName()))
					continue;

				if (Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT) && cache.isSpyingChannel(this) && !cache.isInChannel(this.getName()))
					continue;

				if (cache.isIgnoringPart(Toggle.CHAT))
					continue;

				if (this.range != null && !isInRange(receiver, player))
					continue;

				if (this.party != null && !isInParty(receiver, player))
					continue;

				// Prevent seeing vanished players
				if (PlayerUtil.isVanished(receiver, player)) {
					hiddenReceivers.add(receiver);

					continue;
				}

				receivers.add(receiver);
			}

			else
				receivers.add(receiver);

		final Set<Player> visibleReceivers = new HashSet<>(receivers);

		// Remove vanished or spying players
		visibleReceivers.removeAll(hiddenReceivers);

		return new Tuple<>(visibleReceivers, hiddenReceivers);
	}

	/**
	 * Process and broadcast incoming bungee message
	 *
	 * @param senderName
	 * @param senderUid
	 * @param message
	 * @param component
	 * @param consoleLog
	 * @param muteBypass
	 * @param ignoreBypass
	 * @param logBypass
	 */
	public void processBungeeMessage(String senderName, UUID senderUid, String message, SimpleComponent component, String consoleLog, boolean muteBypass, boolean ignoreBypass, boolean logBypass) {

		// Compile receivers
		final Tuple<Set<Player>, Set<Player>> tuple = this.compileReceivers();

		final Set<Player> receivers = tuple.getKey();
		final Set<Player> hiddenReceivers = tuple.getValue();

		// Remove those who ignore the sender
		if (Settings.Ignore.ENABLED && Settings.Ignore.HIDE_CHAT && !ignoreBypass && !senderUid.equals(FoConstants.NULL_UUID)) {
			final SyncedCache syncedCache = SyncedCache.fromUUID(senderUid);

			final Predicate<Player> filter = recipient -> {
				final PlayerCache recipientCache = PlayerCache.from(recipient);

				return recipientCache.isIgnoringPlayer(senderUid) || recipientCache.isIgnoringPart(Toggle.CHAT)
						|| (Settings.Ignore.BIDIRECTIONAL && syncedCache != null && syncedCache.isIgnoringPlayer(recipient.getUniqueId()));
			};

			receivers.removeIf(filter);
			hiddenReceivers.removeIf(filter);
		}

		// Avoid sending doubled message to sender himself
		final Predicate<Player> filter = recipient -> recipient.getUniqueId().equals(senderUid);
		receivers.removeIf(filter);
		hiddenReceivers.removeIf(filter);

		// Mute
		if ((Mute.isServerMuted() || this.isMuted()) && !muteBypass)
			return;

		// API
		if (!Common.callEvent(new ChatChannelBungeeEvent(this, senderName, senderUid, message, receivers)))
			return;

		// Warn if no visible receivers
		if (receivers.isEmpty() && this.range != null)
			return;

		// Include hidden receivers
		receivers.addAll(hiddenReceivers);

		// Send
		component.send(receivers);

		// Log to file and db
		if (!Settings.MySQL.ENABLED && !logBypass)
			Log.logBungeeChat(senderName, this, message);

		// Log to console
		if (!consoleLog.isEmpty())
			Common.log(consoleLog);
	}

	/*
	* Return true if the given receiver is within the range of the player
	*/
	private boolean isInRange(Player receiver, Player sender) {
		Valid.checkNotNull(this.range);

		// Include all players when range is off or has perm
		if (PlayerUtil.hasPerm(sender, Permissions.Bypass.RANGE)) {
			Log.logOnce("channel-party", "Note: Player " + sender.getName() + " write to channel '" + this.name
					+ "' that has range, but because he had '" + Permissions.Bypass.RANGE + "' permission everyone will see his message.");

			return true;
		}

		final World senderWorld = sender.getWorld();
		final World receiverWorld = receiver.getWorld();

		final boolean hasBypassRangeWorld = PlayerUtil.hasPerm(sender, Permissions.Bypass.RANGE_WORLD);
		final boolean sameWorlds = senderWorld.equals(receiverWorld);

		if (sameWorlds) {
			if ("*".equals(this.range) || hasBypassRangeWorld) {

				if (hasBypassRangeWorld)
					Log.logOnce("channel-party-world", "Note: Player " + sender.getName() + " wrote to channel '" + this.name
							+ "' that has range, but because he had '" + Permissions.Bypass.RANGE_WORLD + "' permission "
							+ "everyone on his world will see his message.");

				return true;
			}

		} else {

			// Linked worlds
			if (this.rangeWorlds.contains(senderWorld.getName()) && this.rangeWorlds.contains(receiverWorld.getName()))
				return true;
		}

		return sameWorlds ? sender.getLocation().distance(receiver.getLocation()) <= Integer.parseInt(this.range) : false;
	}

	/*
	 * Return true if the given receiver is within the senders party
	 */
	private boolean isInParty(Player receiver, Player sender) {
		Valid.checkNotNull(this.party);

		switch (this.party) {
			case FACTION:
				return HookManager.getOnlineFactionPlayers(sender).contains(receiver);

			case PLOT:
				return HookManager.getPlotPlayers(sender).contains(receiver);

			case TOWN:
				return HookManager.getTownResidentsOnline(sender).contains(receiver);

			case NATION:
				return HookManager.getNationPlayersOnline(sender).contains(receiver);

			case MCMMO:
				return HookManager.getMcMMOPartyRecipients(sender).contains(receiver);

			case LAND:
				return HookManager.getLandPlayers(sender).contains(receiver);

			default:
				throw new FoException("Channel party not implemented: " + this.party);
		}
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		return obj instanceof Channel && ((Channel) obj).getName().equals(getName());
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlSectionConfig#toString()
	 */
	@Override
	public String toString() {
		return "Channel{" + this.name + "}";
	}

	// ------–------–------–------–------–------–------–------–------–------–------–------–
	// Classes
	// ------–------–------–------–------–------–------–------–------–------–------–------–

	/**
	 * Represents a sent result from the message
	 */
	@Getter
	@RequiredArgsConstructor
	public static class Result {

		/**
		 * The changed message (rules applied etc.)
		 */
		private final String message;

		/**
		 * The format from channel used that we pass into Bukkit chat event
		 */
		private final String consoleLog;

		/**
		 * Is any rule indicating that we should cancel the sending, but silently?
		 */
		private final boolean cancelledSilently;
	}

	/**
	 * Represents a party for this channel.
	 */
	@RequiredArgsConstructor
	public enum Party {

		/**
		 * This is a channel shown only to players in the same Faction as the sender.
		 */
		FACTION("factions-faction"),

		/**
		 * Chat for PlotSquared - only shown to players inside the plot.
		 */
		PLOT("plotsquared-plot"),

		/**
		 * This is a channel shown only to players in the same Town as the sender.
		 */
		TOWN("towny-town"),

		/**
		 * This is a channel shown only to players in the same Nation as the sender.
		 */
		NATION("towny-nation"),

		/**
		 * Chat for mcMMO - only shown to players within the same party.
		 */
		MCMMO("mcmmo-party"),

		/**
		 * Only chat with other players in the same land
		 */
		LAND("lands-land");

		@Getter
		private final String key;

		public static Party fromKey(String key) {
			for (final Party party : values())
				if (party.key.equalsIgnoreCase(key))
					return party;

			throw new IllegalArgumentException("No such channel party: " + key + ". Available: " + Common.join(values()));
		}

		@Override
		public String toString() {
			return this.key;
		}
	}

	/**
	 * Represents what mode the player is in the channel
	 */
	@RequiredArgsConstructor
	public enum Mode {

		/**
		 * Receive and send messages
		 */
		WRITE("write", CompChatColor.GOLD),

		/**
		 * Receive messages but not write them
		 */
		READ("read", CompChatColor.GREEN);

		/**
		 * The unobfuscated config key
		 */
		@Getter
		private final String key;

		/**
		 * The color associated with this mode
		 * Used in command channel listing
		 */
		@Getter
		private final CompChatColor color;

		/**
		 * Load the mode from the config key
		 *
		 * @param key
		 * @return
		 */
		public static Mode fromKey(String key) {
			for (final Mode mode : values())
				if (mode.key.equalsIgnoreCase(key))
					return mode;

			throw new IllegalArgumentException("No such channel mode: " + key + ". Available: " + Common.join(values(), ", ", Mode::getKey));
		}

		@Override
		public String toString() {
			return this.key;
		}
	}

	// ------–------–------–------–------–------–------–------–------–------–------–------–
	// Static
	// ------–------–------–------–------–------–------–------–------–------–------–------–

	/**
	 * Return if the given player can join at least one channel by permission
	 *
	 * @param player
	 * @return
	 */
	public static boolean canJoinAnyChannel(Player player) {
		return !getChannelsWithJoinPermission(player).isEmpty();
	}

	/**
	 * Return list of channels the player has permission to join into
	 *
	 * @param player
	 * @return
	 */
	public static List<Channel> getChannelsWithJoinPermission(Player player) {
		return collectChannels(Channel.getChannels(), channel -> {
			final String permission = Permissions.Channel.JOIN.replace("{channel}", channel.getName());

			for (final Channel.Mode mode : Channel.Mode.values())
				if (PlayerUtil.hasPerm(player, permission.replace("{mode}", mode.getKey())))
					return true;

			return false;
		});
	}

	/**
	 * Return list of channels the player has permission to leave
	 *
	 * @param player
	 * @return
	 */
	public static List<Channel> getChannelsWithLeavePermission(Player player) {
		return collectChannels(Channel.getChannels(), channel -> PlayerUtil.hasPerm(player, Permissions.Channel.LEAVE.replace("{channel}", channel.getName())));
	}

	/**
	 * Return only those channels from the given list the player can leave
	 *
	 * @param channels
	 * @param player
	 * @return
	 */
	public static List<Channel> filterChannelsPlayerCanLeave(Collection<Channel> channels, @Nullable Player player) {
		return collectChannels(channels, channel -> player == null || PlayerUtil.hasPerm(player, Permissions.Channel.LEAVE.replace("{channel}", channel.getName())));
	}

	/*
	 * Return list of all channels matching the given filter
	 */
	private static List<Channel> collectChannels(Collection<Channel> channels, Predicate<Channel> filter) {
		return channels.stream().filter(filter).collect(Collectors.toList());
	}

	/**
	 * Load all channels, typically only called when the plugin is enabled
	 */
	public static void loadChannels() {
		loadedChannels.loadItems();
	}

	/**
	 * Return true if the channel by the given name exists
	 *
	 * @param name
	 * @return
	 */
	public static boolean isChannelLoaded(final String name) {
		return loadedChannels.isItemLoaded(name);
	}

	/**
	 * Return a channel from name or null if does not exist
	 *
	 * @param name
	 * @return
	 */
	public static Channel findChannel(@NonNull final String name) {
		return loadedChannels.findItem(name);
	}

	/**
	 * Return a list of all channels
	 *
	 * @return
	 */
	public static List<Channel> getChannels() {
		return loadedChannels.getItems();
	}

	/**
	 * Return a list of all channel names
	 *
	 * @return
	 */
	public static List<String> getChannelNames() {
		return loadedChannels.getItemNames();
	}
}