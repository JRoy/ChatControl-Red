package org.mineacademy.chatcontrol.model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.operator.Group;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.RuleOperator;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ItemUtil;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents an elegant way of storing server communication
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Log {

	/**
	 * The storing structure for CSV
	 */
	private final static SerializedMap fileStructure = SerializedMap.ofArray(
			"Date", "datetime",
			"Type", "text",
			"Sender", "text",
			"Receiver", "text",
			"Content", "text",
			"ChannelName", "text",
			"RuleName", "text",
			"RuleGroupName", "text");

	/**
	 * The storing structure for MySQL
	 */
	@Getter
	private final static SerializedMap databaseStructure = SerializedMap.ofArray(
			"Server", "text",
			"Date", "datetime",
			"Type", "text",
			"Sender", "text",
			"Receiver", "text",
			"Content", "text",
			"ChannelName", "text",
			"RuleName", "text",
			"RuleGroupName", "text");

	/**
	 * The date format in dd.MM.yyy HH:mm:ss
	 */
	private static final DateFormat fileDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

	/**
	 * The path to the file used for all logging
	 */
	private final static String filePath = "log.csv";

	/**
	 * What console messages we already showed to the console? This is not stored in any
	 * file and resets every reload/restart. Used to prevent console message spam to
	 * only make certain messages show once.
	 */
	private final static Set<String> shownConsoleMessages = new HashSet<>();

	/* ------------------------------------------------------------------------------- */
	/* A log properties */
	/* ------------------------------------------------------------------------------- */

	/**
	 * The date of this log
	 */
	private final long date;

	/**
	 * The type of this log
	 */
	private final Type type;

	/**
	 * The issuer of this log
	 */
	private final String sender;

	/**
	 * The issuer's input
	 */
	private final String content;

	/**
	 * Optional: The receivers of this message
	 */
	private List<String> receivers = new ArrayList<>();

	/**
	 * Optional, channel name, if associated with
	 */
	@Nullable
	private String channelName;

	/**
	 * Optional, rule name, if associated with
	 */
	@Nullable
	private String ruleName;

	/**
	 * Optional, rule group name, if associated with
	 */
	@Nullable
	private String ruleGroupName;

	/**
	 * Set the receiver
	 *
	 * @param receiver the receiver to set
	 */
	public Log receiver(String receiver) {
		this.receivers.add(receiver);

		return this;
	}

	/**
	 * Set the receivers
	 *
	 * @param receivers the receivers to set
	 */
	public Log receivers(Set<String> receivers) {
		this.receivers.addAll(receivers);

		return this;
	}

	/**
	 * Attach a channel to this log
	 *
	 * @param channel
	 * @return
	 */
	public Log channel(Channel channel) {
		this.channelName = channel.getName();

		return this;
	}

	/**
	 * Attach a rule to this log
	 *
	 * @param rule
	 * @return
	 */
	public Log rule(Rule rule) {
		this.ruleName = rule.getName();

		return this;
	}

	/**
	 * Attach a group to this log
	 *
	 * @param group
	 * @return
	 */
	public Log ruleGroup(Group group) {
		this.ruleGroupName = group.getGroup();

		return this;
	}

	/* ------------------------------------------------------------------------------- */
	/* Writing */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Write this log to database if enabled, if not then to file.
	 *
	 * @return true if log was saved successfully
	 */
	public boolean write(@Nullable CommandSender sender) {

		if (!Settings.Log.APPLY_ON.contains(this.type))
			return false;

		if (this.type == Type.COMMAND && !Settings.Log.COMMAND_LIST.isInList(this.content.split(" ")[0]))
			return false;

		if (sender != null && PlayerUtil.hasPerm(sender, Permissions.Bypass.LOG)) {
			Log.logOnce("log-bypass", "Note: Not logging " + sender.getName() + "'s " + type + " because he had '" + Permissions.Bypass.LOG + "' permission." +
					" Players with these permission do not get their content logged. To disable that, give him this permission as negative (a false value if using LuckPerms).");

			return false;
		}

		// Write to database
		if (Settings.MySQL.ENABLED)
			Common.runAsync(() -> Database.getInstance().insertLogValues(makeLineDb()));

		else {
			final File file = FileUtil.getOrMakeFile(filePath);

			// Write the line - use our own method to handle \n
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {

				// Write header, assuming when something is in file, that header was written already
				if (FileUtil.readLines(file).isEmpty())
					writer.append(Common.join(fileStructure.keySet(), ", ") + System.lineSeparator());

				writer.append(makeLineCsv().replace("\n", "\\n") + System.lineSeparator());

			} catch (final IOException ex) {
				ex.printStackTrace();
			}
		}

		return true;
	}

	/*
	 * Convert this log into a writeable line for csv
	 */
	private String makeLineCsv() {
		final List<Object> values = Arrays.asList(
				fileDateFormat.format(new Date(date)),
				type,
				sender,
				receivers.isEmpty() ? null : Remain.toJson(receivers),
				content,
				channelName,
				ruleName,
				ruleGroupName);

		String line = "";

		for (int i = 0; i < values.size(); i++) {
			final Object value = SerializeUtil.serialize(values.get(i));

			line += value != null && !value.equals("NULL") && !"".equals(value) ? "'" + value.toString().replace(", ", ",/ ") + "'" : "''";
			line += i + 1 < values.size() ? ", " : "";
		}

		return line;
	}

	/*
	 * Convert this log into a writeable line for MySQL
	 */
	private String makeLineDb() {
		final List<Object> values = Arrays.asList(
				Remain.getServerName(), // different from csv
				TimeUtil.toSQLTimestamp(date), // different from csv
				type,
				sender,
				receivers.isEmpty() ? null : Remain.toJson(receivers),
				content,
				channelName,
				ruleName,
				ruleGroupName);

		String line = "";

		for (int i = 0; i < values.size(); i++) {
			final Object value = SerializeUtil.serialize(values.get(i));

			line += value != null && !value.equals("NULL") ? "'" + value.toString()
					.replace("'", "")
					.replace("\"", "\\\"")
					.replace("\\", "\\\\")
					.replace(", ", ",/ ") + "'" : "NULL"; // different from csv

			line += i + 1 < values.size() ? ", " : "";
		}

		return line;
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return all file logs
	 *
	 * @return
	 */
	public static List<Log> readLogs() {
		List<Log> loaded = new ArrayList<>();

		// Write to database
		if (Settings.MySQL.ENABLED)
			loaded = Database.getInstance().getLogEntries();

		else {
			final File file = FileUtil.getFile(filePath);

			if (file.exists())
				for (final String line : FileUtil.readLines(file))
					if (!isHeader(line))
						loaded.add(fromLine(line));
		}

		Collections.reverse(loaded);

		return loaded;
	}

	/**
	 * Purge old entries over {@link Settings.Log#CLEAN_AFTER} limit
	 */
	public static void purgeOldEntries() {

		// Write to database
		if (Settings.MySQL.ENABLED)
			Database.getInstance().purgeLogEntries();

		else {
			final File file = FileUtil.getFile(filePath);

			if (file.exists()) {
				final List<String> lines = FileUtil.readLines(file);

				lines.removeIf(line -> {
					if (!isHeader(line)) {
						final Log log = fromLine(line);
						final long threshold = System.currentTimeMillis() - (Settings.Log.CLEAN_AFTER.getTimeSeconds() * 1000L);

						return log.getDate() < threshold;
					}

					return false;
				});

				FileUtil.write(file, lines, StandardOpenOption.TRUNCATE_EXISTING);
			}
		}
	}

	/*
	 * Attempt to parse log from the given line
	 */
	private static Log fromLine(String line) {
		final String[] split = line.split(", ");
		Valid.checkBoolean(split.length == fileStructure.size(), "Log line size does not match structure: (" + fileStructure.size() + ") " + fileStructure.keySet() + ". Line: (" + split.length + ") " + line);

		// Remove quotations
		for (int i = 0; i < split.length; i++) {
			String part = split[i].trim();

			part = part.charAt(0) == '\'' ? part.substring(1) : part;
			part = part.charAt(part.length() - 1) == '\'' ? part.substring(0, part.length() - 1) : part;

			split[i] = part;
		}

		try {
			final long date = fileDateFormat.parse(split[0]).getTime();
			final Type type = Type.fromKey(split[1]);
			final String sender = split[2];
			final List<String> receivers = Common.getOrDefault(split[3].equals("NULL") ? null : Remain.fromJsonList(split[3]), new ArrayList<>());
			final String content = split[4].replace(",/ ", ", ");
			final String channelName = split[5].equals("NULL") || "".equals(split[5].trim()) ? null : split[5];
			final String ruleName = split[6].equals("NULL") || "".equals(split[6].trim()) ? null : split[6];
			final String ruleGroupName = split[7].equals("NULL") || "".equals(split[7].trim()) ? null : split[7];

			return new Log(date, type, sender, content, receivers, channelName, ruleName, ruleGroupName);

		} catch (final Throwable t) {
			throw new FoException(t, "Failed parsing csv log line: " + line + " The error was: " + t);
		}
	}

	/*
	 * Return if the line is the log header
	 */
	private static boolean isHeader(String line) {
		return String.join(",", fileStructure.keySet()).equals(line.replace(" ", ""));
	}

	/**
	 * Create a new log from a chat message coming from another dimension... server
	 *
	 * @param sender
	 * @param channel
	 * @param message
	 */
	public static void logBungeeChat(String senderName, Channel channel, String message) {

		// Only log locally because it already has been placed to MySQL
		if (!Settings.MySQL.ENABLED) {
			final Log log = new Log(System.currentTimeMillis(), Type.CHAT, senderName, message);

			log.channel(channel);
			log.write(null);
		}
	}

	/**
	 * Create a new log from a chat message
	 *
	 * @param sender
	 * @param channel
	 * @param message
	 * @return
	 */
	public static void logChat(CommandSender sender, @Nullable Channel channel, String message) {
		final Log log = new Log(System.currentTimeMillis(), Type.CHAT, sender.getName(), message);

		if (channel != null)
			log.channel(channel);

		log.write(sender);
	}

	/**
	 * Create a new log from a command
	 *
	 * @param sender
	 * @param command
	 * @return
	 */
	public static void logCommand(CommandSender sender, String command) {
		final Log log = new Log(System.currentTimeMillis(), Type.COMMAND, sender.getName(), command);

		log.write(sender);
	}

	/**
	 * Create a new log from a private message
	 *
	 * @param sender
	 * @param receiver
	 * @param message
	 */
	public static void logPrivateMessage(CommandSender sender, String receiver, String message) {
		final Log log = new Log(System.currentTimeMillis(), Type.PRIVATE_MESSAGE, sender.getName(), message);

		log.receiver(receiver);
		log.write(sender);
	}

	/**
	 * Create a new log from a sign
	 *
	 * @param player
	 * @param lines
	 */
	public static void logSign(Player player, String[] lines) {
		final Log log = new Log(System.currentTimeMillis(), Type.SIGN, player.getName(), String.join("%FOLINES%", lines));

		log.write(player);
	}

	/**
	 * Create a new log from a book
	 *
	 * @param player
	 * @param sign
	 */
	public static void logBook(Player player, Book book) {
		final Log log = new Log(System.currentTimeMillis(), Type.BOOK, player.getName(), book.serialize().toJson());

		log.write(player);
	}

	/**
	 * Create a new log from a book
	 *
	 * @param player
	 * @param book
	 */
	public static void logAnvil(Player player, ItemStack item) {
		final ItemMeta meta = item.getItemMeta();

		if (meta != null) {
			// Saving the whole stack would be overwhelming, only save name/lore
			final String type = ItemUtil.bountify(item.getType());
			final String name = meta.hasDisplayName() ? meta.getDisplayName() : type;
			final List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

			final Log log = new Log(System.currentTimeMillis(), Type.ANVIL, player.getName(), SerializedMap.ofArray("type", type, "name", name, "lore", lore).toJson());

			log.write(player);
		}
	}

	/**
	 * Create a new log from a mail
	 *
	 * @param player
	 * @param mail
	 */
	public static void logMail(Player player, Mail mail) {
		final Log log = new Log(System.currentTimeMillis(), Type.MAIL, player.getName(), mail.toJson());

		log.write(player);
	}

	/**
	 * Create a new log from a rule
	 *
	 * @param sender
	 * @param operator
	 * @param message
	 * @return
	 */
	public static void logRule(@NonNull Rule.Type type, CommandSender sender, @NonNull RuleOperator operator, @NonNull String message) {

		// Logging not supported
		if (type.getLogType() == null)
			return;

		final Log log = new Log(System.currentTimeMillis(), type.getLogType(), sender.getName(), message);

		if (sender instanceof Player) {
			final PlayerCache cache = PlayerCache.from((Player) sender);

			if (cache.getWriteChannel() != null)
				log.channel(cache.getWriteChannel());
		}

		if (operator instanceof Rule)
			log.rule((Rule) operator);

		else if (operator instanceof Group)
			log.ruleGroup((Group) operator);

		else
			throw new FoException("Logging of operator class " + operator.getClass() + " not implemented!");

		log.write(sender);
	}

	/**
	 * Logs the given message once per plugin session
	 *
	 * @param section
	 * @param message
	 */
	public static void logOnce(String section, String message) {
		if (Settings.SHOW_TIPS && !shownConsoleMessages.contains(section)) {
			Common.logTimed(60 * 60 * 3, message + " This message only shows once per 3 hours.");

			shownConsoleMessages.add(section);
		}
	}

	/**
	 * Show a less but still important informational message that the user
	 * can toggle off in settings.yml
	 *
	 * @param message
	 */
	public static void logTip(String message) {
		if (Settings.SHOW_TIPS)
			Common.log(message);
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a log type
	 */
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * This log is a chat message
		 */
		CHAT("chat") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Log.Type_Chat");
			}
		},

		/**
		 * This log is a command
		 */
		COMMAND("command") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Log.Type_Command");
			}
		},

		/**
		 * This log is a private message
		 */
		PRIVATE_MESSAGE("private_message") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Log.Type_Private_Message");
			}
		},

		/**
		 * This log is a mail
		 */
		MAIL("mail") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Log.Type_Mail");
			}
		},

		/**
		 * This log is a sign
		 */
		SIGN("sign") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Log.Type_Sign");
			}
		},

		/**
		 * This log is book
		 */
		BOOK("book") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Log.Type_Book");
			}
		},

		/**
		 * This log holds an itemus
		 */
		ANVIL("anvil") {
			@Override
			public String getLocalized() {
				return Lang.of("Commands.Log.Type_Anvil");
			}
		},

		;

		/**
		 * The saveable non-obfuscated key
		 */
		@Getter
		private final String key;

		/**
		 * OMG this is localized again!
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

			throw new IllegalArgumentException("No such log type: " + key + ". Available: " + Common.join(values()));
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
