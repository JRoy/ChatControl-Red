package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.mineacademy.chatcontrol.api.PreRuleMatchEvent;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.EventHandledException;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a simple chat rule
 */
@Getter
public class Rule extends RuleOperator {

	/**
	 * How kind the rule is
	 *
	 * (Rules are typically very kind)
	 */
	private final Type type;

	/**
	 * The match of the rule
	 */
	private final Pattern pattern;

	/**
	 * List of events this rule does not apply to
	 */
	private final Set<Rule.Type> ignoreTypes = new HashSet<>();

	/**
	 * The name of the rule, empty if not set
	 */
	private String name = "";

	/**
	 * Apply rules from the given group name
	 */
	@Nullable
	private String group;

	/**
	 * Create a new rule of the given type and match operator
	 *
	 * @param type
	 * @param match the regex after the match
	 */
	public Rule(Rule.Type type, String match) {
		this.type = type;
		this.pattern = Common.compilePattern(match);
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getFile()
	 */
	@Override
	public File getFile() {
		return FileUtil.getFile("rules/" + this.type.getKey() + ".rs");
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getMatch()
	 */
	@Override
	public String getUid() {
		return this.pattern.pattern();
	}

	public String getMatch() {
		return this.pattern.pattern();
	}

	/**
	 * @see org.mineacademy.chatcontrol.operator.Operator#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(String param, String theRest, String[] args) {

		if ("name".equals(args[0]))
			this.name = Common.joinRange(1, args);

		else if ("group".equals(args[0])) {
			checkNotSet(this.group, "group");

			this.group = Common.joinRange(1, args);
		}

		else if ("ignore event".equals(param) || "ignore events".equals(param) || "ignore type".equals(param) || "ignore types".equals(param)) {
			Valid.checkBoolean(this.type == Type.GLOBAL, "You can only use 'ignore type' for global rules not " + this);

			for (final String typeKey : theRest.split("\\|")) {
				final Rule.Type type = Rule.Type.fromKey(typeKey);

				this.ignoreTypes.add(type);
			}
		} else
			return super.onParse(param, theRest, args);

		return true;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Rule " + super.collectOptions().put(SerializedMap.ofArray(
				"Name", this.name,
				"Type", this.type,
				"Match", this.pattern.pattern(),
				"Ignore Types", this.ignoreTypes,
				"Group", this.group)).toStringFormatted();
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Start the rule check for the given rule type, sender and his message
	 *
	 * @param type
	 * @param sender
	 * @param message
	 * @param channel can be null
	 *
	 * @return
	 */
	public static RuleCheck<Rule> filter(Rule.Type type, CommandSender sender, String message, @Nullable Channel channel) {
		final RuleCheck<Rule> check = new RuleCheck<>(type, sender, message, channel);

		check.start();
		return check;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	public static class RuleCheck<T extends Rule> extends RuleOperatorCheck<T> {

		/**
		 * The rule type that started the check
		 */
		private final Rule.Type type;

		/**
		 * The at-present evaluated rule
		 */
		private RuleOperator ruleOrGroupEvaluated;

		/**
		 * The at-present rule that the current group is calling
		 */
		private RuleOperator ruleForGroup;

		/**
		 * The at-present matcher
		 */
		private Matcher matcher;

		/**
		 * @param sender
		 * @param message
		 */
		protected RuleCheck(Rule.Type type, @NonNull CommandSender sender, @NonNull String message, @Nullable Channel channel) {
			super(sender, message, channel);

			this.type = type;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<RuleOperator> getOperators() {
			Valid.checkNotNull(type, "Type not set!");

			if (!Settings.Rules.APPLY_ON.contains(type))
				return new ArrayList<>();

			// Get all rules and make a copy
			final List<RuleOperator> rules = new ArrayList<>(Rules.getInstance().getRules(type));

			// Import other rules to check
			for (final Rule.Type toImport : Rules.getInstance().getImports().getOrDefault(type, new ArrayList<>()))
				rules.addAll(0, Rules.getInstance().getRules(toImport));

			return rules;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#filter(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected void filter(RuleOperator rule) throws EventHandledException {

			// Set this to use later in variables
			this.ruleOrGroupEvaluated = rule;

			// Reset for new rule
			this.ruleForGroup = null;

			final Rule ruleEvaluated = (Rule) rule;

			if (ruleEvaluated.getIgnoreTypes().contains(type))
				return;

			final String originalMessage = new String(message);
			String messageMatched = new String(message);

			// Prepare the message before checking
			for (final Entry<Pattern, String> entry : rule.getBeforeReplace().entrySet())
				messageMatched = Common.compileMatcher(entry.getKey(), messageMatched).replaceAll(entry.getValue());

			final Matcher matcher = Common.compileMatcher(ruleEvaluated.getPattern(), messageMatched);

			if (matcher.find()) {

				this.matcher = matcher;

				// Only continue if we match a rule that we can filter.
				if (!canFilter(rule))
					return;

				// API
				final PreRuleMatchEvent event = new PreRuleMatchEvent(sender, message, ruleEvaluated);
				if (!Common.callEvent(event))
					return;

				// Update message from the API
				message = event.getMessage();

				// Find group early
				final Group group = ruleEvaluated.getGroup() != null ? Groups.getInstance().findGroup(ruleEvaluated.getGroup()) : null;
				final String identifier = Common.getOrDefault(ruleEvaluated.getName(), group != null ? group.getGroup() : "");

				// Verbose
				if (!rule.isIgnoreVerbose())
					verbose("&f*--------- Rule match (" + ruleEvaluated.getType().getKey() + (identifier.isEmpty() ? "" : "/" + identifier) + ") for " + sender.getName() + " --------- ",
							"&fMATCH&b: &r" + ruleEvaluated.getMatch(),
							"&fCATCH&b: &r" + message);

				// Execute main operators
				executeOperators(rule, matcher);

				// Execute group operators
				if (group != null) {
					Valid.checkNotNull(group, "Rule referenced to non-existing group '" + group.getGroup() + "'! Rule: " + rule);

					this.ruleForGroup = rule;

					if (canFilter(group))
						executeOperators(group, matcher);
				}

				if (!rule.isIgnoreVerbose() && !originalMessage.equals(message))
					verbose("&fUPDATE&b: &r" + message);

				if (!rule.isIgnoreVerbose() && this.cancelledSilently)
					verbose("&cOriginal message cancelled silently.");

				// Move abort to the bottom to let full rule and handler execute
				if (group != null && group.isAbort())
					throw new OperatorAbortException();
			}
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#canFilter(org.bukkit.command.CommandSender, java.lang.String, org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected boolean canFilter(RuleOperator operator) {

			// Set this to use later in variables
			this.ruleOrGroupEvaluated = operator;

			if (type == Rule.Type.COMMAND && operator.getIgnoreCommands().contains(message.split(" ")[0].toLowerCase()))
				return false;

			if (type == Rule.Type.CHAT && isPlayer && cache.getWriteChannel() != null) {
				if (operator.getIgnoreChannels().containsKey(cache.getWriteChannel().getName()))
					return false;

				if (!operator.getRequireChannels().isEmpty() && !operator.getRequireChannels().containsKey(cache.getWriteChannel().getName()))
					return false;
			}

			return super.canFilter(operator);
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#executeOperators(org.mineacademy.chatcontrol.operator.Operator, java.util.regex.Matcher)
		 */
		@Override
		protected void executeOperators(RuleOperator operator, Matcher matcher) throws EventHandledException {

			if (!operator.isIgnoreLogging())
				Log.logRule(type, sender, operator, message);

			super.executeOperators(operator, matcher);
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#prepareVariables(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected SerializedMap prepareVariables(RuleOperator operator) {
			final SerializedMap map = super.prepareVariables(operator);

			if (this.ruleOrGroupEvaluated instanceof Rule) {
				final Rule rule = (Rule) this.ruleOrGroupEvaluated;

				map.put("ruleID", Common.getOrDefaultStrict(rule.getName(), "")); // backward compatibile
				map.put("rule_name", Common.getOrDefaultStrict(rule.getName(), ""));
				map.put("rule_group", Common.getOrDefault(rule.getGroup(), ""));
				map.put("rule_match", rule.getMatch());
			}

			if (this.ruleOrGroupEvaluated instanceof Group) {
				final Group group = (Group) this.ruleOrGroupEvaluated;

				map.put("group_name", Common.getOrDefault(group.getGroup(), ""));
				map.put("rule_name", Common.getOrDefault(this.ruleForGroup != null ? ((Rule) this.ruleForGroup).getName() : null, ""));
			}

			map.put("rule_fine", this.ruleOrGroupEvaluated.getFine());

			return map;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#replaceVariables(java.lang.String, org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected String replaceVariables(String message, RuleOperator operator) {
			return replaceGroupMatches(super.replaceVariables(message, operator), this.matcher);
		}

		/*
		 * Replace $ + group match index for the given message
		 */
		private String replaceGroupMatches(@NonNull String message, @Nullable Matcher matcher) {

			if (matcher != null)
				for (int i = 0; i <= matcher.groupCount(); i++)
					try {
						message = message.replace("$" + i, Common.getOrEmpty(matcher.group(i)));
					} catch (final IllegalStateException ex) {
						// silence
					}

			return message;
		}
	}

	/**
	 * Represents a rule type
	 */
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Rules applied over everything
		 */
		GLOBAL("global", Log.Type.CHAT) {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Rule.Type_Global");
			}
		},

		/**
		 * Rule matching chat messages
		 */
		CHAT("chat", Log.Type.CHAT) {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Rule.Type_Chat");
			}
		},

		/**
		 * Rule matching player commands
		 */
		COMMAND("command", Log.Type.COMMAND) {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Rule.Type_Command");
			}
		},

		/**
		 * Rule matching packet messages sent to player
		 */
		PACKET("packet", null) {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Rule.Type_Packet");
			}
		},

		/**
		 * Rule matching text on signs
		 */
		SIGN("sign", Log.Type.SIGN) {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Rule.Type_Sign");
			}
		},

		/**
		 * Rule matching text in books and their titles
		 */
		BOOK("book", Log.Type.BOOK) {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Rule.Type_Book");
			}
		},

		/**
		 * Rule matching item names when renamed
		 */
		ANVIL("anvil", Log.Type.ANVIL) {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Rule.Type_Anvil");
			}
		},

		/**
		 * Rule matching player tags: nicks/prefix/suffix
		 */
		TAG("tag", Log.Type.COMMAND) {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Rule.Type_Tag");
			}
		},

		;

		/**
		 * The saveable non-obfuscated key
		 */
		@Getter
		private final String key;

		/**
		 * Get the log type or null if logging not supported
		 */
		@Getter
		@Nullable
		private final Log.Type logType;

		/**
		 * Holy cow, we're international!
		 * Just kidding, Slovakia FTW.
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
				if (mode.key.equalsIgnoreCase(key) || mode.key.equalsIgnoreCase(key.substring(0, key.length() - 1)))
					return mode;

			throw new IllegalArgumentException("No such rule type: " + key + ". Available: " + Common.join(values()));
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
