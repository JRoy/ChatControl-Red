package org.mineacademy.chatcontrol.settings;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventPriority;
import org.mineacademy.chatcontrol.model.Database;
import org.mineacademy.chatcontrol.model.PlayerGroup;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.WarningPoints.WarnTrigger;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictList;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.collection.StrictSet;
import org.mineacademy.fo.model.IsInList;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Whiteblacklist;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.settings.SimpleSettings;
import org.mineacademy.fo.settings.YamlComments;

/**
 * The main settings.yml configuration class.
 */
@SuppressWarnings("unused")
public final class Settings extends SimpleSettings {

	@Override
	protected int getConfigVersion() {
		return 30;
	}

	/**
	 * Attempt to save comments and symlink settings.yml
	 *
	 * @return
	 */
	@Override
	protected boolean saveComments() {
		return true;
	}

	/**
	 * What sections can the user modify? Such as channels,
	 * so that those are not updated by comments
	 *
	 * @return the ignored sections
	 */
	@Override
	protected List<String> getUncommentedSections() {
		return Arrays.asList(
				"Channels.List",
				"Groups",
				"Warning_Points.Reset_Task.Remove",
				"Warning_Points.Sets",
				"Integration.Discord.Connected_Channels",
				"Messages.Prefix",
				"Messages.Discord");
	}

	/**
	 * Channels settings
	 */
	public static class Channels {

		public static Boolean ENABLED;
		public static StrictList<String> COMMAND_ALIASES;
		public static PlayerGroup<Integer> MAX_READ_CHANNELS;
		public static Boolean JOIN_READ_OLD;
		public static Boolean IGNORE_AUTOJOIN_IF_LEFT;
		public static Boolean PREVENT_VANISH_CHAT;
		public static String FORMAT_CONSOLE;
		public static String FORMAT_DISCORD;
		public static StrictList<String> IGNORE_WORLDS;

		private static void init() {
			pathPrefix("Channels");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			MAX_READ_CHANNELS = new PlayerGroup<>(PlayerGroup.Type.MAX_READ_CHANNELS, getInteger("Max_Read_Channels"));
			JOIN_READ_OLD = getBoolean("Join_Read_Old");
			IGNORE_AUTOJOIN_IF_LEFT = getBoolean("Ignore_Autojoin_If_Left");
			PREVENT_VANISH_CHAT = getBoolean("Prevent_Vanish_Chat");
			FORMAT_CONSOLE = getString("Format_Console");
			FORMAT_DISCORD = getString("Format_Discord");
			IGNORE_WORLDS = new StrictList<>(getStringList("Ignore_Worlds"));
		}
	}

	/**
	 * Our core anti spam checker
	 */
	public static class AntiSpam {

		public static class Chat {

			public static PlayerGroup<SimpleTime> DELAY;
			public static PlayerGroup<Double> SIMILARITY;
			public static Integer SIMILARITY_PAST_MESSAGES;
			public static SimpleTime SIMILARITY_TIME;
			public static Whiteblacklist WHITELIST_DELAY;
			public static Whiteblacklist WHITELIST_SIMILARITY;

			public static SimpleTime LIMIT_PERIOD;
			public static Integer LIMIT_MAX;

			private static void init() {
				pathPrefix("Anti_Spam.Chat");

				DELAY = new PlayerGroup<>(PlayerGroup.Type.MESSAGE_DELAY, getTime("Delay"));
				SIMILARITY = new PlayerGroup<>(PlayerGroup.Type.MESSAGE_SIMILARITY, getSimilarity("Similarity"));
				SIMILARITY_PAST_MESSAGES = getInteger("Similarity_Past_Messages");
				SIMILARITY_TIME = getTime("Similarity_Time");
				WHITELIST_DELAY = new Whiteblacklist(getStringList("Whitelist_Delay"));
				WHITELIST_SIMILARITY = new Whiteblacklist(getStringList("Whitelist_Similarity"));
				LIMIT_PERIOD = getTime("Limit.Period");
				LIMIT_MAX = getInteger("Limit.Max_Messages");

				Valid.checkBoolean(SIMILARITY_PAST_MESSAGES > 0, "To disable Anti_Spam.Chat.Similarity_Past_Messages, simply set Anti_Spam.Chat.Similarity to 0%% instead!");
			}
		}

		public static class Commands {

			public static PlayerGroup<SimpleTime> DELAY;
			public static PlayerGroup<Double> SIMILARITY;
			public static Integer SIMILARITY_PAST_COMMANDS;
			public static SimpleTime SIMILARITY_TIME;
			public static Whiteblacklist WHITELIST_DELAY;
			public static Whiteblacklist WHITELIST_SIMILARITY;

			public static SimpleTime LIMIT_PERIOD;
			public static Integer LIMIT_MAX;

			private static void init() {
				pathPrefix("Anti_Spam.Commands");

				DELAY = new PlayerGroup<>(PlayerGroup.Type.COMMAND_DELAY, getTime("Delay"));
				SIMILARITY = new PlayerGroup<>(PlayerGroup.Type.COMMAND_SIMILARITY, getSimilarity("Similarity"));
				SIMILARITY_PAST_COMMANDS = getInteger("Similarity_Past_Commands");
				SIMILARITY_TIME = getTime("Similarity_Time");
				WHITELIST_DELAY = new Whiteblacklist(getStringList("Whitelist_Delay"));
				WHITELIST_SIMILARITY = new Whiteblacklist(getStringList("Whitelist_Similarity"));
				LIMIT_PERIOD = getTime("Limit.Period");
				LIMIT_MAX = getInteger("Limit.Max_Commands");

				Valid.checkBoolean(SIMILARITY_PAST_COMMANDS > 0, "To disable Anti_Spam.Commands.Similarity_Past_Messages, simply set Anti_Spam.Commands.Similarity to 0%% instead!");
			}
		}

		private static double getSimilarity(String path) {
			final String raw = getObject(path).toString();
			Valid.checkBoolean(raw.endsWith("%"), "Your Similarity key in " + getPathPrefix() + "." + path + " must end with %! Got: " + raw);

			final String rawNumber = raw.substring(0, raw.length() - 1);
			Valid.checkInteger(rawNumber, "Your Similarity key in " + getPathPrefix() + "." + path + " must be a whole number! Got: " + raw);

			return Integer.parseInt(rawNumber) / 100D;
		}
	}

	/**
	 * Anti caps check
	 */
	public static class AntiCaps {

		public static Boolean ENABLED;
		public static Whiteblacklist ENABLED_IN_COMMANDS;
		public static Integer MIN_MESSAGE_LENGTH;
		public static Integer MIN_CAPS_PERCENTAGE;
		public static Integer MIN_CAPS_IN_A_ROW;
		public static Whiteblacklist WHITELIST;

		private static void init() {
			pathPrefix("Anti_Caps");

			ENABLED = getBoolean("Enabled");
			ENABLED_IN_COMMANDS = new Whiteblacklist(getStringList("Enabled_In_Commands"));
			MIN_MESSAGE_LENGTH = getInteger("Min_Message_Length");
			MIN_CAPS_PERCENTAGE = getInteger("Min_Caps_Percentage");
			MIN_CAPS_IN_A_ROW = getInteger("Min_Caps_In_A_Row");
			WHITELIST = new Whiteblacklist(getStringList("Whitelist"));
		}
	}

	/**
	 * Anti-bot settings
	 */
	public static class AntiBot {

		public static Boolean BLOCK_CHAT_UNTIL_MOVED;
		public static Whiteblacklist BLOCK_CMDS_UNTIL_MOVED;
		public static Boolean BLOCK_SAME_TEXT_SIGNS;
		public static Whiteblacklist DISALLOWED_USERNAMES;

		public static PlayerGroup<SimpleTime> COOLDOWN_REJOIN;
		public static SimpleTime COOLDOWN_CHAT_AFTER_JOIN;
		public static SimpleTime COOLDOWN_COMMAND_AFTER_JOIN;

		private static void init() {
			pathPrefix("Anti_Bot");

			BLOCK_CHAT_UNTIL_MOVED = getBoolean("Block_Chat_Until_Moved");
			BLOCK_CMDS_UNTIL_MOVED = new Whiteblacklist(getStringList("Block_Commands_Until_Moved"));
			BLOCK_SAME_TEXT_SIGNS = getBoolean("Block_Same_Text_Signs");
			DISALLOWED_USERNAMES = new Whiteblacklist(getStringList("Disallowed_Usernames"));

			pathPrefix("Anti_Bot.Cooldown");

			COOLDOWN_REJOIN = new PlayerGroup<>(PlayerGroup.Type.REJOIN_COOLDOWN, getTime("Rejoin"));
			COOLDOWN_CHAT_AFTER_JOIN = getTime("Chat_After_Login");
			COOLDOWN_COMMAND_AFTER_JOIN = getTime("Command_After_Login");
		}
	}

	/**
	 * Settings for Englush spellug and gramer to avoid mistakes and typpos
	 */
	public static class TabComplete {

		public static Boolean ENABLED;
		public static Integer PREVENT_IF_BELOW_LENGTH;
		public static Whiteblacklist WHITELIST;

		private static void init() {
			pathPrefix("Tab_Complete");

			ENABLED = getBoolean("Enabled");
			PREVENT_IF_BELOW_LENGTH = getInteger("Prevent_If_Below_Length");
			WHITELIST = new Whiteblacklist(getStringList("Whitelist"));
		}
	}

	/**
	 * Settings for Englush spellug and gramer to avoid mistakes and typpos
	 */
	public static class Grammar {

		public static Integer INSERT_DOT_MSG_LENGTH;
		public static Integer CAPITALIZE_MSG_LENGTH;

		private static void init() {
			pathPrefix("Grammar");

			INSERT_DOT_MSG_LENGTH = getInteger("Insert_Dot_Message_Length");
			CAPITALIZE_MSG_LENGTH = getInteger("Capitalize_Message_Length");
		}
	}

	/**
	 * Settings for the rules system
	 */
	public static class Rules {

		public static Set<Rule.Type> APPLY_ON;
		public static Boolean VERBOSE;
		public static Boolean STRIP_COLORS = false;
		public static Boolean STRIP_ACCENTS = false;
		public static Boolean CASE_INSENSITIVE = true;

		private static void init() {
			pathPrefix("Rules");

			APPLY_ON = getSet("Apply_On", Rule.Type.class);
			Valid.checkBoolean(!APPLY_ON.contains(Rule.Type.GLOBAL), "To enable global rules, remove @import global from files in the rules/ folder.");

			VERBOSE = getBoolean("Verbose");
			STRIP_COLORS = getBoolean("Strip_Colors");
			STRIP_ACCENTS = getBoolean("Strip_Accents");
			CASE_INSENSITIVE = getBoolean("Case_Insensitive");
			REGEX_TIMEOUT = getInteger("Regex_Timeout_Ms");
		}
	}

	/**
	 * Settings for private messages
	 */
	public static class PrivateMessages {

		public static Boolean ENABLED;
		public static Boolean TOASTS;
		public static SimpleSound SOUND;
		public static StrictList<String> TELL_ALIASES;
		public static StrictList<String> REPLY_ALIASES;
		public static String FORMAT_SENDER;
		public static String FORMAT_RECEIVER;
		public static String FORMAT_TOAST;
		public static String FORMAT_CONSOLE;

		private static void init() {
			pathPrefix("Private_Messages");

			ENABLED = getBoolean("Enabled");
			TOASTS = getBoolean("Toasts");
			SOUND = getSound("Sound");
			TELL_ALIASES = getCommandList("Tell_Aliases");
			REPLY_ALIASES = getCommandList("Reply_Aliases");
			FORMAT_SENDER = getString("Format_Sender");
			FORMAT_RECEIVER = getString("Format_Receiver");
			FORMAT_TOAST = getString("Format_Toast");
			FORMAT_CONSOLE = getString("Format_Console");

			if (TOASTS && MinecraftVersion.olderThan(V.v1_12)) {
				Common.log("&cWarning: Toast notifications require at least Minecraft 1.12 and won't show. Disabling...");

				TOASTS = false;
			}
		}
	}

	/**
	 * Settings for timed message broadcaster
	 */
	public static class Messages {

		public static StrictSet<PlayerMessage.Type> APPLY_ON;
		public static Boolean STOP_ON_FIRST_MATCH;
		public static Map<PlayerMessage.Type, String> DISCORD;
		public static Map<PlayerMessage.Type, String> PREFIX;
		public static SimpleTime DEFER_JOIN_MESSAGE_BY;
		public static SimpleTime TIMED_DELAY;

		private static void init() {
			pathPrefix("Messages");

			APPLY_ON = new StrictSet<>(getSet("Apply_On", PlayerMessage.Type.class));
			STOP_ON_FIRST_MATCH = getBoolean("Stop_On_First_Match");
			DISCORD = getMap("Discord", PlayerMessage.Type.class, String.class);
			PREFIX = getMap("Prefix", PlayerMessage.Type.class, String.class);
			DEFER_JOIN_MESSAGE_BY = getTime("Defer_Join_Message_By");
			TIMED_DELAY = getTime("Timed_Delay");

			if (TIMED_DELAY.getTimeSeconds() > 3)
				Valid.checkBoolean(TIMED_DELAY.getTimeSeconds() >= 3, "Timed_Messages.Delay must be equal or greater than 3 seconds!"
						+ " (If you want to disable timed messages, remove them from Apply_On)");
		}
	}

	/**
	 * Settings for colors
	 */
	public static class Colors {

		public static StrictSet<org.mineacademy.chatcontrol.model.Colors.Type> APPLY_ON;

		private static void init() {
			pathPrefix("Colors");

			APPLY_ON = new StrictSet<>(getSet("Apply_On", org.mineacademy.chatcontrol.model.Colors.Type.class));
		}
	}

	/**
	 * Settings for the sound notify feature where we tag players in the chat and they receive a "pop"
	 */
	public static class SoundNotify {

		public static Boolean ENABLED;
		public static SimpleTime COOLDOWN;
		public static Boolean REQUIRE_AFK;
		public static String REQUIRE_PREFIX;
		public static SimpleSound SOUND;
		public static PlayerGroup<String> COLOR;

		private static void init() {
			pathPrefix("Sound_Notify");

			ENABLED = getBoolean("Enabled");
			COOLDOWN = getTime("Cooldown");
			REQUIRE_AFK = getBoolean("Require_Afk");
			REQUIRE_PREFIX = getString("Require_Prefix");
			SOUND = getSound("Sound");
			COLOR = new PlayerGroup<>(PlayerGroup.Type.SOUND_NOTIFY_COLOR, getString("Color"));

			Common.runLater(() -> {
				if (ENABLED && !Channels.ENABLED) {
					Common.log("&CWarning: Sound Notify requires Channels, which are disabled. This feature will not function.");

					ENABLED = false;
				}
			});
		}
	}

	/**
	 * Settings for the me command
	 */
	public static class Me {

		public static Boolean ENABLED;
		public static StrictList<String> COMMAND_ALIASES;
		public static String FORMAT;

		private static void init() {
			pathPrefix("Me");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			FORMAT = getString("Format");
		}
	}

	/**
	 * Settings for the list command
	 */
	public static class ListPlayers {

		public static Boolean ENABLED;
		public static StrictList<String> COMMAND_ALIASES;
		public static String FORMAT_LINE;
		public static String SORT_PREFIX;

		private static void init() {
			pathPrefix("List");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			FORMAT_LINE = getString("Format_Line");
			SORT_PREFIX = getString("Sort_Prefix");
		}
	}

	/**
	 * Settings for the ignore command
	 */
	public static class Ignore {

		public static Boolean ENABLED;
		public static Boolean BIDIRECTIONAL;
		public static StrictList<String> COMMAND_ALIASES;
		public static Boolean HIDE_CHAT;
		public static Boolean STOP_PRIVATE_MESSAGES;

		private static void init() {
			pathPrefix("Ignore");

			ENABLED = getBoolean("Enabled");
			BIDIRECTIONAL = getBoolean("Bidirectional");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			HIDE_CHAT = getBoolean("Hide_Chat");
			STOP_PRIVATE_MESSAGES = getBoolean("Stop_Private_Messages");
		}
	}

	/**
	 * Settings for the mute command
	 */
	public static class Mute {

		public static Boolean ENABLED;
		public static StrictList<String> COMMAND_ALIASES;
		public static Whiteblacklist PREVENT_COMMANDS;
		public static Boolean PREVENT_BOOKS;
		public static Boolean PREVENT_SIGNS;
		public static Boolean HIDE_JOINS;
		public static Boolean HIDE_QUITS;
		public static Boolean HIDE_DEATHS;

		private static void init() {
			pathPrefix("Mute");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			PREVENT_COMMANDS = new Whiteblacklist(getStringList("Prevent_Commands"));
			PREVENT_BOOKS = getBoolean("Prevent_Writing_Books");
			PREVENT_SIGNS = getBoolean("Prevent_Placing_Signs");
			HIDE_JOINS = getBoolean("Hide_Join_Messages");
			HIDE_QUITS = getBoolean("Hide_Quit_Messages");
			HIDE_DEATHS = getBoolean("Hide_Death_Messages");
		}
	}

	/**
	 * Settings for the /motd command
	 */
	public static class Motd {

		public static Boolean ENABLED;
		public static SimpleTime DELAY;
		public static StrictList<String> COMMAND_ALIASES;
		public static PlayerGroup<String> FORMAT_MOTD;
		public static String FORMAT_MOTD_FIRST_TIME;
		public static String FORMAT_MOTD_NEWCOMER;
		public static SimpleSound SOUND;

		private static void init() {
			pathPrefix("Motd");

			ENABLED = getBoolean("Enabled");
			DELAY = getTime("Delay");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			FORMAT_MOTD = new PlayerGroup<>(PlayerGroup.Type.MOTD, getString("Format_Motd"));
			FORMAT_MOTD_FIRST_TIME = getString("Format_Motd_First_Time");
			FORMAT_MOTD_NEWCOMER = getString("Format_Motd_Newcomer");
			SOUND = getSound("Sound");
		}
	}

	/**
	 * Settings for the /chc announce command
	 */
	public static class Announcer {

		public static SimpleSound CHAT_SOUND;

		private static void init() {
			pathPrefix("Announcer");

			CHAT_SOUND = getSound("Chat_Sound");
		}
	}

	/**
	 * Settings for the /mail command
	 */
	public static class Mail {

		public static Boolean ENABLED;
		public static StrictList<String> COMMAND_ALIASES;

		private static void init() {
			pathPrefix("Mail");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
		}
	}

	/**
	 * Settings for the /tag command
	 */
	public static class Tag {

		public static StrictSet<org.mineacademy.chatcontrol.operator.Tag.Type> APPLY_ON;
		public static StrictList<String> COMMAND_ALIASES;
		public static Integer MAX_NICK_LENGTH;
		public static String NICK_PREFIX;
		public static Boolean NICK_DISABLE_IMPERSONATION;
		public static Boolean CHANGE_DISPLAYNAME;
		public static Boolean CHANGE_CUSTOMNAME;

		private static void init() {
			pathPrefix("Tag");

			APPLY_ON = new StrictSet<>(getSet("Apply_On", org.mineacademy.chatcontrol.operator.Tag.Type.class));
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			MAX_NICK_LENGTH = getInteger("Max_Nick_Length");
			NICK_PREFIX = getString("Nick_Prefix");
			NICK_DISABLE_IMPERSONATION = getBoolean("Nick_Disable_Impersonation");
			CHANGE_DISPLAYNAME = getBoolean("Change_Displayname");
			CHANGE_CUSTOMNAME = getBoolean("Change_Customname");

			Players.setNicksEnabled(APPLY_ON.contains(org.mineacademy.chatcontrol.operator.Tag.Type.NICK));
		}
	}

	/**
	 * Settings for the realname command
	 */
	public static class RealName {

		public static StrictList<String> COMMAND_ALIASES;

		private static void init() {
			pathPrefix("Real_Name");

			COMMAND_ALIASES = getCommandList("Command_Aliases");
		}
	}

	/**
	 * Settings for the tab list feature
	 */
	public static class TabList {

		public static Boolean ENABLED;
		public static String FORMAT;

		private static void init() {
			pathPrefix("Tab_List");

			ENABLED = getBoolean("Enabled");
			FORMAT = getString("Format");
		}
	}

	/**
	 * Settings for the /toggle command
	 */
	public static class Toggle {

		public static StrictSet<org.mineacademy.chatcontrol.model.Toggle> APPLY_ON;
		public static StrictList<String> COMMAND_ALIASES;

		private static void init() {
			pathPrefix("Toggle");

			APPLY_ON = new StrictSet<>(getSet("Apply_On", org.mineacademy.chatcontrol.model.Toggle.class));
			COMMAND_ALIASES = getCommandList("Command_Aliases");
		}
	}

	/**
	 * Settings for the spy command
	 */
	public static class Spy {

		public static String PREFIX;
		public static StrictList<String> COMMAND_ALIASES;
		public static Set<org.mineacademy.chatcontrol.model.Spy.Type> APPLY_ON;
		public static String FORMAT_CHAT;
		public static String FORMAT_PARTY_CHAT;
		public static String FORMAT_COMMAND;
		public static String FORMAT_PRIVATE_MESSAGE;
		public static String FORMAT_MAIL;
		public static String FORMAT_SIGN;
		public static String FORMAT_BOOK;
		public static String FORMAT_ANVIL;
		public static Whiteblacklist COMMANDS;

		private static void init() {
			pathPrefix("Spy");

			PREFIX = getString("Prefix");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			APPLY_ON = new HashSet<>(getList("Apply_On", org.mineacademy.chatcontrol.model.Spy.Type.class));
			FORMAT_CHAT = getString("Format_Chat");
			FORMAT_PARTY_CHAT = getString("Format_Party_Chat");
			FORMAT_COMMAND = getString("Format_Command");
			FORMAT_PRIVATE_MESSAGE = getString("Format_Private_Message");
			FORMAT_MAIL = getString("Format_Mail");
			FORMAT_SIGN = getString("Format_Sign");
			FORMAT_BOOK = getString("Format_Book");
			FORMAT_ANVIL = getString("Format_Anvil");
			COMMANDS = new Whiteblacklist(getStringList("Commands"));

			if (MinecraftVersion.olderThan(V.v1_8) && APPLY_ON.contains(org.mineacademy.chatcontrol.model.Spy.Type.MAIL)) {
				APPLY_ON.remove(org.mineacademy.chatcontrol.model.Spy.Type.MAIL);

				Common.log("Spying mail requires Minecraft 1.8.8 or newer.");
			}
		}
	}

	/**
	 * Settings for settings groups
	 */
	public static class Groups {

		public static StrictMap<String, StrictMap<PlayerGroup.Type, Object>> LIST;

		private static void init() {
			pathPrefix("Groups");

			LIST = new StrictMap<>();

			for (final Entry<String, Object> entry : getMap("").entrySet()) {
				final String groupName = entry.getKey();
				final StrictMap<PlayerGroup.Type, Object> settings = new StrictMap<>();

				for (final Map.Entry<String, Object> groupSetting : SerializedMap.of(entry.getValue()).entrySet()) {
					final PlayerGroup.Type settingKey = PlayerGroup.Type.fromKey(groupSetting.getKey());
					final Object settingValue = SerializeUtil.deserialize(settingKey.getValidClass(), groupSetting.getValue());

					settings.put(settingKey, settingValue);
				}

				LIST.put(groupName, settings);
			}
		}
	}

	/**
	 * Settings for the warning points
	 */
	public static class WarningPoints {

		public static Boolean ENABLED;

		public static SimpleTime RESET_TASK_PERIOD;
		public static LinkedHashMap<String, Integer> RESET_MAP;

		public static WarnTrigger TRIGGER_CHAT_DELAY;
		public static WarnTrigger TRIGGER_CHAT_SIMILARITY;
		public static WarnTrigger TRIGGER_CHAT_LIMIT;
		public static WarnTrigger TRIGGER_COMMAND_DELAY;
		public static WarnTrigger TRIGGER_COMMAND_SIMILARITY;
		public static WarnTrigger TRIGGER_COMMAND_LIMIT;
		public static WarnTrigger TRIGGER_CAPS;

		private static void init() {
			pathPrefix("Warning_Points");

			ENABLED = getBoolean("Enabled");

			// Load warning points
			org.mineacademy.chatcontrol.model.WarningPoints.getInstance().clearSets();

			for (final Map.Entry<String, Object> entry : getMap("Sets").entrySet()) {
				final String setName = entry.getKey();
				final SerializedMap triggers = SerializedMap.of(entry.getValue());

				org.mineacademy.chatcontrol.model.WarningPoints.getInstance().addSet(setName, triggers);
			}

			pathPrefix("Warning_Points.Reset_Task");

			RESET_TASK_PERIOD = getTime("Period");
			RESET_MAP = getMap("Remove", String.class, Integer.class);

			pathPrefix("Warning_Points.Triggers");

			TRIGGER_CHAT_DELAY = getTrigger("Chat_Delay");
			TRIGGER_CHAT_SIMILARITY = getTrigger("Chat_Similarity");
			TRIGGER_CHAT_LIMIT = getTrigger("Chat_Limit");
			TRIGGER_COMMAND_DELAY = getTrigger("Command_Delay");
			TRIGGER_COMMAND_SIMILARITY = getTrigger("Command_Similarity");
			TRIGGER_COMMAND_LIMIT = getTrigger("Command_Limit");
			TRIGGER_CAPS = getTrigger("Caps");
		}

		/*
		 * Load a warning trigger from config path, that is a math formula
		 */
		private static WarnTrigger getTrigger(String path) {
			final String[] split = getString(path).split(" ");
			Valid.checkBoolean(split.length > 1, "Invalid warning point trigger syntax, use <warn set> <formula> in " + getPathPrefix() + "." + path);

			final String set = split[0];
			Valid.checkBoolean(org.mineacademy.chatcontrol.model.WarningPoints.getInstance().isSetLoaded(set), "Warn set '" + set + "' specified in " + getPathPrefix() + "." + path + " does not exist!");

			final String formula = Common.joinRange(1, split);

			return new WarnTrigger(set, formula);
		}
	}

	/**
	 * Settings for new players
	 */
	public static class Newcomer {

		public static SimpleTime THRESHOLD;
		public static IsInList<String> WORLDS;
		public static StrictSet<Tuple<String, Boolean>> PERMISSIONS;
		public static Double WARNING_POINTS_MULTIPLIER;

		public static Boolean RESTRICT_SEEING_CHAT;
		public static Boolean RESTRICT_CHAT;
		public static Whiteblacklist RESTRICT_CHAT_WHITELIST;

		public static Boolean RESTRICT_COMMANDS;
		public static Whiteblacklist RESTRICT_COMMANDS_WHITELIST;

		private static void init() {
			pathPrefix("Newcomer");

			THRESHOLD = getTime("Threshold");
			WORLDS = new IsInList<>(getStringList("Worlds"));
			PERMISSIONS = loadPermissions();
			WARNING_POINTS_MULTIPLIER = getDouble("Warning_Points_Multiplier");
			RESTRICT_SEEING_CHAT = getBoolean("Restrict_Seeing_Chat");

			pathPrefix("Newcomer.Restrict_Chat");

			RESTRICT_CHAT = getBoolean("Enabled");
			RESTRICT_CHAT_WHITELIST = new Whiteblacklist(getStringList("Whitelist"));

			pathPrefix("Newcomer.Restrict_Commands");

			RESTRICT_COMMANDS = getBoolean("Enabled");
			RESTRICT_COMMANDS_WHITELIST = new Whiteblacklist(getStringList("Whitelist"));
		}

		private static StrictSet<Tuple<String, Boolean>> loadPermissions() {
			final StrictSet<Tuple<String, Boolean>> loaded = new StrictSet<>();

			for (final String raw : getSet("Permissions", String.class)) {
				final String split[] = raw.split(" \\- ");
				final String permission = split[0];
				final boolean value = split.length > 1 ? Boolean.parseBoolean(split[1]) : true;

				loaded.add(new Tuple<>(permission, value));
			}

			return loaded;
		}
	}

	/**
	 * Settings for the chat saving and logging feature
	 */
	public static class Log {

		public static Set<org.mineacademy.chatcontrol.model.Log.Type> APPLY_ON;
		public static SimpleTime CLEAN_AFTER;
		public static Whiteblacklist COMMAND_LIST;

		private static void init() {
			pathPrefix("Log");

			APPLY_ON = getSet("Apply_On", org.mineacademy.chatcontrol.model.Log.Type.class);
			CLEAN_AFTER = getTime("Clean_After");
			COMMAND_LIST = new Whiteblacklist(getStringList("Command_List"));
		}
	}

	/**
	 * Settings for the console filter
	 */
	public static class ConsoleFilter {

		public static Boolean ENABLED;
		public static Set<String> MESSAGES;

		private static void init() {
			pathPrefix("Console_Filter");

			ENABLED = getBoolean("Enabled");
			MESSAGES = getSet("Messages", String.class);
		}
	}

	/**
	 * Integration with third party plugins
	 */
	public static class Integration {

		public static class AuthMe {

			public static Boolean DELAY_JOIN_MESSAGE_UNTIL_LOGGED;
			public static Boolean HIDE_QUIT_MSG_IF_NOT_LOGGED;

			private static void init() {
				pathPrefix("Integration.AuthMe");

				DELAY_JOIN_MESSAGE_UNTIL_LOGGED = getBoolean("Delay_Join_Message_Until_Logged");
				HIDE_QUIT_MSG_IF_NOT_LOGGED = getBoolean("Hide_Quit_Message_If_Not_Logged");
			}
		}

		public static class BungeeCord {

			public static Boolean ENABLED;
			public static String PREFIX;

			private static void init() {
				pathPrefix("Integration.BungeeCord");

				ENABLED = getBoolean("Enabled");
				PREFIX = getString("Prefix");
			}
		}

		public static class Discord {

			public static Boolean ENABLED;
			public static Boolean WEBHOOK;
			public static Boolean SEND_MESSAGES_AS_BOT;
			public static Map<String, String> CONNECTED_CHANNELS;

			private static void init() {
				pathPrefix("Integration.Discord");

				ENABLED = getBoolean("Enabled");
				WEBHOOK = getBoolean("Webhook");
				SEND_MESSAGES_AS_BOT = getBoolean("Send_Messages_As_Bot");
				CONNECTED_CHANNELS = getMap("Connected_Channels", String.class, String.class);
			}
		}

		public static class ProtocolLib {

			public static Boolean BOOK_ANTI_CRASH;
			public static Boolean LISTEN_FOR_PACKETS;

			private static void init() {
				pathPrefix("Integration.ProtocolLib");

				LISTEN_FOR_PACKETS = getBoolean("Listen_For_Packets");
				BOOK_ANTI_CRASH = getBoolean("Book_Anti_Crash");
			}
		}
	}

	/**
	 * Settings for MySQL
	 *
	 * For security reasons, no sensitive information is stored here.
	 */
	public static class MySQL {

		public static Boolean ENABLED = false;

		private static void init() {
			pathPrefix("MySQL");

			if (!SimplePlugin.getInstance().isEnabled())
				return;

			// Load the MySQL database using credentials from a separate file
			// This enables people to share their settings.yml without leaking credentials
			final File mysqlFile = FileUtil.extract("mysql.yml");

			// Write comments first so that it updates the file
			YamlComments.writeComments("mysql.yml", mysqlFile);

			// Now load the config
			final FileConfiguration mysqlConfig = FileUtil.loadConfigurationStrict(mysqlFile);

			ENABLED = mysqlConfig.getBoolean("Enabled");

			final String host = mysqlConfig.getString("Host");
			final String database = mysqlConfig.getString("Database");
			final String user = mysqlConfig.getString("User");
			final String password = mysqlConfig.getString("Password");
			final String line = mysqlConfig.getString("Line");

			if (ENABLED) {
				Common.log("&cConnecting to MySQL database...");

				Database.getInstance().connect(line.replace("{host}", host).replace("{database}", database), user, password, "ChatControl");
			}
		}
	}

	public static Boolean SHOW_TIPS;
	public static EventPriority CHAT_LISTENER_PRIORITY;
	public static SimpleTime CLEAR_DATA_IF_INACTIVE;

	private static void init() {
		pathPrefix(null);

		SHOW_TIPS = getBoolean("Show_Tips");
		CHAT_LISTENER_PRIORITY = get("Chat_Listener_Priority", EventPriority.class);
		CLEAR_DATA_IF_INACTIVE = getTime("Clear_Data_If_Inactive");
	}
}
