package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Discord;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.RandomNoRepeatPicker;
import org.mineacademy.fo.model.Replacer;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.visual.VisualizedRegion;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Represents an operator that has require/ignore for both sender and receiver
 * Used for join/leave/kick/death messages yo
 */
@Getter
public abstract class PlayerMessage extends Operator {

	/**
	 * The type of this message
	 */
	@Getter
	private final Type type;

	/**
	 * The name of this message group
	 */
	private final String group;

	/**
	 * Permission required for the player that caused the rule to fire in
	 * order for the rule to apply
	 */
	@Nullable
	private Tuple<String, String> requireSenderPermission;

	/**
	 * Permission required for receivers of the message of the rule
	 */
	@Nullable
	private Tuple<String, String> requireReceiverPermission;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	@Nullable
	private String requireSenderScript;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	@Nullable
	private String requireReceiverScript;

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> requireSenderGamemodes = new HashSet<>();

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> requireReceiverGamemodes = new HashSet<>();

	/**
	 * World names to require
	 */
	private final Set<String> requireSenderWorlds = new HashSet<>();

	/**
	 * World names to require
	 */
	private final Set<String> requireReceiverWorlds = new HashSet<>();

	/**
	 * Region names to require
	 */
	private final Set<String> requireSenderRegions = new HashSet<>();

	/**
	 * Region names to require
	 */
	private final Set<String> requireReceiverRegions = new HashSet<>();

	/**
	 * List of channels to require matching from
	 */
	private final Set<String> requireSenderChannels = new HashSet<>();

	/**
	 * List of channels to require matching from
	 */
	private final Set<String> requireReceiverChannels = new HashSet<>();

	/**
	 * Should the message only be sent to the sending player?
	 */
	private boolean requireSelf;

	/**
	 * Permission to bypass the rule
	 */
	@Nullable
	private String ignoreSenderPermission;

	/**
	 * Permission to bypass the rule
	 */
	@Nullable
	private String ignoreReceiverPermission;

	/**
	 * The match that, if matched against a given message, will make the rule be ignored
	 */
	@Nullable
	private Pattern ignoreMatch;

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */
	@Nullable
	private String ignoreSenderScript;

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */
	@Nullable
	private String ignoreReceiverScript;

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> ignoreSenderGamemodes = new HashSet<>();

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> ignoreReceiverGamemodes = new HashSet<>();

	/**
	 * World names to ignore
	 */
	private final Set<String> ignoreSenderWorlds = new HashSet<>();

	/**
	 * World names to ignore
	 */
	private final Set<String> ignoreReceiverWorlds = new HashSet<>();

	/**
	 * Region names to ignore
	 */
	private final Set<String> ignoreSenderRegions = new HashSet<>();

	/**
	 * Region names to ignore
	 */
	private final Set<String> ignoreReceiverRegions = new HashSet<>();

	/**
	 * List of channels to ignore matching from
	 */
	private final Set<String> ignoreSenderChannels = new HashSet<>();

	/**
	 * List of channels to ignore matching from
	 */
	private final Set<String> ignoreReceiverChannels = new HashSet<>();

	/**
	 * The suffix for the {@link #messages}
	 */
	private String prefix;

	/**
	 * The suffix for the {@link #messages}
	 */
	private String suffix;

	/**
	 * Shall we also broadcast the message to the network?
	 */
	private boolean bungee;

	/**
	 * The list of messages whereof we use {@link RandomNoRepeatPicker} to pick one at the time
	 * until we run out of them to prevent random repeating
	 */
	@Getter
	private final List<String> messages = new ArrayList<>();

	/*
	 * A special flag to indicate we are about to load messages
	 */
	private boolean loadingMessages = false;

	/*
	 * Used to compute messages
	 */
	private int lastMessageIndex = 0;

	protected PlayerMessage(Type type, String group) {
		this.type = type;
		this.group = group;
	}

	/**
	 * Return the next message in a cyclic repetition
	 *
	 * @return
	 */
	public final String getNextMessage() {
		Valid.checkBoolean(!this.messages.isEmpty(), "Messages must be set on " + this);

		if (this.lastMessageIndex >= this.messages.size())
			this.lastMessageIndex = 0;

		return this.messages.get(this.lastMessageIndex++);
	}

	/**
	 * Return the prefix or the default one if not set
	 *
	 * @return the prefix
	 */
	public String getPrefix() {
		return Common.getOrDefaultStrict(this.prefix, Settings.Messages.PREFIX.get(this.type));
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getMatch()
	 */
	@Override
	public final String getUid() {
		return this.group;
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getFile()
	 */
	@Override
	public final File getFile() {
		return FileUtil.getFile("messages/" + this.type.getKey() + ".rs");
	}

	/**
	 * @see org.mineacademy.chatcontrol.operator.Operator#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(String firstThreeParams, String theRestThree, String[] args) {

		firstThreeParams = Common.joinRange(0, 3, args, " ");
		theRestThree = Common.joinRange(3, args);

		final List<String> theRestThreeSplit = splitVertically(theRestThree);

		if (this.loadingMessages) {
			final String everything = String.join(" ", args).trim();

			if (everything.startsWith("- ")) {
				String line = everything.substring(1).trim();

				if (line.startsWith("\"") || line.startsWith("'"))
					line = line.substring(1);

				if (line.endsWith("\"") || line.endsWith("'"))
					line = line.substring(0, line.length() - 1);

				this.messages.add(line);

			} else {
				Valid.checkBoolean(!this.messages.isEmpty(), "Enter messages with '-' on each line. Got: " + everything);

				// Merge the line that does not start with "-", assume it is used
				// for multiline messages:
				// - first line
				//   second line
				//   third line etc.
				final int index = this.messages.size() - 1;
				final String lastMessage = this.messages.get(index) + "\n" + everything;

				this.messages.set(index, lastMessage);
			}

			return true;
		}

		final String line = Common.joinRange(1, args);

		if ("prefix".equals(args[0])) {
			if (this.prefix != null)
				this.prefix += "\n" + line;

			else
				this.prefix = line;
		}

		else if ("suffix".equals(args[0])) {
			if (this.suffix != null)
				this.suffix += "\n" + line;

			else
				this.suffix = line;
		}

		else if ("bungee".equals(args[0])) {
			Valid.checkBoolean(!this.bungee, "Operator 'bungee' can only be used once in " + this);

			this.bungee = true;
		}

		else if ("message:".equals(args[0]) || "messages:".equals(args[0])) {
			Valid.checkBoolean(!this.loadingMessages, "Operator messages: can only be used once in " + this);

			this.loadingMessages = true;
		}

		else if ("require sender perm".equals(firstThreeParams) || "require sender permission".equals(firstThreeParams)) {
			checkNotSet(this.requireSenderPermission, "require sender perm");
			final String[] split = theRestThree.split(" ");

			this.requireSenderPermission = new Tuple<>(split[0], split.length > 1 ? Common.joinRange(1, split) : null);
		}

		else if ("require receiver perm".equals(firstThreeParams) || "require receiver permission".equals(firstThreeParams)) {
			checkNotSet(this.requireReceiverPermission, "require receiver perm");
			final String[] split = theRestThree.split(" ");

			this.requireReceiverPermission = new Tuple<>(split[0], split.length > 1 ? Common.joinRange(1, split) : null);
		}

		else if ("require sender script".equals(firstThreeParams)) {
			checkNotSet(this.requireSenderScript, "require sender script");

			this.requireSenderScript = theRestThree;
		}

		else if ("require receiver script".equals(firstThreeParams)) {
			checkNotSet(this.requireReceiverScript, "require receiver script");

			this.requireReceiverScript = theRestThree;
		}

		else if ("require sender gamemode".equals(firstThreeParams) || "require sender gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.requireSenderGamemodes.add(gameMode);
			}

		else if ("require receiver gamemode".equals(firstThreeParams) || "require receiver gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.requireReceiverGamemodes.add(gameMode);
			}

		else if ("require sender world".equals(firstThreeParams) || "require sender worlds".equals(firstThreeParams))
			this.requireSenderWorlds.addAll(theRestThreeSplit);

		else if ("require receiver world".equals(firstThreeParams) || "require receiver worlds".equals(firstThreeParams))
			this.requireReceiverWorlds.addAll(theRestThreeSplit);

		else if ("require sender region".equals(firstThreeParams) || "require sender regions".equals(firstThreeParams))
			this.requireSenderRegions.addAll(theRestThreeSplit);

		else if ("require receiver region".equals(firstThreeParams) || "require receiver regions".equals(firstThreeParams))
			this.requireReceiverRegions.addAll(theRestThreeSplit);

		else if ("require sender channel".equals(firstThreeParams) || "require sender channels".equals(firstThreeParams))
			this.requireSenderChannels.addAll(theRestThreeSplit);

		else if ("require receiver channel".equals(firstThreeParams) || "require receiver channels".equals(firstThreeParams))
			this.requireReceiverChannels.addAll(theRestThreeSplit);

		else if ("require self".equals(Common.joinRange(0, 2, args, " "))) {
			Valid.checkBoolean(!this.requireSelf, "'require self' option already set for " + this);

			this.requireSelf = true;
		}

		else if ("ignore string".equals(firstThreeParams)) {
			checkNotSet(this.ignoreMatch, "ignore receiver string");

			this.ignoreMatch = Common.compilePattern(Common.joinRange(2, args));
		}

		else if ("ignore sender perm".equals(firstThreeParams) || "ignore sender permission".equals(firstThreeParams)) {
			checkNotSet(this.ignoreSenderPermission, "ignore sender perm");

			this.ignoreSenderPermission = theRestThree;
		}

		else if ("ignore receiver perm".equals(firstThreeParams) || "ignore receiver permission".equals(firstThreeParams)) {
			checkNotSet(this.ignoreReceiverPermission, "ignore receiver perm");

			this.ignoreReceiverPermission = theRestThree;
		}

		else if ("ignore sender script".equals(firstThreeParams)) {
			checkNotSet(this.ignoreSenderScript, "ignore sender script");

			this.ignoreReceiverScript = theRestThree;
		}

		else if ("ignore sender gamemode".equals(firstThreeParams) || "ignore sender gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.ignoreSenderGamemodes.add(gameMode);
			}

		else if ("ignore receiver gamemode".equals(firstThreeParams) || "ignore receiver gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.ignoreReceiverGamemodes.add(gameMode);
			}

		else if ("ignore sender world".equals(firstThreeParams) || "ignore sender worlds".equals(firstThreeParams))
			this.ignoreSenderWorlds.addAll(theRestThreeSplit);

		else if ("ignore receiver world".equals(firstThreeParams) || "ignore receiver worlds".equals(firstThreeParams))
			this.ignoreReceiverWorlds.addAll(theRestThreeSplit);

		else if ("ignore sender region".equals(firstThreeParams) || "ignore sender regions".equals(firstThreeParams))
			this.ignoreSenderRegions.addAll(theRestThreeSplit);

		else if ("ignore receiver region".equals(firstThreeParams) || "ignore receiver regions".equals(firstThreeParams))
			this.ignoreReceiverRegions.addAll(theRestThreeSplit);

		else if ("ignore sender channel".equals(firstThreeParams) || "ignore sender channels".equals(firstThreeParams))
			this.ignoreSenderChannels.addAll(theRestThreeSplit);

		else if ("ignore receiver channel".equals(firstThreeParams) || "ignore receiver channels".equals(firstThreeParams))
			this.ignoreReceiverChannels.addAll(theRestThreeSplit);

		else
			return false;

		return true;
	}

	/**
	 * Collect all options we have to debug
	 *
	 * @return
	 */
	@Override
	protected SerializedMap collectOptions() {
		return SerializedMap.ofArray(
				"Group", this.group,
				"Prefix", this.prefix,
				"Suffix", this.suffix,
				"Bungee", this.bungee,
				"Messages", this.messages,

				"Require Sender Permission", this.requireSenderPermission,
				"Require Sender Script", this.requireSenderScript,
				"Require Sender Gamemodes", this.requireSenderGamemodes,
				"Require Sender Worlds", this.requireSenderWorlds,
				"Require Sender Regions", this.requireSenderRegions,
				"Require Sender Channels", this.requireSenderChannels,

				"Require Receiver Permission", this.requireReceiverPermission,
				"Require Receiver Script", this.requireReceiverScript,
				"Require Receiver Gamemodes", this.requireReceiverGamemodes,
				"Require Receiver Worlds", this.requireReceiverWorlds,
				"Require Receiver Regions", this.requireReceiverRegions,
				"Require Receiver Channels", this.requireReceiverChannels,

				"Require Self", this.requireSelf,
				"Ignore Match", this.ignoreMatch,

				"Ignore Sender Permission", this.ignoreSenderPermission,
				"Ignore Sender Script", this.ignoreSenderScript,
				"Ignore Sender Regions", this.ignoreSenderRegions,
				"Ignore Sender Gamemodes", this.ignoreSenderGamemodes,
				"Ignore Sender Worlds", this.ignoreSenderWorlds,
				"Ignore Sender Channels", this.ignoreSenderChannels,

				"Ignore Receiver Permission", this.ignoreReceiverPermission,
				"Ignore Receiver Regions", this.ignoreReceiverRegions,
				"Ignore Receiver Script", this.ignoreReceiverScript,
				"Ignore Receiver Gamemodes", this.ignoreReceiverGamemodes,
				"Ignore Receiver Worlds", this.ignoreReceiverWorlds,
				"Ignore Receiver Channels", this.ignoreReceiverChannels

		);
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Player Message " + super.collectOptions().put(SerializedMap.of("Type", this.type)).toStringFormatted();
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a check that is implemented by this class
	 */
	public static abstract class PlayerMessageCheck<T extends PlayerMessage> extends OperatorCheck<T> {

		/**
		 * The message type
		 */
		protected final Type type;

		/**
		 * Players who have seen at least one message (we prevent players
		 * from seeing more than one message at a time)
		 */
		private final Set<UUID> messageReceivers = new HashSet<>();

		/**
		 * The current iterated receiver
		 */
		protected Player receiver;

		/**
		 * The current receiver cache
		 */
		protected PlayerCache receiverCache;

		/**
		 * Pick one message randomly from the list to show to all players equally
		 */
		protected String pickedMessage;

		/**
		 * Has this rule been run at least once? Used to prevent firing operators
		 * for the receiver the amount of times as the online player count.
		 */
		private boolean executed;

		/**
		 * @param player
		 * @param message
		 */
		protected PlayerMessageCheck(Type type, Player player, String message) {
			super(player, message);

			this.type = type;
		}

		/**
		 * Set variables for the receiver when he is iterated and shown messages to
		 *
		 * @param receiver
		 */
		protected void setVariablesFor(@NonNull Player receiver) {
			this.receiver = receiver;
			this.receiverCache = PlayerCache.from(receiver);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#filter(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected void filter(T message) throws EventHandledException {

			Debugger.debug("operator", "FILTERING " + message.getUid());

			// Ignore
			if (message.getIgnoreMatch() != null && Common.regExMatch(message.getIgnoreMatch(), this.message)) {
				Debugger.debug("operator", "\tignore match found");

				return;
			}

			// Delay
			if (message.getDelay() != null) {
				final SimpleTime time = message.getDelay().getKey();
				final long now = System.currentTimeMillis();

				// Round the number due to Bukkit scheduler lags
				final long delay = Math.round((now - message.getLastExecuted()) / 1000D);

				// Prevent reloading spamming all messages
				if (message.getLastExecuted() == -1 && this.type == Type.TIMED) {
					Debugger.debug("operator", "\tprevented reload spam and rescheduled");
					message.setLastExecuted(now);

					return;
				}

				if (delay < time.getTimeSeconds()) {
					Debugger.debug("operator", "\tbefore delay: " + delay + " threshold: " + time.getTimeSeconds());

					return;
				}

				message.setLastExecuted(now);
			}

			boolean pickedMessage = false;

			for (final Player player : Remain.getOnlinePlayers()) {

				if (this.sender != null && message.isRequireSelf() && !this.sender.equals(player))
					continue;

				if (this.messageReceivers.contains(player.getUniqueId()) && Settings.Messages.STOP_ON_FIRST_MATCH) {
					Debugger.debug("operator", "\t" + player.getName() + " already received a message");

					continue;
				}

				this.setVariablesFor(player);
				Valid.checkNotNull(this.receiverCache, "Player cache not set");

				if (this.receiverCache.isIgnoringMessage(message) || this.receiverCache.isIgnoringMessages(message.getType())) {
					Debugger.debug("operator", "\t" + player.getName() + " s ignoring this");

					continue;
				}

				// Filter for each player
				if (!canFilter(message)) {
					Debugger.debug("operator", "\tcanFilter returned false for " + player.getName());

					continue;
				}

				// Pick the message ONLY if it can be shown to at least ONE player
				if (!pickedMessage) {
					this.pickedMessage = message.getNextMessage();

					pickedMessage = true;
				}

				// Execute main operators
				executeOperators(message);
			}
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#canFilter(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected boolean canFilter(T operator) {
			Valid.checkNotNull(receiver, "receiver in canFilter == null");

			Debugger.debug("operator", "CAN FILTER message " + operator.getUid());

			// ----------------------------------------------------------------
			// Require
			// ----------------------------------------------------------------

			if (operator.getRequireSenderPermission() != null) {
				final String permission = operator.getRequireSenderPermission().getKey();
				final String noPermissionMessage = operator.getRequireSenderPermission().getValue();

				if (!PlayerUtil.hasPerm(sender, replaceVariables(permission, operator))) {
					if (noPermissionMessage != null) {
						Common.tell(sender, replaceVariables(noPermissionMessage, operator));

						throw new EventHandledException(true);
					}

					Debugger.debug("operator", "\tno required sender permission");
					return false;
				}
			}

			if (operator.getRequireReceiverPermission() != null) {
				final String permission = operator.getRequireReceiverPermission().getKey();
				final String noPermissionMessage = operator.getRequireReceiverPermission().getValue();

				if (!PlayerUtil.hasPerm(receiver, replaceReceiverVariables(permission, operator))) {
					if (noPermissionMessage != null) {
						Common.tell(receiver, replaceReceiverVariables(noPermissionMessage, operator));

						throw new EventHandledException(true);
					}

					Debugger.debug("operator", "\tno required receiver permission");
					return false;
				}
			}

			if (operator.getRequireSenderScript() != null) {
				final Object result = JavaScriptExecutor.run(replaceVariables(operator.getRequireSenderScript(), operator), sender);

				if (result != null) {
					Valid.checkBoolean(result instanceof Boolean, "require sender script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if ((boolean) result == false) {
						Debugger.debug("operator", "\tno required sender script");

						return false;
					}
				}
			}

			if (operator.getRequireReceiverScript() != null) {
				final Object result = JavaScriptExecutor.run(replaceReceiverVariables(operator.getRequireReceiverScript(), operator), receiver);

				if (result != null) {
					Valid.checkBoolean(result instanceof Boolean, "require receiver script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if ((boolean) result == false) {
						Debugger.debug("operator", "\tno required receiver script");

						return false;
					}
				}
			}

			if (isPlayer) {
				if (!operator.getRequireSenderGamemodes().isEmpty() && !operator.getRequireSenderGamemodes().contains(player.getGameMode())) {
					Debugger.debug("operator", "\trequire sender gamemodes found");

					return false;
				}

				if (!operator.getRequireSenderWorlds().isEmpty() && !Valid.isInList(player.getWorld().getName(), operator.getRequireSenderWorlds())) {
					Debugger.debug("operator", "\tno required sender worlds");

					return false;
				}

				if (!operator.getRequireSenderRegions().isEmpty()) {
					final List<String> regions = Common.convert(ServerCache.getInstance().findRegions(player.getLocation()), VisualizedRegion::getName);
					boolean found = false;

					for (final String requireRegionName : operator.getRequireSenderRegions())
						if (regions.contains(requireRegionName)) {
							found = true;

							break;
						}

					if (!found) {
						Debugger.debug("operator", "\tno required sender regions");

						return false;
					}
				}

				if (!operator.getRequireSenderChannels().isEmpty()) {
					boolean atLeastInOne = false;

					for (final String channelName : operator.getRequireSenderChannels()) {
						if (cache.isInChannel(channelName)) {
							atLeastInOne = true;

							break;
						}
					}

					if (!atLeastInOne)
						return false;
				}
			}

			if (!operator.getRequireReceiverGamemodes().isEmpty() && !operator.getRequireReceiverGamemodes().contains(receiver.getGameMode())) {
				Debugger.debug("operator", "\trequire receiver gamemodes found");

				return false;
			}

			if (!operator.getRequireReceiverWorlds().isEmpty() && !Valid.isInList(receiver.getWorld().getName(), operator.getRequireReceiverWorlds())) {
				Debugger.debug("operator", "\tno required receiver worlds");

				return false;
			}

			if (!operator.getRequireReceiverRegions().isEmpty()) {
				final List<String> regions = Common.convert(ServerCache.getInstance().findRegions(receiver.getLocation()), VisualizedRegion::getName);
				boolean found = false;

				for (final String requireRegionName : operator.getRequireReceiverRegions())
					if (regions.contains(requireRegionName)) {
						found = true;

						break;
					}

				if (!found) {
					Debugger.debug("operator", "\tno required receiver regions");

					return false;
				}
			}

			if (!operator.getRequireReceiverChannels().isEmpty()) {
				boolean atLeastInOne = false;

				for (final String channelName : operator.getRequireReceiverChannels()) {
					if (receiverCache.isInChannel(channelName)) {
						atLeastInOne = true;

						break;
					}
				}

				if (!atLeastInOne)
					return false;
			}

			// ----------------------------------------------------------------
			// Ignore
			// ----------------------------------------------------------------

			if (operator.getIgnoreSenderPermission() != null && PlayerUtil.hasPerm(sender, replaceVariables(operator.getIgnoreSenderPermission(), operator))) {
				Debugger.debug("operator", "\tignore sender permission found");

				return false;
			}

			if (operator.getIgnoreReceiverPermission() != null && PlayerUtil.hasPerm(receiver, replaceReceiverVariables(operator.getIgnoreReceiverPermission(), operator))) {
				Debugger.debug("operator", "\tignore receiver permission found");

				return false;
			}

			if (operator.getIgnoreSenderScript() != null) {
				final Object result = JavaScriptExecutor.run(replaceVariables(operator.getIgnoreSenderScript(), operator), sender);

				if (result != null) {
					Valid.checkBoolean(result instanceof Boolean, "ignore sendre script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if ((boolean) result == true) {
						Debugger.debug("operator", "\tignore sender script found");

						return false;
					}
				}
			}

			if (operator.getIgnoreReceiverScript() != null) {
				final Object result = JavaScriptExecutor.run(replaceReceiverVariables(operator.getIgnoreReceiverScript(), operator), receiver);

				if (result != null) {
					Valid.checkBoolean(result instanceof Boolean, "ignore receiver script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if ((boolean) result == true) {
						Debugger.debug("operator", "\tignore receiver script found");

						return false;
					}
				}
			}

			if (isPlayer) {
				if (operator.getIgnoreSenderGamemodes().contains(player.getGameMode())) {
					Debugger.debug("operator", "\tignore sender gamemodes found");

					return false;
				}

				if (operator.getIgnoreSenderWorlds().contains(player.getWorld().getName())) {
					Debugger.debug("operator", "\tignore sender worlds found");

					return false;
				}

				for (final String playersRegion : Common.convert(ServerCache.getInstance().findRegions(player.getLocation()), VisualizedRegion::getName))
					if (operator.getIgnoreSenderRegions().contains(playersRegion)) {
						Debugger.debug("operator", "\tignore sender regions found");

						return false;
					}

				for (final String channelName : operator.getIgnoreSenderChannels())
					if (cache.isInChannel(channelName))
						return false;
			}

			if (operator.getIgnoreReceiverGamemodes().contains(receiver.getGameMode())) {
				Debugger.debug("operator", "\tignore receiver gamemodes found");

				return false;
			}

			if (operator.getIgnoreReceiverWorlds().contains(receiver.getWorld().getName())) {
				Debugger.debug("operator", "\tignore receiver worlds found");

				return false;
			}

			for (final String playersRegion : Common.convert(ServerCache.getInstance().findRegions(receiver.getLocation()), VisualizedRegion::getName))
				if (operator.getIgnoreReceiverRegions().contains(playersRegion)) {
					Debugger.debug("operator", "\tignore receiver regions found");

					return false;
				}

			for (final String channelName : operator.getIgnoreReceiverChannels())
				if (receiverCache.isInChannel(channelName))
					return false;

			return super.canFilter(operator);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#executeOperators(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected void executeOperators(T operator) throws EventHandledException {

			// Use the same message for all players
			final String message = this.pickedMessage;

			if (!message.isEmpty() && !"none".equals(message)) {
				String prefix = operator.getPrefix();
				String json;
				String plainMessage;

				// Send message as JSON
				if ("[JSON]".equals(prefix) || message.startsWith("[JSON]")) {
					final String toSend = replaceVariables(message.startsWith("[JSON]") ? message : prefix + message, operator);

					// Prepare message we send to bungee
					json = toSend.replace("[JSON]", "").trim();

					// Send whatever part starts with JSON
					Common.tellNoPrefix(receiver, toSend);

					plainMessage = TextComponent.toLegacyText(Remain.toComponent(json));
				}

				// Send as interactive format otherwise
				else {
					final String colorlessMessage = Common.stripColors(message);

					// Support interactive chat
					if (ChatUtil.isInteractive(colorlessMessage)) {
						Common.tell(receiver, message);

						// Remove the first <> prefix
						plainMessage = message.replaceFirst("<[a-zA-Z]+>", "");
						json = null;

					} else {

						// Add the main part and add prefix for all lines
						final Format format = Format.isFormatLoaded(message) ? Format.findFormat(message) : Format.parse("{message}");

						// Construct
						prefix = prefix != null ? prefix + (prefix.endsWith(" ") ? "" : " ") : "";
						String replaced = replaceVariables(prefix + message + Common.getOrEmpty(operator.getSuffix()), operator);

						// Support centering
						final String[] replacedLines = replaced.split("\n");

						for (int i = 0; i < replacedLines.length; i++) {
							final String line = replacedLines[i];

							if (Common.stripColors(line).startsWith("<center>"))
								replacedLines[i] = ChatUtil.center(line.replace("<center>", "").trim());
						}

						replaced = String.join("\n", replacedLines);

						// Build again
						final SimpleComponent component = format.build(getMessagePlayerForVariables(), replaced);

						// Send
						component.send(receiver);

						// Prepare message we send to bungee
						final TextComponent textComponent = component.getTextComponent();

						json = Remain.toJson(textComponent);
						plainMessage = textComponent.toLegacyText();
					}
				}

				// Send to Bungee and Discord
				if (!this.executed) {
					if (BungeeCord.ENABLED && operator.isBungee()) {
						if (json != null)
							BungeeUtil.tellBungee(BungeePacket.JSON_BROADCAST, json);
						else
							BungeeUtil.tellBungee(BungeePacket.PLAIN_BROADCAST, message);
					}

					if (HookManager.isDiscordSRVLoaded()) {
						final String discordChannel = Settings.Messages.DISCORD.get(this.type);

						if (discordChannel != null)
							Discord.getInstance().sendChannelMessageNoPlayer(discordChannel, Common.stripColors(plainMessage));
					}
				}
			}

			// Register as received message
			this.messageReceivers.add(receiver.getUniqueId());

			if (!this.executed)
				super.executeOperators(operator);

			// Mark as executed, starting the first receiver
			this.executed = true;
		}

		protected CommandSender getMessagePlayerForVariables() {
			return this.receiver;
		}

		/*
		 * Replace all kinds of check variables
		 */
		private final String replaceReceiverVariables(@Nullable String message, T operator) {
			if (message == null)
				return null;

			return Variables.replace(Replacer.replaceVariables(message, prepareVariables(operator)), receiver);
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#prepareVariables(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected SerializedMap prepareVariables(T operator) {
			return super.prepareVariables(operator).putArray("broadcast_group", operator.getGroup());
		}
	}

	/**
	 * Represents a message type
	 */
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Join messages
		 */
		JOIN("join") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Toggle.Type_Join");
			}
		},

		/**
		 * Leave messages
		 */
		QUIT("quit") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Toggle.Type_Quit");
			}
		},

		/**
		 * Kick messages
		 */
		KICK("kick") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Toggle.Type_Kick");
			}
		},

		/**
		 * Death messages
		 */
		DEATH("death") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Toggle.Type_Death");
			}
		},

		/**
		 * Timed messages
		 */
		TIMED("timed") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Toggle.Type_Timed");
			}
		};

		/**
		 * The saveable non-obfuscated key
		 */
		@Getter
		private final String key;

		/**
		 * Yummy dummy localized key from messages_aliens.yml
		 *
		 * @return
		 */
		public abstract String getLocalized();

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
