package org.mineacademy.chatcontrol.operator;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Discord;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.WarningPoints;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.exception.RegexTimeoutException;
import org.mineacademy.fo.model.DiscordSender;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.Replacer;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.CompBarColor;
import org.mineacademy.fo.remain.CompBarStyle;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Operator implements org.mineacademy.fo.model.Rule {

	/**
	 * Represents the date formatting using to evaluate "expires" operator
	 *
	 * d MMM yyyy, HH:mm
	 */
	private final static DateFormat DATE_FORMATTING = new SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.ENGLISH);

	/**
	 * Map of keys and JS expressions to match certain player data to require
	 */
	private final Map<String, String> requireData = new HashMap<>();

	/**
	 * Map of keys and JS expressions to match certain player data to ignore
	 */
	private final Map<String, String> ignoreData = new HashMap<>();

	/**
	 * Map of keys and JS expressions to match certain player data to add
	 */
	private final Map<String, String> saveData = new HashMap<>();

	/**
	 * The time in the future when this broadcast no longer runs
	 */
	@Getter
	private long expires = -1;

	/**
	 * The delay between the next time this rule can be fired up, with optional warning message
	 */
	private Tuple<SimpleTime, String> delay;

	/**
	 * List of commands to run as player when rule matches
	 */
	private final List<String> playerCommands = new ArrayList<>();

	/**
	 * List of commands to run as console when rule matches
	 */
	private final List<String> consoleCommands = new ArrayList<>();

	/**
	 * List of commands to send to BungeeCord to run when rule matches
	 */
	private final List<String> bungeeCommands = new ArrayList<>();

	/**
	 * List of messages to log
	 */
	private final List<String> consoleMessages = new ArrayList<>();

	/**
	 * Kick message that when set, rule will kick player
	 */
	@Nullable
	private String kickMessage;

	/**
	 * The message that, if set, will show as a toast notification
	 */
	@Nullable
	private Tuple<CompMaterial, String> toast;

	/**
	 * Permission:Message map to send to other players having such permission
	 */
	private final Map<String, String> notifyMessages = new HashMap<>();

	/**
	 * Channel:Message map to send to Discord
	 */
	private final Map<String, String> discordMessages = new HashMap<>();

	/**
	 * File:Message messages to log
	 */
	private final Map<String, String> writeMessages = new HashMap<>();

	/**
	 * Map of messages to send back to player when rule matches
	 * They have unique ID assigned to prevent duplication
	 */
	private final Map<UUID, String> warnMessages = new LinkedHashMap<>();

	/**
	 * How much money to take from player? Uses Vault.
	 */
	private double fine = 0D;

	/**
	 * Warning set:Points map to give warning points for these sets
	 */
	private final Map<String, Double> warningPoints = new HashMap<>();

	/**
	 * Lists of sounds to send to player
	 */
	private final List<SimpleSound> sounds = new ArrayList<>();

	/**
	 * The book to open for player
	 */
	@Nullable
	private Book book;

	/**
	 * Title and subtitle to send
	 */
	@Nullable
	private Tuple<String, String> title;

	/**
	 * The message on the action bar
	 */
	@Nullable
	private String actionBar;

	/**
	 * The Boss bar message
	 */
	@Nullable
	private BossBarMessage bossBar;

	/**
	 * Should we abort checking more rules below this one?
	 */
	private boolean abort = false;

	/**
	 * Shall we cancel the event and not send the message at all?
	 */
	private boolean cancelMessage = false;

	/**
	 * Should we send the message only to the sender making him think it went through
	 * while hiding it from everyone else?
	 */
	private boolean cancelMessageSilently = false;

	/**
	 * Should we exempt the rule from being logged?
	 */
	private boolean ignoreLogging = false;

	/**
	 * Prevent console catch information coming up?
	 */
	private boolean ignoreVerbose = false;

	/**
	 * Is this class (all operators here) temporary disabled?
	 */
	private boolean disabled;

	/**
	 * The time the operator was last executed
	 */
	@Setter(value = AccessLevel.PROTECTED)
	@Getter
	private long lastExecuted = -1;

	/**
	 * @see org.mineacademy.fo.model.Rule#onOperatorParse(java.lang.String[])
	 */
	@Override
	public final boolean onOperatorParse(String[] args) {
		final String param = Common.joinRange(0, 2, args);
		final String theRest = Common.joinRange(args.length >= 2 ? 2 : 1, args);

		final List<String> theRestSplit = splitVertically(theRest);

		if ("require key".equals(param) || "ignore key".equals(param) || "save key".equals(param)) {
			final String[] split = theRest.split(" ");
			Valid.checkBoolean(split.length > 0, "Wrong operator syntax! Usage: <keyName> <JavaScript condition with 'value' as the value object>");

			final String key = split[0];
			final String script = split.length > 1 ? Common.joinRange(1, split) : "";

			if ("require key".equals(param)) {
				Valid.checkBoolean(!this.requireData.containsKey(key), "The 'require key' operator already contains key: " + key);

				this.requireData.put(key, script);

			} else if ("ignore key".equals(param)) {
				Valid.checkBoolean(!this.ignoreData.containsKey(key), "The 'ignore key' operator already contains key: " + key);

				this.ignoreData.put(key, script);

			} else if ("save key".equals(param)) {
				Valid.checkBoolean(!this.saveData.containsKey(key), "The 'save key' operator already contains key: " + key);

				this.saveData.put(key, script);
			}

		}

		else if ("expires".equals(args[0])) {
			Valid.checkBoolean(this.expires == -1, "Operator 'expires' already defined on " + this);

			String date = Common.joinRange(1, args);

			try {
				// Workaround to enable users put in both short and fully abbreviated month names
				final String[] months = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
				final String[] fullNameMonths = new String[] { "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" };

				for (int i = 0; i < months.length; i++)
					date = date.replaceAll(months[i] + "\\b", fullNameMonths[i]);

				this.expires = DATE_FORMATTING.parse(date).getTime();

			} catch (final ParseException ex) {
				Common.throwError(ex, "Syntax error in 'expires' operator. Valid: dd MMM yyyy, HH:mm Got: " + date);
			}
		}

		else if ("delay".equals(args[0])) {
			checkNotSet(this.delay, "delay");

			try {
				final SimpleTime time = SimpleTime.from(Common.joinRange(1, 3, args));
				final String message = args.length > 2 ? Common.joinRange(3, args) : null;

				this.delay = new Tuple<>(time, message);

			} catch (final Throwable ex) {
				Common.throwError(ex, "Syntax error in 'delay' operator. Valid: <amount> <unit> (1 second, 2 minutes). Got: " + String.join(" ", args));
			}
		}

		else if ("then command".equals(param) || "then commands".equals(param))
			this.playerCommands.addAll(theRestSplit);

		else if ("then console".equals(param))
			this.consoleCommands.addAll(theRestSplit);

		else if ("then bungeeconsole".equals(param) || "then bungee".equals(param))
			this.bungeeCommands.addAll(theRestSplit);

		else if ("then log".equals(param))
			this.consoleMessages.addAll(theRestSplit);

		else if ("then kick".equals(param)) {
			checkNotSet(this.kickMessage, "then kick");

			this.kickMessage = theRest;
		}

		else if ("then toast".equals(param)) {
			checkNotSet(this.toast, "then toast");

			final String[] split = theRest.split(" ");

			// Test for material but not mandatory
			final CompMaterial material = ReflectionUtil.lookupEnumSilent(CompMaterial.class, split[0].toUpperCase());
			this.toast = new Tuple<>(Common.getOrDefault(material, CompMaterial.WRITTEN_BOOK), material == null ? theRest : Common.joinRange(1, split));
		}

		else if ("then notify".equals(param)) {
			final String[] split = theRest.split(" ");
			Valid.checkBoolean(split.length > 1, "wrong then notify syntax! Usage: <permission> <message>");

			final String permission = split[0];
			final String message = Common.joinRange(1, split);

			this.notifyMessages.put(permission, message);
		}

		else if ("then discord".equals(param)) {
			final String[] split = theRest.split(" ");
			Valid.checkBoolean(split.length > 1, "wrong then discord syntax! Usage: <channel> <message>");

			final String channel = split[0];
			final String message = Common.joinRange(1, split);

			this.discordMessages.put(channel, message);
		}

		else if ("then write".equals(param)) {
			final String[] split = theRest.split(" ");
			Valid.checkBoolean(split.length > 1, "wrong 'then log' syntax! Usage: <file (without spaces)> <message>");

			final String file = split[0];
			final String message = Common.joinRange(1, split);

			this.writeMessages.put(file, message);
		}

		else if ("then fine".equals(param)) {
			Valid.checkBoolean(this.fine == 0D, "everything is fine except you specifying 'then fine' twice (dont do that) for rule: " + this);

			double fine;

			try {
				fine = Double.parseDouble(theRest);

			} catch (final NumberFormatException ex) {
				throw new FoException("Invalid whole number in 'then fine': " + theRest);
			}

			this.fine = fine;
		}

		else if ("then points".equals(param)) {
			final String[] split = theRest.split(" ");
			Valid.checkBoolean(split.length == 2, "wrong then points syntax! Usage: <warning set> <points>");

			final String warningSet = split[0];

			double points;

			try {
				points = Double.parseDouble(split[1]);

			} catch (final NumberFormatException ex) {
				throw new FoException("Invalid whole number in 'then points': " + split[1]);
			}

			this.warningPoints.put(warningSet, points);
		}

		else if ("then sound".equals(param)) {
			final SimpleSound sound = new SimpleSound(theRest);

			this.sounds.add(sound);
		}

		else if ("then book".equals(param)) {
			checkNotSet(this.book, "then book");

			this.book = Book.fromFile(theRest);
		}

		else if ("then title".equals(param)) {
			checkNotSet(this.title, "then title");

			final List<String> split = splitVertically(theRest);
			final String title = split.get(0);
			final String subtitle = split.size() > 1 ? split.get(1) : "";

			this.title = new Tuple<>(title, subtitle);
		}

		else if ("then actionbar".equals(param)) {
			checkNotSet(this.actionBar, "then actionbar");

			this.actionBar = theRest;
		}

		else if ("then bossbar".equals(param)) {
			checkNotSet(this.bossBar, "then bossbar");

			final String[] split = theRest.split(" ");
			Valid.checkBoolean(split.length >= 4, "Invalid 'then bossbar' syntax. Usage: <color> <style> <secondsToShow> <message>");

			final CompBarColor color = CompBarColor.fromKey(split[0]);
			final CompBarStyle style = CompBarStyle.fromKey(split[1]);

			int secondsToShow;

			try {
				secondsToShow = Integer.parseInt(split[2]);

			} catch (final NumberFormatException ex) {
				throw new FoException("Invalid seconds to show in 'then bossbar': " + split[2]);
			}

			final String message = Common.joinRange(3, split);

			this.bossBar = new BossBarMessage(color, style, secondsToShow, message);
		}

		else if ("then warn".equals(param)) {
			for (final String message : theRestSplit)
				this.warnMessages.put(UUID.randomUUID(), message);

		} else if ("then abort".equals(param)) {
			Valid.checkBoolean(this.abort == false, "then abort already used on " + this);

			this.abort = true;
		}

		else if ("then deny".equals(param)) {
			if ("silently".equals(theRest)) {
				Valid.checkBoolean(this.cancelMessageSilently == false, "then deny silently already used on " + this);

				this.cancelMessageSilently = true;

			} else {
				Valid.checkBoolean(this.cancelMessage == false, "then deny already used on " + this);

				this.cancelMessage = true;
			}
		}

		else if ("dont log".equals(param)) {
			Valid.checkBoolean(this.ignoreLogging == false, "dont log already used on " + this);

			this.ignoreLogging = true;
		}

		else if ("dont verbose".equals(param)) {
			Valid.checkBoolean(this.ignoreVerbose == false, "dont verbose already used on " + this);

			this.ignoreVerbose = true;
		}

		else if ("disabled".equals(args[0])) {
			Valid.checkBoolean(!this.disabled, "'disabled' already used on " + this);

			this.disabled = true;
		}

		else {
			final boolean success = onParse(param, theRest, args);

			Valid.checkBoolean(success, "Unrecognized operator '" + String.join(" ", args) + "' found in " + this);
		}

		return true;
	}

	/**
	 * Parses additional operators
	 *
	 * @param param
	 * @param theRest
	 * @param args
	 * @return
	 */
	protected abstract boolean onParse(String param, String theRest, String[] args);

	/**
	 * Check if the value is null or complains that the operator of the given type is already defined
	 *
	 * @param value
	 * @param type
	 */
	protected final void checkNotSet(Object value, String type) {
		Valid.checkBoolean(value == null, "Operator '" + type + "' already defined on " + this);
	}

	/**
	 * A helper method to split a message by |
	 * but ignore \| and replace it with | only.
	 *
	 * @param message
	 * @return
	 */
	protected final List<String> splitVertically(String message) {
		final List<String> split = Arrays.asList(message.split("(?<!\\\\)\\|"));

		for (int i = 0; i < split.size(); i++)
			split.set(i, split.get(i).replace("\\|", "|"));

		return split;
	}

	/**
	 * Collect all options we have to debug
	 *
	 * @return
	 */
	protected SerializedMap collectOptions() {
		return SerializedMap.ofArray(
				"Require Keys", this.requireData,
				"Ignore Keys", this.ignoreData,
				"Save Keys", this.saveData,
				"Expires", this.expires != -1 ? this.expires : null,
				"Delay", this.delay,
				"Player Commands", this.playerCommands,
				"Console Commands", this.consoleCommands,
				"BungeeCord Commands", this.bungeeCommands,
				"Console Messages", this.consoleMessages,
				"Kick Message", this.kickMessage,
				"Toast Message", this.toast,
				"Notify Messages", this.notifyMessages,
				"Discord Message", this.discordMessages,
				"Log To File", this.writeMessages,
				"Fine", this.fine,
				"Warning Points", this.warningPoints,
				"Sounds", this.sounds,
				"Book", this.book,
				"Title", this.title,
				"Action Bar", this.actionBar,
				"Boss Bar", this.bossBar == null ? null : this.bossBar.toString(),
				"Warn Messages", this.warnMessages,
				"Abort", this.abort,
				"Cancel Message", this.cancelMessage,
				"Cancel Message Silently", this.cancelMessageSilently,
				"Ignore Logging", this.ignoreLogging,
				"Ignore Verbose", this.ignoreVerbose,
				"Disabled", this.disabled

		);
	}

	/**
	 * Return a tostring representation suitable to show in game
	 *
	 * @return
	 */
	public final String toDisplayableString() {
		return Common.revertColorizing(toString().replace("\t", "    "));
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public final boolean equals(Object obj) {
		return obj instanceof Operator && ((Operator) obj).getUid().equals(this.getUid());
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a check that is implemented by this class
	 */
	public abstract static class OperatorCheck<T extends Operator> {

		/**
		 * The sender involved in this check
		 */
		protected CommandSender sender;

		/**
		 * The message that is being altered
		 */
		@Getter
		@Nullable
		protected String message;

		/**
		 * The original message that was matched
		 */
		@Nullable
		protected String originalMessage;

		/**
		 * Is the {@link #sender} a {@link Player}?
		 */
		protected boolean isPlayer;

		/**
		 * The player if {@link #isPlayer} is true
		 */
		protected Player player;

		/**
		 * The player cache if {@link #isPlayer} is true
		 */
		protected PlayerCache cache;

		/**
		 * The sender cache
		 */
		protected SenderCache senderCache;

		/**
		 * Should we cancel the event silently and only send the message
		 * to the sender himself?
		 */
		@Getter
		protected boolean cancelledSilently;

		/**
		 * Was the sender already warned? Used to prevent multiple warnings.
		 */
		private boolean receivedAnyWarningMessage;

		/**
		 * Stores sent notify messages to prevent duplication, such as when multiple curse words are matched
		 */
		private final Set<String> notifyMessages = new HashSet<>();

		/**
		 * Construct check and useful parameters
		 *
		 * @param sender
		 * @param message
		 */
		protected OperatorCheck(CommandSender sender, String message) {
			this.sender = sender;
			this.message = message;
			this.originalMessage = message == null ? null : new String(message);

			this.isPlayer = sender instanceof Player;
			this.player = isPlayer ? (Player) sender : null;
			this.cache = isPlayer ? PlayerCache.from(player) : null;
			this.senderCache = sender != null ? SenderCache.from(sender) : null;
		}

		public final void start() {

			// Collect all to filter
			final List<T> operators = getOperators();

			// Iterate through all rules and parse
			for (final T operator : operators)
				try {
					filter(operator);

				} catch (final OperatorAbortException ex) {
					// Verbose
					if (!operator.isIgnoreVerbose())
						verbose("&cStopping further operator check.");

					break;

				} catch (final EventHandledException ex) {
					throw ex; // send upstream

				} catch (final RegexTimeoutException ex) {
					final Pattern pattern = operator instanceof Rule ? ((Rule) operator).getPattern() : null;

					Common.handleRegexTimeoutException(ex, pattern);

				} catch (final Throwable t) {
					Common.throwError(t, "Error parsing rule: " + operator);
				}
		}

		/**
		 * Returns the list of effective operators this check will evaluate against the message
		 *
		 * @return
		 */
		public abstract List<T> getOperators();

		/**
		 * Starts the filtering
		 */
		protected abstract void filter(T operator) throws EventHandledException;

		/**
		 * Return true if the given operator can be applied for the given message
		 */
		protected boolean canFilter(T operator) {

			// Ignore disabled rules
			if (operator.isDisabled())
				return false;

			// Expired
			if (operator.getExpires() != -1 && System.currentTimeMillis() > operator.getExpires())
				return false;

			if (this.isPlayer) {
				for (final Map.Entry<String, String> entry : operator.getRequireData().entrySet()) {
					final String key = entry.getKey();

					if (!this.cache.hasRuleData(key))
						return false;

					if (entry.getValue() != null && !"".equals(entry.getValue())) {
						final String script = replaceVariables(entry.getValue(), operator);

						final Object value = this.cache.getRuleData(key);
						final Object result = JavaScriptExecutor.run(script, SerializedMap.ofArray("player", this.player, "value", value).asMap());

						Valid.checkBoolean(result instanceof Boolean, "'require key' expected boolean, got " + result.getClass() + ": " + result + " for rule: " + this);

						if ((boolean) result == false)
							return false;
					}
				}

				for (final Map.Entry<String, String> entry : operator.getIgnoreData().entrySet()) {
					final String key = entry.getKey();
					final Object value = this.cache.getRuleData(key);

					if ((entry.getValue() == null || "".equals(entry.getValue())) && value != null)
						return false;

					final String script = replaceVariables(entry.getValue(), operator);

					if (value != null) {
						final Object result = JavaScriptExecutor.run(script, SerializedMap.ofArray("player", this.player, "value", value).asMap());
						Valid.checkBoolean(result instanceof Boolean, "'ignore key' expected boolean, got " + result.getClass() + ": " + result + " for rule: " + this);

						if ((boolean) result == true)
							return false;
					}
				}
			}

			return true;
		}

		/**
		 * Run given operators for the given message and return the updated message
		 */
		protected void executeOperators(T operator) throws EventHandledException {

			if (isPlayer)
				for (final String command : operator.getPlayerCommands())
					Common.dispatchCommandAsPlayer(player, replaceVariables(command, operator));

			if (!(sender instanceof ConsoleCommandSender))
				for (final String command : operator.getConsoleCommands())
					Common.dispatchCommand(sender, replaceVariables(command, operator));

			for (final String commandLine : operator.getBungeeCommands()) {
				final String[] split = commandLine.split(" ");
				final String server = split.length > 1 ? split[0] : "bungee";
				final String command = split.length > 1 ? Common.joinRange(1, split) : split[0];

				BungeeUtil.tellBungee(BungeePacket.FORWARD_COMMAND, server, replaceVariables(command, operator));
			}

			for (final String message : operator.getConsoleMessages())
				Common.log(replaceVariables(message, operator));

			for (final Map.Entry<String, String> entry : operator.getNotifyMessages().entrySet()) {
				final String permission = entry.getKey();
				final String formatOrMessage = replaceVariables(entry.getValue(), operator);

				final Format format = Format.parse(formatOrMessage);
				Valid.checkNotNull(format, "'then notify' operator contains invalid format: " + formatOrMessage);

				final SimpleComponent component = format.build(sender, message, prepareVariables(operator));
				final String plain = component.getPlainMessage();

				if (!this.notifyMessages.contains(plain)) {
					this.notifyMessages.add(plain);

					Players.broadcastWithPermission(permission, component);

					if (BungeeCord.ENABLED)
						BungeeUtil.tellBungee(BungeePacket.NOTIFY, permission, component.serialize());
				}
			}

			if (HookManager.isDiscordSRVLoaded())
				for (final Map.Entry<String, String> entry : operator.getDiscordMessages().entrySet()) {
					final String discordChannel = entry.getKey();
					final String discordMessage = entry.getValue();

					Discord.getInstance().sendChannelMessageNoPlayer(discordChannel, replaceVariables(discordMessage, operator));
				}

			for (final Map.Entry<String, String> entry : operator.getWriteMessages().entrySet()) {
				final String file = entry.getKey();
				final String message = replaceVariables(entry.getValue(), operator);

				// Run async for best performance
				Common.runAsync(() -> FileUtil.writeFormatted(file, message));
			}

			if (isPlayer) {
				if (operator.getFine() > 0)
					HookManager.withdraw(player, operator.getFine());

				for (final Entry<String, Double> entry : operator.getWarningPoints().entrySet()) {
					final boolean warned = WarningPoints.getInstance().givePoints(player, entry.getKey(), entry.getValue());

					if (!this.receivedAnyWarningMessage && warned)
						this.receivedAnyWarningMessage = true;
				}

				for (final SimpleSound sound : operator.getSounds())
					sound.play(player);

				if (operator.getBook() != null)
					operator.getBook().open(player);

				if (operator.getToast() != null)
					Common.runLater(() -> Remain.sendToast(player, replaceVariables(operator.getToast().getValue(), operator).replace("|", "\n"), operator.getToast().getKey()));

				if (operator.getTitle() != null)
					Remain.sendTitle(player, replaceVariables(operator.getTitle().getKey(), operator), replaceVariables(operator.getTitle().getValue(), operator));

				if (operator.getActionBar() != null)
					Remain.sendActionBar(player, replaceVariables(operator.getActionBar(), operator));

				if (operator.getBossBar() != null)
					operator.getBossBar().displayTo(player, replaceVariables(operator.getBossBar().getMessage(), operator));

				for (final Map.Entry<String, String> entry : operator.getSaveData().entrySet()) {
					final String key = entry.getKey();
					final String script = replaceVariables(entry.getValue(), operator);
					final Object result = script.trim().isEmpty() ? null : JavaScriptExecutor.run(script, SerializedMap.ofArray("player", this.player).asMap());

					Common.runLater(() -> this.cache.setRuleData(key, result));
				}
			}

			if (operator.getKickMessage() != null) {
				final String kickReason = replaceVariables(operator.getKickMessage(), operator);

				if (isPlayer)
					Common.runLater(() -> player.kickPlayer(kickReason));

				else if (sender instanceof DiscordSender)
					Discord.getInstance().kickMember((DiscordSender) sender, kickReason);
			}

			// Dirty: Run later including when EventHandledException is thrown
			Common.runLater(1, () -> {
				if (!operator.getWarnMessages().isEmpty() && !receivedAnyWarningMessage) {
					for (final Entry<UUID, String> entry : operator.getWarnMessages().entrySet()) {
						final UUID uniqueId = entry.getKey();
						final String warnMessage = entry.getValue();

						final long now = System.currentTimeMillis();
						final long lastTimeShown = this.senderCache.getRecentWarningMessages().getOrDefault(uniqueId, -1L);

						// Prevent duplicate messages in the last 0.5 seconds
						if (lastTimeShown == -1L || (now - lastTimeShown) > 500) {
							final Format format = Format.parse(replaceVariables(warnMessage, operator));
							final SimpleComponent component = format.build(sender, message);

							component.send(sender);
							this.senderCache.getRecentWarningMessages().put(uniqueId, now);
						}
					}
				}
			});

			if (operator.isCancelMessage()) {
				if (!operator.isIgnoreVerbose())
					verbose("&cOriginal message cancelled.");

				throw new EventHandledException(true);
			}

			if (operator.isCancelMessageSilently())
				this.cancelledSilently = true;
		}

		/*
		 * Replace all kinds of check variables
		 */
		protected String replaceVariables(@Nullable String message, T operator) {
			if (message == null)
				return null;

			if (isPlayer)
				for (final Map.Entry<String, Object> data : this.cache.getRuleData().entrySet())
					message = message.replace("{data_" + data.getKey() + "}", SerializeUtil.serialize(data.getValue()).toString());

			return Variables.replace(Replacer.replaceVariables(message, prepareVariables(operator)), sender);
		}

		/**
		 * Prepare variables available in this check
		 *
		 * @param operator
		 * @return
		 */
		protected SerializedMap prepareVariables(T operator) {
			return SerializedMap.ofArray(
					"message", Common.getOrDefaultStrict(this.message, ""),
					"original_message", Common.getOrDefaultStrict(this.originalMessage, ""));
		}

		/**
		 * Return if the sender has the given permission
		 *
		 * @param permission
		 * @return
		 */
		protected final boolean hasPerm(String permission) {
			return PlayerUtil.hasPerm(this.sender, permission);
		}

		/**
		 * Cancels the pipeline by throgin a {@link EventHandledException}
		 */
		protected final void cancel() {
			this.cancel(null);
		}

		/**
		 * Cancels the pipeline by throgin a {@link EventHandledException}
		 * and send an error message to the player
		 *
		 * @param errorMessage
		 */
		protected final void cancel(@Nullable String errorMessage) {
			if (errorMessage != null)
				Messenger.error(sender, Variables.replace(errorMessage, sender));

			throw new EventHandledException(true);
		}

		/**
		 * Show the message if rules are set to verbose
		 *
		 * @param message
		 */
		protected final void verbose(String... messages) {
			if (Settings.Rules.VERBOSE)
				Common.logNoPrefix(messages);
		}
	}

	/**
	 * Represents a simple boss bar message
	 */
	@RequiredArgsConstructor
	public static class BossBarMessage {

		/**
		 * The bar color
		 */
		private final CompBarColor color;

		/**
		 * The bar style
		 */
		private final CompBarStyle style;

		/**
		 * Seconds to show this bar
		 */
		private final int seconds;

		/**
		 * The message to show
		 */
		@Getter
		private final String message;

		/**
		 * Displays this boss bar to the given player
		 *
		 * @param player
		 * @param message replace variables here
		 */
		public void displayTo(Player player, String message) {
			Remain.sendBossbarTimed(player, message, seconds, color, style);
		}

		/**
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return color + " " + style + " " + seconds + " " + message;
		}
	}

	/**
	 * Represents an indication that further rule processing should be aborted
	 */
	@Getter
	@RequiredArgsConstructor
	public final static class OperatorAbortException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
