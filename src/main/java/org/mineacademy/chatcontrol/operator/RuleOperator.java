package org.mineacademy.chatcontrol.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.api.RuleReplaceEvent;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.visual.VisualizedRegion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class RuleOperator extends Operator {

	/**
	 * Replace the matching expression in the message with the optional replacement
	 */
	private final Map<Pattern, String> beforeReplace = new HashMap<>();

	/**
	 * Permission required for the rule to apply,
	 * message sent to player if he lacks it.
	 */
	@Nullable
	private Tuple<String, String> requirePermission;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	@Nullable
	private String requireScript;

	/**
	 * Gamemodes to require
	 */
	private final Set<GameMode> requireGamemodes = new HashSet<>();

	/**
	 * World names to require
	 */
	private final Set<String> requireWorlds = new HashSet<>();

	/**
	 * Region names to require
	 */
	private final Set<String> requireRegions = new HashSet<>();

	/**
	 * Channels and their modes to require
	 */
	private final Map<String, String> requireChannels = new HashMap<>();

	/**
	 * Permission to bypass the rule
	 */
	@Nullable
	private String ignorePermission;

	/**
	 * The matches that, if one matched, will make the rule be ignored
	 */
	private final Set<Pattern> ignoreMatches = new HashSet<>();

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */
	@Nullable
	private String ignoreScript;

	/**
	 * A list of command labels including / that will be ignored.
	 */
	private final Set<String> ignoreCommands = new HashSet<>();

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> ignoreGamemodes = new HashSet<>();

	/**
	 * World names to ignore
	 */
	private final Set<String> ignoreWorlds = new HashSet<>();

	/**
	 * Region names to ignore
	 */
	private final Set<String> ignoreRegions = new HashSet<>();

	/**
	 * List of channels and their modes to ignore matching from
	 */
	private final Map<String, String> ignoreChannels = new HashMap<>();

	/**
	 * List of strings to randomly select to replace the matching part of the message to
	 */
	private final Set<String> replacements = new HashSet<>();

	/**
	 * List of strings to randomly select to completely rewrite the whole message to
	 */
	private final Set<String> rewrites = new HashSet<>();

	/**
	 * List of strings blahblahblah but for each world differently
	 */
	private final Map<String, Set<String>> worldRewrites = new HashMap<>();

	/**
	 * @see org.mineacademy.chatcontrol.operator.Operator#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(String param, String theRest, String[] args) {

		final List<String> theRestSplit = splitVertically(theRest);

		if ("before replace".equals(param)) {
			final String[] split = theRest.split(" with ");
			final Pattern regex = Common.compilePattern(split[0]);
			final String replacement = split.length > 1 ? split[1] : "";

			this.beforeReplace.put(regex, replacement);
		}

		else if ("require perm".equals(param) || "require permission".equals(param)) {
			checkNotSet(this.requirePermission, "require perm");
			final String[] split = theRest.split(" ");

			this.requirePermission = new Tuple<>(split[0], split.length > 1 ? Common.joinRange(1, split) : null);
		}

		else if ("require script".equals(param)) {
			checkNotSet(this.requireScript, "require script");

			this.requireScript = theRest;
		}

		else if ("require gamemode".equals(param) || "require gamemodes".equals(param)) {
			for (final String modeName : theRestSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.requireGamemodes.add(gameMode);
			}
		}

		else if ("require world".equals(param) || "require worlds".equals(param))
			this.requireWorlds.addAll(theRestSplit);

		else if ("require region".equals(param) || "require regions".equals(param))
			this.requireRegions.addAll(theRestSplit);

		else if ("require channel".equals(param) || "require channels".equals(param)) {
			for (final String channelAndMode : theRestSplit) {
				final String[] split = channelAndMode.split(" ");

				final String channelName = split[0];
				final String mode = split.length == 2 ? split[1] : "";

				this.requireChannels.put(channelName, mode);
			}
		}

		else if ("ignore perm".equals(param) || "ignore permission".equals(param)) {
			checkNotSet(this.ignorePermission, "ignore perm");

			this.ignorePermission = theRest;
		}

		else if ("ignore string".equals(param)) {
			final Pattern pattern = Common.compilePattern(theRest);

			Valid.checkBoolean(!this.ignoreMatches.contains(pattern), "'ignore string' already contains: " + theRest + " for: " + this);
			this.ignoreMatches.add(pattern);
		}

		else if ("ignore script".equals(param)) {
			checkNotSet(this.ignoreScript, "ignore script");

			this.ignoreScript = theRest;
		}

		else if ("ignore command".equals(param) || "ignore commands".equals(param)) {
			for (final String label : theRestSplit)
				this.ignoreCommands.add(label);
		}

		else if ("ignore gamemode".equals(param) || "ignore gamemodes".equals(param)) {
			for (final String modeName : theRestSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.ignoreGamemodes.add(gameMode);
			}
		}

		else if ("ignore world".equals(param) || "ignore worlds".equals(param)) {
			this.ignoreWorlds.addAll(theRestSplit);
		}

		else if ("ignore region".equals(param) || "ignore regions".equals(param)) {
			this.ignoreRegions.addAll(theRestSplit);
		}

		else if ("ignore channel".equals(param) || "ignore channels".equals(param)) {
			for (final String channelAndMode : theRestSplit) {
				final String[] split = channelAndMode.split(" ");

				final String channelName = split[0];
				final String mode = split.length == 2 ? split[1] : "";

				this.ignoreChannels.put(channelName, mode);
			}
		}

		else if ("then replace".equals(param)) {
			this.replacements.addAll(theRestSplit);
		}

		else if ("then rewrite".equals(param)) {
			this.rewrites.addAll(theRestSplit);
		}

		else if ("then rewritein".equals(param)) {
			final String[] split = theRest.split(" ");
			Valid.checkBoolean(split.length > 1, "wrong then rewritein syntax! Usage: <world> <message>");

			final String world = split[0];
			final List<String> messages = splitVertically(Common.joinRange(1, split));

			this.worldRewrites.put(world, new HashSet<>(messages));

		} else
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
		return super.collectOptions().put(SerializedMap.ofArray(
				"Before Replace", this.beforeReplace,
				"Require Permission", this.requirePermission,
				"Require Script", this.requireScript,
				"Require Gamemodes", this.requireGamemodes,
				"Require Worlds", this.requireWorlds,
				"Require Regions", this.requireRegions,
				"Require Channels", this.requireChannels,
				"Ignore Permission", this.ignorePermission,
				"Ignore Matches", this.ignoreMatches,
				"Ignore Script", this.ignoreScript,
				"Ignore Commands", this.ignoreCommands,
				"Ignore Gamemodes", this.ignoreGamemodes,
				"Ignore Worlds", this.ignoreWorlds,
				"Ignore Regions", this.ignoreRegions,
				"Ignore Channels", this.ignoreChannels,
				"Replacements", this.replacements,
				"Rewrites", this.rewrites,
				"World Rewrites", this.worldRewrites

		));
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a check that is implemented by this class
	 */
	public abstract static class RuleOperatorCheck<T extends RuleOperator> extends OperatorCheck<RuleOperator> {

		/**
		 * Channel wherefrom the checked message is sent, null if N/A
		 */
		@Nullable
		private final Channel channel;

		/**
		 * @param sender
		 * @param message
		 */
		protected RuleOperatorCheck(CommandSender sender, String message, @Nullable Channel channel) {
			super(sender, message);

			this.channel = channel;
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#canFilter(org.bukkit.command.CommandSender, java.lang.String, org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected boolean canFilter(RuleOperator operator) {

			// ----------------------------------------------------------------
			// Require
			// ----------------------------------------------------------------

			if (operator.getRequirePermission() != null) {
				final String permission = operator.getRequirePermission().getKey();
				final String noPermissionMessage = operator.getRequirePermission().getValue();

				if (!PlayerUtil.hasPerm(sender, replaceVariables(permission, operator))) {
					if (noPermissionMessage != null) {
						Common.tell(sender, replaceVariables(noPermissionMessage, operator).replace("{permission}", permission));

						throw new EventHandledException(true);
					}

					return false;
				}
			}

			if (operator.getRequireScript() != null) {
				final Object result = JavaScriptExecutor.run(replaceVariables(operator.getRequireScript(), operator), sender);

				if (result != null) {
					Valid.checkBoolean(result instanceof Boolean, "require script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if ((boolean) result == false)
						return false;
				}
			}

			if (isPlayer) {
				if (!operator.getRequireGamemodes().isEmpty() && !operator.getRequireGamemodes().contains(player.getGameMode()))
					return false;

				if (!operator.getRequireWorlds().isEmpty() && !Valid.isInList(player.getWorld().getName(), operator.getRequireWorlds()))
					return false;

				if (!operator.getRequireRegions().isEmpty()) {
					final List<String> regions = Common.convert(ServerCache.getInstance().findRegions(player.getLocation()), VisualizedRegion::getName);
					boolean found = false;

					for (final String requireRegionName : operator.getRequireRegions())
						if (regions.contains(requireRegionName)) {
							found = true;

							break;
						}

					if (!found)
						return false;
				}

				if (channel != null && !operator.getRequireChannels().isEmpty()) {
					boolean foundChannel = false;

					for (final Entry<String, String> entry : operator.getRequireChannels().entrySet()) {
						final String channelName = entry.getKey();
						final String channelMode = entry.getValue();

						if (channel.getName().equalsIgnoreCase(channelName) && cache.isInChannel(channelName)
								&& (channelMode.isEmpty() || channelMode.equalsIgnoreCase(cache.getChannelMode(channelName).getKey()))) {
							foundChannel = true;

							break;
						}
					}

					if (!foundChannel)
						return false;
				}
			}

			// ----------------------------------------------------------------
			// Ignore
			// ----------------------------------------------------------------

			if (operator.getIgnorePermission() != null && PlayerUtil.hasPerm(sender, replaceVariables(operator.getIgnorePermission(), operator)))
				return false;

			for (final Pattern ignoreMatch : operator.getIgnoreMatches())
				if (Common.regExMatch(ignoreMatch, message))
					return false;

			if (operator.getIgnoreScript() != null) {
				final Object result = JavaScriptExecutor.run(replaceVariables(operator.getIgnoreScript(), operator), sender);

				if (result != null) {
					Valid.checkBoolean(result instanceof Boolean, "ignore script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if ((boolean) result == true)
						return false;
				}
			}

			if (isPlayer) {
				if (operator.getIgnoreGamemodes().contains(player.getGameMode()))
					return false;

				if (operator.getIgnoreWorlds().contains(player.getWorld().getName()))
					return false;

				for (final String playersRegion : Common.convert(ServerCache.getInstance().findRegions(player.getLocation()), VisualizedRegion::getName))
					if (operator.getIgnoreRegions().contains(playersRegion))
						return false;

				if (channel != null) {
					for (final Entry<String, String> entry : operator.getIgnoreChannels().entrySet()) {
						final String channelName = entry.getKey();
						final String channelMode = entry.getValue();

						if (channel.getName().equalsIgnoreCase(channelName) && cache.isInChannel(channelName)
								&& (channelMode.isEmpty() || channelMode.equalsIgnoreCase(cache.getChannelMode(channelName).getKey())))
							return false;
					}
				}
			}

			return super.canFilter(operator);
		}

		/**
		 * Run given operators for the given message and return the updated message
		 */
		protected void executeOperators(RuleOperator operator, @Nullable Matcher matcher) throws EventHandledException {

			// Delay
			if (operator.getDelay() != null) {
				final SimpleTime time = operator.getDelay().getKey();
				final String message = operator.getDelay().getValue();

				final long now = System.currentTimeMillis();

				// Round the number due to Bukkit scheduler lags
				final long delay = Math.round((now - operator.getLastExecuted()) / 1000D);

				if (delay < time.getTimeSeconds()) {
					Debugger.debug("operator", "\tbefore delay: " + delay + " threshold: " + time.getTimeSeconds());

					cancel(message == null ? null : replaceVariables(message.replace("{delay}", (time.getTimeSeconds() - delay) + ""), operator));
				}

				operator.setLastExecuted(now);
			}

			final LinkedHashMap<Tuple<Integer, Integer>, String> replacements = new LinkedHashMap<>();

			if (matcher != null) {
				if (!operator.getReplacements().isEmpty()) {
					int count = 0;
					int end = -1;

					// Update the message with the matcher's content, this fixes inconsistencies if matcher removes colors
					message = ReflectionUtil.getFieldContent(Matcher.class, "text", matcher).toString();

					do {

						try {
							matcher.start();

							end = matcher.end();
						} catch (final IllegalStateException ex) {
							throw new FoException("Your rule contained 'then replace' but the word has already been replaced! If the rule has a group, check that the group is not replacing the word. Rule: " + operator);
						}

						String replacement = RandomUtil.nextItem(operator.getReplacements());

						// Automatically prolong one-letter replacements
						if (replacement.startsWith("@prolong "))
							replacement = Common.duplicate(replacement.replace("@prolong ", ""), matcher.group().length());

						// Fix greedy filters eating up ending space
						final boolean endsWithSpace = matcher.group().charAt(matcher.group().length() - 1) == ' ';
						final String replacedMatch = replaceVariables(replacement, operator) + (endsWithSpace ? " " : "");

						// Call API
						final RuleReplaceEvent event = new RuleReplaceEvent(sender, message, replacedMatch, operator, false);

						if (!Common.callEvent(event))
							continue;

						// Store indexes
						replacements.put(new Tuple<>(matcher.start(), matcher.end()), event.getReplacedMatch());

						// Stop after 20 matches to prevent infinite loop
					} while (end < message.length() && matcher.find(end) && count++ < 20);
				}

				// Not sure about a better way to do this... must store indexes from the end to start then replace one by one from the match above
				final List<Map.Entry<Tuple<Integer, Integer>, String>> entries = new ArrayList<>(replacements.entrySet());
				Collections.reverse(entries);

				for (final Map.Entry<Tuple<Integer, Integer>, String> entry : entries) {
					final Tuple<Integer, Integer> index = entry.getKey();

					message = message.substring(0, index.getKey()) + entry.getValue() + message.substring(index.getValue(), message.length());
				}

				if (!operator.getRewrites().isEmpty()) {
					final String replacedMatch = replaceVariables(RandomUtil.nextItem(operator.getRewrites()), operator);

					// Call API
					final RuleReplaceEvent event = new RuleReplaceEvent(sender, message, replacedMatch, operator, false);

					if (Common.callEvent(event))
						message = event.getReplacedMatch();
				}
			}

			if (isPlayer)
				if (operator.getWorldRewrites().containsKey(player.getWorld().getName())) {
					final String replacedMatch = RandomUtil.nextItem(operator.getWorldRewrites().get(player.getWorld().getName()));

					// Call API
					final RuleReplaceEvent event = new RuleReplaceEvent(sender, message, replacedMatch, operator, false);

					if (Common.callEvent(event))
						message = event.getReplacedMatch();
				}

			super.executeOperators(operator);
		}
	}
}
