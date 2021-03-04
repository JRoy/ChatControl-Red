package org.mineacademy.chatcontrol.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.settings.SimpleSettings;

import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Helps migrate from ChatControl 8
 */
public final class Migrator {

	/**
	 * Instance of this class
	 */
	private final static Migrator instance = new Migrator();

	/**
	 * ChatControl 8 folder
	 */
	private final File oldDataFolder;

	/**
	 * settings.yml file
	 */
	private final File mainConfigFile;

	/**
	 * settings.yml file as config
	 */
	private final FileConfiguration mainConfig;

	/*
	 * Create a new instance of this class
	 */
	private Migrator() {
		this.oldDataFolder = new File("plugins/ChatControl");
		this.mainConfigFile = FileUtil.getOrMakeFile("settings.yml");
		this.mainConfig = FileUtil.loadConfigurationStrict(mainConfigFile);
	}

	/*
	 * Start the migration process
	 */
	private void start() {
		log0(" ");
		log0(" ");
		log0("Starting migration from ChatControl 8 to ChatControl 10 on " + TimeUtil.getFormattedDate());
		log0(" ");

		new SettingsYmlRemapper().start();
		new RulesRemapper().start();
		new VariablesRemapper().start();
		new ChannelsRemapper().start();
		new FormattingRemapper().start();
		new HandlersRemapper().start();
		new MessagesRemapper().start();

		this.saveSettings();
	}

	/*
	 * Log a new migrate section
	 */
	private void logSection(String message) {
		this.log0(": Migrating " + message);
	}

	/*
	 * Log the message with the given prefix
	 */
	private void logPart(String type, String message) {
		this.log0(type, message);
	}

	/*
	 * Log the message to console and migration.log file.
	 */
	private void log0(String... messages) {
		String message = "";

		for (int i = 0; i < messages.length; i++)
			message += String.format("%-20s", messages[i]);

		// Hide sensitive information
		if (message.contains("MySQL.Password"))
			message = "Migrated mysql password but not showing here for privacy.";

		FileUtil.write("migration.log", message);
		Common.logNoPrefix(message);
	}

	/*
	 * Save settings.yml
	 */
	private void saveSettings() {
		try {
			mainConfig.options().copyHeader(true);
			mainConfig.options().header("Please restart your server for comments to be applied automatically.");

			mainConfig.save(mainConfigFile);

		} catch (final IOException ex) {
			ex.printStackTrace();

			logPart("ERROR", "Failed to save: " + mainConfig.getName());
		}
	}

	/**
	 * Attempts to migrate ChatControl 8 configuration
	 * and logs the results.
	 */
	public static void migrate() {
		instance.start();
	}

	/*
	 * Migrates settings.yml from CHC8 to CHC10
	 */
	private final class SettingsYmlRemapper {

		/**
		 * The old settings config
		 */
		private final FileConfiguration oldSettings;

		/**
		 * Temporary path prefix
		 */
		@Setter
		private String oldPathPrefix = null;

		/*
		 * Create a new instance of this class
		 */
		private SettingsYmlRemapper() {
			final File file = new File(oldDataFolder, "settings.yml");

			this.oldSettings = file.exists() ? FileUtil.loadConfigurationStrict(file) : null;
		}

		/*
		 * Migrates settings.yml from CHC8 to CHC10
		 */
		private void start() {

			// settings.yml does not exist
			if (this.oldSettings == null)
				return;

			logSection("settings.yml");

			setOldPathPrefix("Anti_Bot");

			remap("Cooldown.Rejoin", "Anti_Bot.Cooldown.Rejoin", oldValue -> oldValue + " seconds");
			remap("Cooldown.Chat_After_Login", "Anti_Bot.Cooldown.Chat_After_Login", oldValue -> oldValue + " seconds");
			remap("Cooldown.Command_After_Login", "Anti_Bot.Cooldown.Command_After_Login", oldValue -> oldValue + " seconds");
			remap("Block_Chat_Until_Moved");
			remap("Block_Commands_Until_Moved");
			remap("Show_Quit_Message_Only_If_Logged", "Integration.AuthMe.Hide_Quit_Message_If_Not_Logged");
			remap("Block_Signs_With_Same_Text", "Anti_Bot.Block_Same_Text_Signs");
			remap("Disallowed_Nicknames", "Anti_Bot.Disallowed_Usernames");

			setOldPathPrefix("Anti_Caps");

			final String capsPointsAmount = oldSettings.getString("Anti_Bot.Points.Amount");

			remap("Enabled");
			remap("Commands_To_Apply", "Anti_Caps.Enabled_In_Commands");

			if (capsPointsAmount != null)
				remap("Points.Warn_Set", "Warning_Points.Triggers.Caps", oldValue -> oldValue + " " + capsPointsAmount.replace("{capsPercentage}", "{caps_percentage_double}"));

			remap("Min_Message_Length");
			remap("Min_Caps_Percentage");
			remap("Min_Caps_In_A_Row");
			remap("Whitelist");

			setOldPathPrefix("Anti_Spam.Commands");

			final String commandDelayPointsAmount = oldSettings.getString("Anti_Spam.Commands.Delay_Points.Amount");
			final String commandSimilarityPointsAmount = oldSettings.getString("Anti_Spam.Commands.Similarity_Points.Amount");

			remap("Command_Delay", "Anti_Spam.Commands.Delay", oldValue -> oldValue + " seconds");
			remap("Limit.Period");
			remap("Limit.Max_Commands");
			remap("Delay_Points.Warn_Set", "Warning_Points.Triggers.Command_Delay", oldValue -> oldValue + " " + commandDelayPointsAmount.replace("{delay}", "{remaining_time}"));
			remap("Similar_Percentage_Block", "Anti_Spam.Commands.Similarity", oldValue -> oldValue + "%");
			remap("Similarity_Points.Warn_Set", "Warning_Points.Triggers.Command_Similarity", oldValue -> oldValue + " " + commandSimilarityPointsAmount.replace("{similarityPercentage}", "{similarity_percentage_double}"));
			remap("Whitelist_Similarity");
			remap("Whitelist_Delay");

			setOldPathPrefix("Anti_Spam.Chat");

			final String chatLimitPointsAmount = String.valueOf(oldSettings.get("Anti_Spam.Chat.Limit.Points.Amount"));
			final String chatDelayPointsAmount = String.valueOf(oldSettings.get("Anti_Spam.Chat.Delay_Points.Amount"));
			final String chatSimilarityPointsAmount = String.valueOf(oldSettings.get("Anti_Spam.Chat.Similarity_Points.Amount"));

			remap("Message_Delay", "Anti_Spam.Chat.Delay", oldValue -> oldValue + " seconds");
			remap("Limit.Period");
			remap("Limit.Max_Messages");
			remap("Limit.Points.Warn_Set", "Warning_Points.Triggers.Chat_Limit", oldValue -> oldValue + " " + chatLimitPointsAmount);
			remap("Delay_Points.Warn_Set", "Warning_Points.Triggers.Chat_Delay", oldValue -> oldValue + " " + chatDelayPointsAmount.replace("{amount}", "{remaining_time}").replace("{delay}", "{remaining_time}"));
			remap("Similar_Percentage_Block", "Anti_Spam.Chat.Similarity", oldValue -> oldValue + "%");
			remap("Similarity_Message_Check_Count", "Anti_Spam.Chat.Similarity_Past_Messages");
			remap("Similarity_Points.Warn_Set", "Warning_Points.Triggers.Chat_Similarity", oldValue -> oldValue + " " + chatSimilarityPointsAmount.replace("{similarityPercentage}", "{similarity_percentage_double}"));
			remap("Whitelist_Similarity");
			remap("Whitelist_Delay");

			setOldPathPrefix("Grammar");

			remap("Insert_Dot.Min_Message_Length", "Grammar.Insert_Dot_Message_Length");
			remap("Capitalize.Min_Message_Length", "Grammar.Capitalize_Message_Length");

			setOldPathPrefix("Mute");

			remap("Prevent.Writing_Books", "Mute.Prevent_Writing_Books");
			remap("Prevent.Placing_Signs", "Mute.Prevent_Placing_Signs");
			remap("Silence.Join_Messages", "Mute.Hide_Join_Messages");
			remap("Silence.Quit_Messages", "Mute.Hide_Quit_Messages");
			remap("Silence.Death_Messages", "Mute.Hide_Death_Messages");
			remap("Disabled_Commands", "Mute.Prevent_Commands");

			setOldPathPrefix("Newcomer");

			remap("Threshold");
			remap("Worlds");
			remap("Warn_Points_Multiplier", "Newcomer.Warning_Points_Multiplier");
			remap("Restrict_Chat.Enabled");
			remap("Restrict_Chat.Whitelist");
			remap("Restrict_Seeing_Chat.Enabled", "Newcomer.Restrict_Seeing_Chat");
			remap("Restrict_Commands.Enabled");
			remap("Restrict_Commands.Whitelist");

			setOldPathPrefix("Newcomer");

			remap("Sound", "Announcer.Chat_Sound");

			setOldPathPrefix("Private_Messages");

			remap("Enabled");
			remap("Toast.Enabled", "Private_Messages.Toasts");
			remap("Reply_Change_Timeout", "Private_Messages.Reply_Change_Threshold");
			remap("Aliases.Tell", "Private_Messages.Tell_Aliases");
			remap("Aliases.Reply", "Private_Messages.Reply_Aliases");
			remap("Format_Sender");
			remap("Format_Receiver");

			setOldPathPrefix("Ignore");

			remap("Enabled");
			remap("Aliases", "Ignore.Command_Aliases");
			remap("Chat", "Ignore.Hide_Chat");
			remap("Private_Messages", "Ignore.Stop_Private_Messages");

			setOldPathPrefix("Me");

			remap("Enabled");
			remap("Aliases", "Me.Command_Aliases");

			setOldPathPrefix("Packets");

			remap("Replace_Unicode_In_Books", "Integration.ProtocolLib.Book_Anti_Crash");

			setOldPathPrefix("Rules");

			remap("Verbose");

			final Set<String> applyOn = new HashSet<>();

			if (oldSettings.getBoolean("Rules.Chat"))
				applyOn.add("chat");

			if (oldSettings.getBoolean("Rules.Commands"))
				applyOn.add("command");

			if (oldSettings.getBoolean("Rules.Packets"))
				applyOn.add("packet");

			if (oldSettings.getBoolean("Rules.Signs.Check"))
				applyOn.add("sign");

			if (oldSettings.getBoolean("Rules.Books.Check"))
				applyOn.add("book");

			if (oldSettings.getBoolean("Rules.Anvil.Check"))
				applyOn.add("anvil");

			applyOn.add("tag");

			setOldPathPrefix("Console_Filter");

			remap("Enabled");
			remap("Filter_Console_Messages", "Console_Filter.Messages");

			setOldPathPrefix("Sound_Notify");

			remap("Enabled");
			remap("Cooldown");
			remap("Require_Afk");
			remap("Require_Prefix");
			remap("Sound");
			remap("Color");

			setOldPathPrefix("Groups");

			logPart("ACTION REQUIRED", "Groups were not migrated, because key names changed, see: https://github.com/kangarko/ChatControl-Pro/wiki/Groups");

			setOldPathPrefix("Points");

			remap("Enabled", "Warning_Points.Enabled");
			remap("Reset_Task.Repeat_Every_Seconds", "Warning_Points.Reset_Task.Period", oldValue -> oldValue + " seconds");
			remap("Reset_Task.Remove", "Warning_Points.Reset_Task.Remove");
			remap("Warn_Sets", "Warning_Points.Sets");

			setOldPathPrefix("Motd");

			remap("Enabled");
			remap("Command_Aliases");
			remap("Sound");
			toFormat("migrated-motd", "Motd.Format_Motd", oldSettings.getStringList("Motd.Message"));
			toFormat("migrated-motd-first-time", "Motd.Format_Motd_First_Time", oldSettings.getStringList("Motd.Message_First_Time"));
			toFormat("migrated-motd-newcomer", "Motd.Format_Motd_Newcomer", oldSettings.getStringList("Motd.Message_Newcomer"));

			setOldPathPrefix("Color_Menu");

			logPart("ACTION REQUIRED", "Color_Menu was not migrated, you can now change this in your localization/messages_" + SimpleSettings.LOCALE_PREFIX + ".yml file.");

			setOldPathPrefix("Spy");

			remap("Command_Aliases");
			remap("Command_List", "Spy.Commands");

			logPart("ACTION REQUIRED", "Spy section was partially not migrated, because our system changed.");

			setOldPathPrefix("BungeeCord");

			if (oldSettings.getBoolean("BungeeCord.Enabled"))
				logPart("ACTION REQUIRED", "BungeeCord was not enabled. Ensure you have MySQL and update BungeeControl,"
						+ " then enable it manualy in the Integration section of settings.yml");

			remap("Prefix", "Integration.BungeeCord.Prefix");

			setOldPathPrefix("MySQL");

			remap("Enabled");

			final String mysqlPort = String.valueOf(oldSettings.get("MySQL.Connection.Port"));

			remap("Connection.Host", "MySQL.Host", oldValue -> oldValue + ":" + mysqlPort);
			remap("Connection.Database", "MySQL.Database");
			remap("Connection.User", "MySQL.User");
			remap("Connection.Password", "MySQL.Password");

			logPart("ACTION REQUIRED", "Your MySQL settings were migrated, but we will create 3 new tables instead of using the old one. You can remove your old table.");

			setOldPathPrefix(null);

			remap("Listener_Priority.Formatter", "Chat_Listener_Priority");
			remap("Prefix");
			remap("Command_Aliases");
			remap("Translate_Diacritical_Marks", "Rules.Strip_Accents");
			remap("Clear_Data_If_Inactive");
			remap("Regex_Timeout_Milis", "Rules.Regex_Timeout_Ms");
			remap("Regex_Case_Insensitive", "Rules.Case_Insensitive");
			remap("Log_Lag_Over_Milis");
			remap("Silent_Startup");
		}

		/*
		 * This will remap two config sections between 8. and 10. generation settings.yml
		 * given their value datatype hasn't changed and ther location hasn't changed
		 */
		private void remap(String oldPath) {
			final String newPath = (oldPathPrefix != null ? oldPathPrefix + "." : "") + oldPath;

			this.remap(oldPath, newPath);
		}

		/*
		 * This will remap two config sections between 8. and 10. generation settings.yml
		 * given their value datatype hasn't changed
		 */
		private void remap(String oldPath, String newPath) {
			this.remap(oldPath, newPath, oldValue -> oldValue);
		}

		/*
		 * This will remap two config sections between 8. and 10. generation settings.yml
		 */
		private void remap(String oldPath, String newPath, Function<Object, Object> converter) {

			oldPath = (oldPathPrefix != null ? oldPathPrefix + "." : "") + oldPath;

			// Only apply when old settings exist
			if (oldSettings.isSet(oldPath)) {

				// Convert old values because sometimes their format is different
				final Object oldValue = oldSettings.get(oldPath);
				final Object remapped = converter.apply(oldValue);

				logPart("OK", "Remapped '" + oldPath + "' -> '" + newPath + "': '" + remapped + "' ");

				mainConfig.set(newPath, remapped);

			} else
				logPart("ACTION REQUIRED", "Skipping '" + oldPath + "' path because old settings did not have it. You'll now find this at '" + newPath + "' in new settings.");
		}

		/*
		 * Create a format in formats/ from settings.yml messages list
		 */
		private void toFormat(String formatName, String newSettingsPath, List<String> messages) {
			final File file = FileUtil.getOrMakeFile("formats/" + formatName + ".yml");
			final FileConfiguration config = FileUtil.loadConfigurationStrict(file);

			// Add basic documentation
			config.options().copyHeader(true);
			config.options().header(
					"This format has been imported from ChatControl 8\n" +
							"using '/chc migrate chc8 command' on " + TimeUtil.getFormattedDate() + "\n" +
							"\n" +
							"Please see chat.yml file for documentation");

			// Copy the key
			config.set("Parts.Migrated.Message", messages);

			mainConfig.set(newSettingsPath, formatName);

			try {
				config.save(file);
			} catch (final IOException e) {
				e.printStackTrace();

				logPart("ERROR", "Failed creating format " + formatName + " from " + messages);
			}
		}
	}

	@NoArgsConstructor
	private final class RulesRemapper {

		private void start() {
			final SerializedMap ruleFiles = SerializedMap.ofArray(
					"books", "book",
					"chat", "chat",
					"commands", "command",
					"items", "anvil",
					"rules", "global",
					"packets", "packet",
					"sign", "sign");

			for (final Entry<String, Object> entry : ruleFiles.entrySet()) {

				final String oldName = entry.getKey();
				final String newName = entry.getValue().toString();

				final File oldFile = new File(oldDataFolder, "rules/" + oldName + ".txt");

				if (!oldFile.exists())
					continue;

				final File newFile = FileUtil.getOrMakeFile("rules/" + newName + ".rs");

				final List<String> newLines = new ArrayList<>();

				logSection(oldFile.toPath().toString());

				// Add header
				newLines.add("# This file was automatically migrated from ChatControl 8 on " + TimeUtil.getFormattedDate());
				newLines.add("# It is highly advised to review it manually and ensure everything works correctly!");
				newLines.add("# For documentation, see https://github.com/kangarko/ChatControl-Red/wiki/Rules");

				// Automatically import global rules like it was default previously
				if (!oldName.contains("packets") && !oldName.contains("rules")) {
					newLines.add("");
					newLines.add("# Include rules from global.rs automatically.");
					newLines.add("@import global");
				}

				String lastMatch = "unknown";

				// Write lines one by one
				for (String line : FileUtil.readLines(oldFile)) {

					// Do not migrate comments
					if (line.startsWith("#") || line.trim().isEmpty())
						continue;

					if (line.startsWith("match ")) {
						newLines.add("");
						lastMatch = line.replace("match ", "");
					}

					if (line.startsWith("before strip ")) {
						line = line.replace("before strip ", "before replace ");

						logPart("WARNING", "Rule " + lastMatch + " had 'before strip' operator, changing to 'before replace'");
					}

					if (line.startsWith("id ")) {
						line = line.replace("id ", "name ");

						logPart("WARNING", "Rule " + lastMatch + " had 'id' operator, changing to 'name'");
					}

					if (line.startsWith("ignore event") && line.contains("item")) {
						line = line.replace("item", "anvil");
						line = line.replace("commands", "command");
						line = line.replace("packets", "packet");
						line = line.replace("signs", "sign");
						line = line.replace("books", "book");

						logPart("WARNING", "Rule " + lastMatch + " had 'ignore event' operator with 'item', changing to 'anvil'");
					}

					if ((line.startsWith("ignore type") || line.startsWith("ignore event")) && !oldName.equals("rules")) {
						logPart("WARNING", "Found 'ignore type' which can only be used for global rules. It will be ignored for rule: " + lastMatch);

						continue;
					}

					if (line.startsWith("handle as ")) {
						line = line.replace("handle as ", "group ");

						logPart("WARNING", "Rule " + lastMatch + " had 'handle as' operator, changing to 'group'");
					}

					if (line.startsWith("then points"))
						logPart("ACTION REQUIRED", "From now on, you must specify warning set in 'then points' for line'" + line + " in " + newFile + "', ENSURE IT IS SET otherwise plugin will crash");

					// Removed
					if (line.startsWith("ignore chatdisplay") || line.startsWith("ignore usernames"))
						continue;

					// Migrate otherwise
					newLines.add(line);
				}

				// Override previous content in the new file
				FileUtil.write(newFile, newLines, StandardOpenOption.TRUNCATE_EXISTING);
			}
		}
	}

	/**
	 * Helps migrate variables/ folder
	 */
	@NoArgsConstructor
	private final class VariablesRemapper {

		/*
		 * Start migrating
		 */
		private void start() {
			final File folder = new File(oldDataFolder, "variables");

			if (!folder.exists())
				return;

			logSection("variables/ folder");

			for (final File variablesFile : folder.listFiles()) {
				if (variablesFile.getName().endsWith(".yml")) {
					logPart("OK", "Migrating variable " + variablesFile);

					final FileConfiguration config = FileUtil.loadConfigurationStrict(variablesFile);

					// Notify about type and save header
					if (!config.isSet("Type"))
						logPart("ACTION REQUIRED", "This variable lacks the 'Type' key. We try to determine if it's a FORMAT or MESSAGE variable but"
								+ " it may malfunction, please set the appropriate type yourself from reading"
								+ " https://github.com/kangarko/ChatControl-Red/wiki/JavaScript-Variables");

					config.options().copyHeader(true);
					config.options().header("Imported automatically, restart your server for this file to be updated and commented.");

					// Move the file with header edited
					try {
						config.save(FileUtil.getFile("variables/" + variablesFile.getName()));

					} catch (final IOException e) {
						e.printStackTrace();

						logPart("ERROR", "Failed saving variable file: " + variablesFile.toPath());
					}
				}
			}
		}
	}

	/**
	 * Helps migrate channels.yml file
	 */
	@NoArgsConstructor
	private final class ChannelsRemapper {

		/*
		 * Start migrating
		 */
		private void start() {
			final File channelsFile = new File(oldDataFolder, "channels.yml");
			final FileConfiguration channelsConfig = channelsFile.exists() ? FileUtil.loadConfigurationStrict(channelsFile) : null;

			// Return if file doesn't exist
			if (channelsConfig == null)
				return;

			logSection("channels.yml file (only old channel options Format, Console_Format, Discord_Format and Range are migrated)");

			mainConfig.set("Channels.Max_Read_Channels", channelsConfig.getInt("Channel_Limits.Read", 3));
			mainConfig.set("Channels.Format_Discord", channelsConfig.getString("Compatibility.Discord.Format"));

			// Remove all new channels
			mainConfig.set("Channels.List", null);

			// Iterate through all channels
			for (final Map.Entry<String, Object> entry : channelsConfig.getConfigurationSection("Channels").getValues(false).entrySet()) {
				final String name = entry.getKey();
				final SerializedMap oldOptions = SerializedMap.of(entry.getValue());

				final SerializedMap newOptions = new SerializedMap();

				logPart("OK", "Partially migrating '" + name + "' options: " + oldOptions);

				newOptions.put("Format", oldOptions.getString("Format"));
				newOptions.putIf("Format_Console", oldOptions.getString("Console_Format"));
				newOptions.putIf("Format_Discord", oldOptions.getString("Discord_Format"));
				newOptions.putIf("Range", oldOptions.getString("Range"));
				newOptions.putIf("Bungee", oldOptions.getObject("Bungee"));

				if (oldOptions.containsKey("Party"))
					logPart("ACTION REQUIRED", "Channel " + name + " had Party key, which now contains significantly altered keys, please migrate"
							+ " this manually according to https://github.com/kangarko/ChatControl-Red/wiki/Channels#party");

				// Write to settings.yml -> Channels.List
				mainConfig.set("Channels.List." + name, newOptions.serialize());
			}

			logPart("ACTION REQUIRED", "Channels are now the only way to format chat in ChatControl."
					+ " They are disabled by default, you can enable them in Channels.Enabled key in settings.yml" +
					" Lots of their configuration has changed, see https://github.com/kangarko/ChatControl-Red/wiki/Channels");
		}
	}

	/**
	 * Helps migrate formatting.yml file
	 */
	@NoArgsConstructor
	private final class FormattingRemapper {

		/*
		 * Start migrating
		 */
		private void start() {
			final File file = new File(oldDataFolder, "formatting.yml");
			final FileConfiguration config = file.exists() ? FileUtil.loadConfigurationStrict(file) : null;

			// Return if file doesn't exist
			if (config == null)
				return;

			logSection("formatting.yml file -- IMPORTANT: We no longer use non-channel formatting, that means you must have channels enabled to format your chat.");

			// This is the only option we support migrating
			mainConfig.set("Me.Format", config.getString("Formatting.Me"));

			// Iterate through all formats
			for (final Map.Entry<String, Object> entry : config.getConfigurationSection("Formats").getValues(false).entrySet()) {

				final String formatName = entry.getKey();
				final SerializedMap formatParts = SerializedMap.of(entry.getValue());

				final File formatFile = FileUtil.getOrMakeFile("formats/" + formatName + ".yml");
				final FileConfiguration formatConfig = FileUtil.loadConfigurationStrict(formatFile);

				// Clear previous format
				formatConfig.set("Parts", null);

				logPart("OK", "Migrating format " + formatName);

				for (final Map.Entry<String, Object> optionsEntry : formatParts.asMap().entrySet()) {

					final String partName = optionsEntry.getKey();
					final String configPrefix = "Formats." + formatName + "." + partName;

					// The settings dumped to new format file
					final SerializedMap newOptions = new SerializedMap();

					// Get supported old values
					final String message = config.getString(configPrefix + ".Message");
					final String hoverAction = config.getString(configPrefix + ".Hover_Event.Action", "").toUpperCase();
					final List<String> hoverText = config.getStringList(configPrefix + ".Hover_Event.Values");
					final String clickAction = config.getString(configPrefix + ".Click_Event.Action", "").toUpperCase();
					final String clickText = config.getString(configPrefix + ".Click_Event.Value");
					final String senderPermission = config.getString(configPrefix + ".Sender_Permission");
					final String condition = config.getString(configPrefix + ".Condition");

					// Map them
					newOptions.put("Message", message);

					if ("SHOW_TEXT".equals(hoverAction))
						newOptions.putIf("Hover", hoverText);

					if ("OPEN_URL".equals(clickAction))
						newOptions.putIf("Open_Url", clickText);

					else if ("RUN_COMMAND".equals(clickAction))
						newOptions.putIf("Run_Command", clickText);

					else if ("SUGGEST_COMMAND".equals(clickAction))
						newOptions.putIf("Suggest_Command", clickText);

					newOptions.putIf("Sender_Permission", senderPermission);
					newOptions.putIf("Sender_Condition", condition);

					//logPart("OK", "\tMigrated part '" + partName + "' -> " + newOptions);

					// Save it
					formatConfig.set("Parts." + partName, newOptions.serialize());
				}

				formatConfig.options().copyHeader(true);
				formatConfig.options().header("Automatically migrated on " + TimeUtil.getFormattedDate() + "\n" +
						"Restart your server to see comments.");

				// Special: Override old format if having the same name
				try {
					formatConfig.save(formatFile);

				} catch (final IOException ex) {
					ex.printStackTrace();

					logPart("ERROR", "Failed to migrate format " + formatName + " with options " + formatParts);
				}
			}
		}
	}

	/**
	 * Helps migrate handlers.yml file
	 */
	@NoArgsConstructor
	private final class HandlersRemapper {

		/**
		 * The groups config
		 */
		private FileConfiguration groupsConfig;

		/**
		 * The current evaluated handler
		 */
		private String handlerName;

		/**
		 * The lines of groups.rs
		 */
		private List<String> lines;

		/*
		 * Start migrating
		 */
		private void start() {
			final File file = new File(oldDataFolder, "handlers.yml");
			this.groupsConfig = file.exists() ? FileUtil.loadConfigurationStrict(file) : null;

			// handlers.yml does not exist
			if (this.groupsConfig == null)
				return;

			logSection("handlers.yml file (now in rules/groups.rs and using rules syntax)");

			// We simply write new groups file line by line
			this.lines = new ArrayList<>();

			lines.add("# Handlers automatically migrated to groups from ChatControl 8 on " + TimeUtil.getFormattedDate());
			lines.add("# See https://github.com/kangarko/ChatControl-Red/wiki/Rules for documentation and help.");

			for (final String name : groupsConfig.getKeys(false)) {
				this.handlerName = name;

				logPart("OK", "Migrating handler " + name);

				lines.add("");
				lines.add("group " + name);

				migrateString("Bypass_With_Permission", "ignore perm");
				migrateStringList("Ignore_Commands", "ignore string");
				migrateStringList("Ignore_Worlds", "ignore world");
				migrateStringList("Require_Worlds", "require world");
				migrateStringList("Ignore_Channels", "ignore channel");

				migrateString("Player_Warn_Message", "then warn");
				migrateString("Broadcast_Message", "then console say");
				migrateString("Staff_Alert", "then notify");
				migrateString("Console_Message", "then log");

				if (groupsConfig.isSet(name + ".Write_To_File"))
					logPart("WARNING", "Option 'Write_To_File' for handler " + name + " is no longer supported, we now save it by default to the log file or database.");

				migrateBoolean("Block_Message", "then deny");
				migrateString("Fine", "then fine");

				if (groupsConfig.isSet(name + ".Warn_Points"))
					logPart("ACTION REQUIRED", "From now on, you must specify warning set in 'then points' for handler '" + name + "', ENSURE IT IS SET and uncomment the operator");

				migrateString("Warn_Points", "#then points");
				migrateString("Replace_Word", "then replace");
				migrateString("Rewrite_To", "then rewrite");
				migrateString("Sound", "then sound");
				migrateStringList("Execute_Commands", "then console");
				migrateStringList("Execute_Player_Commands", "then command");
				migrateStringList("Execute_Bungee_Commands", "then bungeeconsole");

				if (groupsConfig.isSet(name + ".Only_In_Commands"))
					logPart("WARNING", "Handler '" + name + "' had Only_In_Commands which is no longer supported");

			}

			FileUtil.write(FileUtil.getOrMakeFile("rules/groups.rs"), lines, StandardOpenOption.TRUNCATE_EXISTING);
		}

		/*
		 * Migrate boolean from handlers.yml to rules/groups.rs
		 */
		private void migrateBoolean(String oldPath, String newValue) {
			final boolean value = groupsConfig.getBoolean(this.handlerName + "." + oldPath, false);

			if (value)
				this.lines.add(newValue);
		}

		/*
		 * Migrate string from handlers.yml to rules/groups.rs
		 */
		private void migrateString(String oldPath, String newPath) {
			final String value = groupsConfig.getString(this.handlerName + "." + oldPath);

			if (value != null)
				this.lines.add(newPath + " " + value);
		}

		/*
		 * Migrate string list from handlers.yml to rules/groups.rs
		 */
		private void migrateStringList(String oldPath, String newPath) {
			final List<String> values = groupsConfig.getStringList(this.handlerName + "." + oldPath);

			if (values != null)
				for (final String line : values)
					this.lines.add(newPath + " " + line);
		}
	}

	/**
	 * Helps migrate messages.yml file
	 */
	@NoArgsConstructor
	private final class MessagesRemapper {

		private File messagesFile;

		private FileConfiguration messagesConfig;

		/*
		 * Start migrating
		 */
		private void start() {
			this.messagesFile = new File(oldDataFolder, "messages.yml");

			// Skip if non existent
			if (!this.messagesFile.exists())
				return;

			logSection("messages.yml -- now split into messages/ folder files");

			this.messagesConfig = FileUtil.loadConfigurationStrict(messagesFile);

			this.migrateTimed();
			this.migratePlayerMessage("Join_Message", PlayerMessage.Type.JOIN);
			this.migratePlayerMessage("Kick_Message", PlayerMessage.Type.KICK);
			this.migratePlayerMessage("Quit_Message", PlayerMessage.Type.QUIT);
			this.migrateDeathMessage();
		}

		private void migrateTimed() {

			logPart("OK", "Migrating timed messages");

			// Migrate global enable flag
			this.writeEnabled("Broadcaster", PlayerMessage.Type.TIMED);

			// Write file header
			writeHeader("timed.rs");

			// Write delay
			final String delay = String.valueOf(messagesConfig.get("Broadcaster.Delay_Seconds")) + " seconds";
			mainConfig.set("Messages.Timed_Delay", delay);

			// Load timed messages
			final String prefix = messagesConfig.getString("Broadcaster.Prefix");
			final Map<String, List<String>> timedMessages = new LinkedHashMap<>();

			for (final Entry<String, Object> entry : messagesConfig.getConfigurationSection("Broadcaster.Messages").getValues(false).entrySet()) {
				final List<String> newList = new ArrayList<>();
				final List<?> keys = entry.getValue() instanceof List ? (List<?>) entry.getValue() : Arrays.asList(entry.getValue().toString());

				for (final Object oldListValue : keys)
					newList.add(oldListValue.toString());

				timedMessages.put(entry.getKey(), newList);
			}

			// Use CHC8 logic here to include/exclude keys
			final List<String> global = timedMessages.get("global");

			for (final String world : timedMessages.keySet()) {
				final List<String> worldMessages = timedMessages.get(world);

				if (worldMessages.size() == 0 || world.equalsIgnoreCase("global"))
					continue;

				final String firstArgument = worldMessages.get(0);

				if (firstArgument == null)
					continue;

				if (firstArgument.startsWith("includeFrom ")) {
					worldMessages.remove(0);

					final List<String> worldToInclude = timedMessages.get(firstArgument.replace("includeFrom ", ""));

					if (worldToInclude != null && !worldToInclude.isEmpty())
						worldMessages.addAll(worldToInclude);
				}

				if (firstArgument.equalsIgnoreCase("excludeGlobal")) {
					worldMessages.remove(0);

					continue;
				}

				if (global != null && !global.isEmpty())
					worldMessages.addAll(global);
			}

			// Finally, write to file
			final List<String> lines = new ArrayList<>();
			int count = 1;

			for (final Map.Entry<String, List<String>> entry : timedMessages.entrySet()) {
				final String world = entry.getKey();
				final List<String> messages = entry.getValue();

				logPart("OK", "\t-> part " + world);

				lines.add("");
				lines.add("group imported-" + count++);
				lines.add("prefix " + prefix);

				if (!"global".equals(world))
					lines.add("require receiver world " + world);

				lines.add("messages:");

				// Prevent repetitive lines
				final Set<String> written = new HashSet<>();

				if (messages.isEmpty())
					lines.add("- none");
				else
					for (String message : messages) {
						message = "- " + (message.isEmpty() ? "none" : message.replace("\n", "\n  "));

						if (!written.contains(message)) {
							lines.add(message);

							written.add(message);
						}
					}
			}

			FileUtil.write(FileUtil.getOrMakeFile("messages/timed.rs"), lines, StandardOpenOption.APPEND);
		}

		private void migratePlayerMessage(String path, PlayerMessage.Type type) {
			final String fileName = type.getKey() + ".rs";

			logPart("OK", "Migrating " + fileName);

			// Write enabled option
			this.writeEnabled(path, type);

			// Write file header
			this.writeHeader(fileName);

			// Migrate messages
			this.writeMessage(path + ".Conditions", fileName);

			// Write default
			this.writeDefaultMessage(path, fileName);
		}

		private void migrateDeathMessage() {
			final String fileName = "death.rs";

			logPart("OK", "Migrating " + fileName);

			// Migrate global enable flag
			this.writeEnabled("Death_Messages", PlayerMessage.Type.DEATH);

			// Write file header
			this.writeHeader(fileName);

			// And default conditions
			this.writeMessage("Death_Messages.Default.Conditions", fileName);

			// Add messages for different death causes
			for (final String causeName : messagesConfig.getConfigurationSection("Death_Messages").getKeys(false)) {
				final SerializedMap conditions = SerializedMap.of(messagesConfig.getConfigurationSection("Death_Messages." + causeName));
				DamageCause cause;

				try {
					cause = ReflectionUtil.lookupEnum(DamageCause.class, causeName);
				} catch (final Throwable t) {
					continue;
				}

				final String causeNamePretty = cause.toString().toLowerCase().replace("_", "-");

				// Write sub clauses
				if (messagesConfig.isSet("Death_Messages." + causeName + ".Conditions")) {
					final Map<String, Object> subconditions = messagesConfig.getConfigurationSection("Death_Messages." + causeName + ".Conditions").getValues(false);

					if (!subconditions.isEmpty()) {
						for (final Entry<String, Object> entry : subconditions.entrySet()) {
							final List<String> sublines = new ArrayList<>();

							final String subconditionName = entry.getKey();
							final SerializedMap subcondition = SerializedMap.of(entry.getValue());

							this.createGroupAndLoadConditions(sublines, causeNamePretty + "-by-" + subconditionName.replace("_", "-"), subcondition);

							// Add require clause manually
							sublines.add(2, "require cause " + cause.name());

							FileUtil.write(FileUtil.getOrMakeFile("messages/" + fileName), sublines, StandardOpenOption.APPEND);
						}
					}
				}

				// Write default clause
				final List<String> lines = new ArrayList<>();

				// Load conditions and create group
				this.createGroupAndLoadConditions(lines, causeNamePretty, conditions);

				// Add require clause manually
				lines.add(2, "require cause " + cause.name());

				FileUtil.write(FileUtil.getOrMakeFile("messages/" + fileName), lines, StandardOpenOption.APPEND);
			}

			// And add default message itself
			this.writeDefaultMessage("Death_Messages.Default", fileName);
		}

		private void writeEnabled(String path, PlayerMessage.Type type) {
			final String key = type.getKey();
			final boolean enabled = this.messagesConfig.getBoolean(path + ".Enabled");

			final List<String> applyOnList = mainConfig.getStringList("Messages.Apply_On");
			final Set<String> applyOn = new HashSet<>();

			// Deduplicate and lower case
			if (applyOnList != null)
				for (final String item : applyOnList)
					applyOn.add(item.toLowerCase());

			final boolean stored = applyOn.contains(key);

			if (enabled && !stored)
				applyOn.add(key);

			else if (!enabled && stored)
				applyOn.remove(key);

			mainConfig.set("Messages.Apply_On", new ArrayList<>(applyOn)); // save as list again
		}

		private void writeHeader(String fileName) {
			final List<String> lines = new ArrayList<>();

			lines.add("# Messages automatically migrated from ChatControl 8 to rules syntax on " + TimeUtil.getFormattedDate());
			lines.add("# See https://github.com/kangarko/ChatControl-Red/wiki/Messages for documentation and help.");

			FileUtil.write(FileUtil.getOrMakeFile("messages/" + fileName), lines, StandardOpenOption.TRUNCATE_EXISTING);
		}

		private void writeDefaultMessage(String path, String fileName) {
			final List<String> lines = new ArrayList<>();

			final SerializedMap conditions = SerializedMap.of(messagesConfig.getConfigurationSection(path));
			final List<String> messages = conditions.getStringList("Message");

			if (!messages.isEmpty() && (messages.size() > 1 || !messages.get(0).equals("default")))
				this.createGroupAndLoadConditions(lines, "default", conditions);

			FileUtil.write(FileUtil.getOrMakeFile("messages/" + fileName), lines, StandardOpenOption.APPEND);
		}

		private void writeMessage(String path, String fileName) {
			final List<String> lines = new ArrayList<>();

			if (!messagesConfig.isSet(path))
				return;

			for (final Entry<String, Object> entry : messagesConfig.getConfigurationSection(path).getValues(false).entrySet()) {
				final String conditionName = entry.getKey();
				final SerializedMap conditions = SerializedMap.of(entry.getValue());

				this.createGroupAndLoadConditions(lines, conditionName, conditions);
			}

			FileUtil.write(FileUtil.getOrMakeFile("messages/" + fileName), lines, StandardOpenOption.APPEND);
		}

		private void createGroupAndLoadConditions(List<String> lines, String conditionName, SerializedMap conditions) {

			logPart("OK", "\t-> group " + conditionName);

			lines.add("");
			lines.add("group " + conditionName);

			if (conditions.containsKey("Permission"))
				lines.add("require sender perm " + conditions.getString("Permission"));

			if (conditions.containsKey("Gamemode"))
				lines.add("require sender gamemode " + conditions.getString("Gamemode"));

			if (conditions.containsKey("Condition"))
				lines.add("require sender script " + conditions.getString("Condition"));

			if (conditions.containsKey("Killer")) {
				final String killerName = conditions.getString("Killer");
				EntityType type;

				try {
					type = ReflectionUtil.lookupEnum(EntityType.class, killerName);

					lines.add("require killer " + type.name());

				} catch (final Throwable t) {
					logPart("ERROR", "\t\tYour MC version is missing " + killerName + ", we will disable this message");

					lines.add("#require killer " + killerName);
					lines.add("disabled");
				}

			}

			if (conditions.containsKey("Killer_Item"))
				lines.add("require killer item " + conditions.getString("Killer_Item"));

			if (conditions.containsKey("Bungee_Message")) {
				lines.add("bungee");

				logPart("WARNING", "\t\tMessage 'Bungee_Message' option is no longer available for " + conditionName + ", we now use 'bungee' operator that will send"
						+ " your default message. We added this to your file but your bungee_message was not migrated.");
			}

			if (conditions.containsKey("Display_To")) {
				if (conditions.getString("Display_To").equalsIgnoreCase("PLAYER"))
					lines.add("require self");

				else
					logPart("ACTION REQUIRED", "\t\tMessage 'Display_To' option is no longer available for " + conditionName);
			}

			if (conditions.containsKey("Killer_Message"))
				logPart("WARNING", "\t\tMessage 'Killer_Message' option is no longer available for " + conditionName + ", but you can duplicate your"
						+ " condition and use 'require self' to send a message to killer");

			if (conditions.containsKey("Range"))
				logPart("WARNING", "\t\tMessage 'Range' option is no longer available for " + conditionName + " in " + conditionName);

			lines.add("message: ");

			for (final String line : conditions.getStringList("Message"))
				lines.add("- " + (line.isEmpty() ? "none" : line.replace("\n", "\n  ")));
		}
	}
}
